package com.sematext.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.sematext.android.logsene.ApiResponse;
import com.sematext.android.logsene.Bulk;
import com.sematext.android.logsene.LogseneClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class LogWorker {
  private final static String LOG_TAG = "Logsene";

  /**
   * Queue for communicating with the appender thread.
   */
  private final BlockingQueue<JSONObject> queue = new LinkedBlockingQueue<>();
  private final LogseneClient client;
  private final String appToken;
  private final String type;
  private final int preflightSize;
  private final Appender appender;
  private final Context context;

  /**
   * Constructor.
   *
   * @param context application context
   * @param client logsene client
   * @param appToken the logsene app token
   * @param type type to use when indexing messages
   * @param preflightSize the max size of the preflight queue
   * @throws IOException if unable to initialize preflight queue
   */
  public LogWorker(Context context, LogseneClient client, String appToken, String type, int preflightSize)
          throws IOException {
    this.client = client;
    this.appToken = appToken;
    this.type = type;
    this.context = context;
    this.preflightSize = preflightSize;

    this.appender = new Appender();
    Thread appenderThread = new Thread(appender);
    appenderThread.start();
  }

  public void addToQueue(JSONObject obj) {
    this.queue.add(obj);
  }

  private class Appender implements Runnable {
    /**
     * Minimum number of messages before sending request.
     */
    private static final int MIN_BATCH_SIZE = 10;

    /**
     * Maximum number of messages to send in one bulk request.
     */
    private static final int MAX_BULK_SIZE = 50;

    /**
     * Max time between sending requests.
     */
    private static final int MAX_TIME_TRIGGER = 60 * 1000;
    // discuss: what happens if user opens the app -> exception is triggered & logged -> app is closed
    // in this case we would never log the exception because batch size or max_time_trigger are not triggered.
    // even if built as a long-running service that continues running after user closes the app, the user might
    // delete the app out of frustration (that would stop the service)

    /**
     * Number of times to attempt to send batch request.
     */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Persistent queue for storing messages to be sent to Logsene.
     *
     * Messages are persisted as they are acknowledged, and they stay in the queue until they are successfully sent.
     */
    private final SqliteObjectQueue preflightQueue;

    public Appender() throws IOException {
      this.preflightQueue = new SqliteObjectQueue(context, "logs", preflightSize);
    }

    public void run() {
      long lastSend = System.currentTimeMillis();

      while(true) {
        JSONObject newMessage;
        try {
          newMessage = queue.poll(1, TimeUnit.SECONDS);
          if (newMessage != null) {
            preflightQueue.add(newMessage);
          }
        } catch (InterruptedException e) {
          return;
        }
        long sinceLastSend = System.currentTimeMillis() - lastSend;
        if (isNetworkAvailable() && preflightQueue.size() > 0
            && (preflightQueue.size() >= MIN_BATCH_SIZE || sinceLastSend > MAX_TIME_TRIGGER)) {
          sendInBatches();
          lastSend = System.currentTimeMillis();
        }
      }
    }

    private void sendInBatches() {
      List<JSONObject> batch = preflightQueue.peek(MAX_BULK_SIZE);
      do {
        if (sendBatch(batch)) {
          preflightQueue.remove(batch.size());
          batch = preflightQueue.peek(MAX_BULK_SIZE);
        } else {
          return;
        }
      } while (batch.size() > 0);
    }

    private boolean sendBatch(List<JSONObject> batch) {
      Bulk.Builder bulkBuilder = new Bulk.Builder();
      for (JSONObject obj : batch) {
        bulkBuilder.addSource(obj.toString(), type);
      }
      return attemptExecute(bulkBuilder.build(), MAX_ATTEMPTS);
    }

    private boolean attemptExecute(Bulk bulk, int leftAttempts) {
      if (leftAttempts == 0) {
        return false;
      }
      leftAttempts -= 1;
      try {
        Log.d(LOG_TAG, "Attempting to send bulk request");
        ApiResponse result = client.execute(bulk);
        if (!result.isSuccessful()) {
          Log.e(LOG_TAG, String.format("Bad status code (%d) returned from api. Response: %s",
                  result.getHttpResponse().code(), result.getBody()));
          return false;
        } else {
          // even though the status code is successful, some documents might have failed
          JSONObject json = result.getJson();
          if (json != null && Boolean.parseBoolean(json.optString("errors", "false"))) {
            // we just log the error, as we most likely cannot resolve the issue by retrying these documents
            Log.e(LOG_TAG, String.format("Unable to index all documents. Response: %s\nRequest: %s",
                    result.getBody(), bulk.toString(appToken)));
          }
        }
        return true;
      } catch (IOException e) {
        Log.e(LOG_TAG, "Error while sending logs", e);
        return attemptExecute(bulk, leftAttempts);
      }
    }

    private boolean isNetworkAvailable() {
      ConnectivityManager connectivityManager =
              (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
      return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
  }
}

