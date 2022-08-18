package com.sematext.logseneandroid;

import android.content.Context;
import android.util.Log;
import com.sematext.logseneandroid.logsene.ApiResponse;
import com.sematext.logseneandroid.logsene.Bulk;
import com.sematext.logseneandroid.logsene.LogseneClient;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class LogWorker extends Worker {
  private final static String LOG_TAG = "Logsene";

  /**
   * Maximum number of messages to send in one bulk request.
   */
  private static final int MAX_BULK_SIZE = 50;

  /**
   * Number of times to attempt to send batch request.
   */
  private static final int MAX_ATTEMPTS = 3;

  private LogseneClient client;
  private String appToken;
  private String receiverUrl;
  private String type;
  private boolean checkUnreachableUrl;
  private int unreachableUrlTimeout;
  private final Context context;

  private SqliteObjectQueue preflightQueue;

  public LogWorker(@NonNull Context context, @NonNull WorkerParameters params) {
    super(context, params);
    this.context = context;
  }

  @Override
  public Result doWork() {
    receiverUrl = getInputData().getString(Logsene.KEY_RECEIVERURL);
    appToken = getInputData().getString(Logsene.KEY_APPTOKEN);
    type = getInputData().getString(Logsene.KEY_TYPE);
    checkUnreachableUrl = getInputData().getBoolean(Logsene.KEY_CHECK_UNREACHABLE_URL,false);
    unreachableUrlTimeout = getInputData().getInt(Logsene.KEY_UNREACHABLE_URL_TIMEOUT, Logsene.DEFAULT_UNREACHABLE_TIMEOUT);

    this.client = new LogseneClient(receiverUrl, appToken);
    this.preflightQueue = new SqliteObjectQueue(context);

    long size = preflightQueue.size();
    Log.d(LOG_TAG, "Worker started, message queue size = " + size);
    if (size <= 0) {
      return Result.success();
    }

    // sendInBatches() only returns false if nothing was sent
    boolean success = sendInBatches();

    if (!success) {
        Log.e(LOG_TAG, "Worker failed to send logs");
        return Result.failure();
    } else {
        Log.d(LOG_TAG, "Worker succeeded in sending logs, message queue size = " + preflightQueue.size());
        return Result.success();
    }
  }

  private boolean sendInBatches() {
    boolean success = false;
    List<JSONObject> batch = preflightQueue.peek(MAX_BULK_SIZE);
    do {
      if (sendBatch(batch)) {
        success = true;
        preflightQueue.remove(batch.size());
        batch = preflightQueue.peek(MAX_BULK_SIZE);
      } else {
        return success;
      }
    } while (batch.size() > 0);

    return success;
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
      if (checkUnreachableUrl && !Utils.isURLReachable(receiverUrl, unreachableUrlTimeout)){
        Log.e(LOG_TAG, "Unreachable URL");
        return attemptExecute(bulk, leftAttempts);
      }
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
}

