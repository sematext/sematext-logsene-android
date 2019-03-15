package com.sematext.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/**
 * Logs messages to Logsene from Android applications.
 */
public class Logsene {
  public static final String KEY_RECEIVERURL = "LOGSENE_RECEIVERURL";
  public static final String KEY_APPTOKEN = "LOGSENE_APPTOKEN";
  public static final String KEY_TYPE = "LOGSENE_TYPE";

  private final String TAG = getClass().getSimpleName();
  private final String UNCONSTRAINED_WORKER_TAG = "com.sematext.android.LogWorker.unconstrained";
  private final String CONSTRAINED_WORKER_TAG = "com.sematext.android.LogWorker.constrained";

  private final String RECEIVER_URL = "https://logsene-receiver.sematext.com";

  /**
   * Maximum number of messages to cache when offline.
   */
  private final int DEFAULT_MAX_OFFLINE_MESSAGES = 5000;

  /**
   * Minimum number of messages before sending request.
   */
  private final int DEFAULT_MIN_BATCH_SIZE = 10;

  /**
   * Max time between sending requests.
   */
  private static final int DEFAULT_MAX_TIME_TRIGGER = 60 * 1000;
  // discuss: what happens if user opens the app -> exception is triggered & logged -> app is closed
  // in this case we would never log the exception because batch size or max_time_trigger are not triggered.
  // even if built as a long-running service that continues running after user closes the app, the user might
  // delete the app out of frustration (that would stop the service)

  private static JSONObject defaultMeta;
  private final Context context;
  private String versionName;
  private Integer versionCode;
  private String uuid;

  /**
   * Persistent queue for storing messages to be sent to Logsene.
   *
   * Messages are persisted as they are acknowledged.
   * Messages are dequeued from LogWorker, and only removed when they are successfully sent.
   */
  private SqliteObjectQueue preflightQueue;
  private long lastScheduled;

  private String appToken;
  private String type;
  private String receiverUrl;
  private int maxOfflineMessages;
  private int minBatchSize;
  private long maxTimeTrigger;
  private boolean sendRequiresUnmeteredNetwork;
  private boolean sendRequiresDeviceIdle;
  private boolean sendRequiresBatteryNotLow;

  public Logsene(Context context) {
    Utils.requireNonNull(context);
    this.context = context;
    this.uuid = Installation.id(context);
    config();
    // Database initialization can potentially take a long time, and Logsene can be initialized
    // in UI thread, hence spawn a separate thread
    Thread initDbThread = new Thread("Initialize Logsene database") {
      public void run() {
        Logsene.this.preflightQueue = new SqliteObjectQueue(Logsene.this.context, "logs", maxOfflineMessages);
      }
    };
    initDbThread.start();
    this.lastScheduled = SystemClock.elapsedRealtime();
  }

  public long getQueueSize() {
    return preflightQueue.size();
  }

  private void config() {
    Bundle data = null;
    try {
      data = context.getPackageManager().getApplicationInfo(context.getPackageName(),
            PackageManager.GET_META_DATA).metaData;
    } catch (PackageManager.NameNotFoundException e) {
      throw new RuntimeException(e);
    }

    // required fields
    if (!data.containsKey("LogseneAppToken")) {
      throw new RuntimeException("Please provide <meta-data name=\"LogseneAppToken\" value=\"yourapptoken\">");
    } else if (!data.containsKey("LogseneType")) {
      throw new RuntimeException("Please provide <meta-data name=\"LogseneType\" value=\"example\">");
    }
    appToken = data.getString("LogseneAppToken");
    type = data.getString("LogseneType");

    // optional fields
    receiverUrl = data.getString("LogseneReceiverUrl", RECEIVER_URL);
    maxOfflineMessages = data.getInt("LogseneMaxOfflineMessages", DEFAULT_MAX_OFFLINE_MESSAGES);
    minBatchSize = data.getInt("LogseneMinBatchSize", DEFAULT_MIN_BATCH_SIZE);
    maxTimeTrigger = (long)(data.getInt("LogseneMaxTimeTrigger", DEFAULT_MAX_TIME_TRIGGER));
    sendRequiresUnmeteredNetwork = data.getBoolean("LogseneSendRequiresUnmeteredNetwork", false);
    sendRequiresDeviceIdle = data.getBoolean("LogseneSendRequiresDeviceIdle", false);
    sendRequiresBatteryNotLow = data.getBoolean("LogseneSendRequiresBatteryNotLow", false);

    Log.d(TAG, String.format("Logsene is configured:\n"
        + "  Type:                                   %s\n"
        + "  Receiver URL:                           %s\n"
        + "  Max Offline Messages:                   %d\n"
        + "  Min Batch Size:                         %d\n"
        + "  Max Time Trigger:                       %d\n"
        + "  Send logs only on unmetered network:    %s\n"
        + "  Send logs only when device is idle:     %s\n"
        + "  Send logs only when battery is not low: %s",
        type, receiverUrl, maxOfflineMessages, minBatchSize, maxTimeTrigger,
        sendRequiresUnmeteredNetwork, sendRequiresDeviceIdle, sendRequiresBatteryNotLow));
  }

  /**
   * Logs a simple message.
   *
   * @param level value of the log `level` field
   * @param message message text
     */
  public void log(String level, String message) {
    Utils.requireNonNull(message);
    JSONObject obj = new JSONObject();
    try {
      obj.put("level", level);
      obj.put("message", message);
      addToQueue(obj);
    } catch (JSONException e) {
      // thrown when key is null in put(), so should never happen
      Log.e(TAG, "Failed to construct json object", e);
    }
  }

  /**
   * Logs debug message.
   * @param message the message text
   */
  public void debug(String message) {
    log("debug", message);
  }

  /**
   * Logs debug message.
   * @param message the message text
   */
  public void info(String message) {
    log("info", message);
  }

  /**
   * Logs debug message.
   * @param message the message text
   */
  public void warn(String message) {
    log("warn", message);
  }

  /**
   * Logs exception with `warn` level.
   * @param error the exception
   */
  public void warn(Throwable error) {
    log("warn", error);
  }

  /**
   * Logs error message.
   * @param message the message text
   */
  public void error(String message) {
    log("error", message);
  }

  /**
   * Logs exception with `error` level.
   * @param error the exception
   */
  public void error(Throwable error) {
    log("error", error);
  }

  /**
   * Logs an exception.
   * @param level value of log `level` field
   * @param error any throwable
     */
  public void log(String level, Throwable error) {
    Utils.requireNonNull(error);
    JSONObject obj = new JSONObject();
    try {
      obj.put("level", level);
      obj.put("exception", error.getClass().toString());
      obj.put("message", error.getMessage());
      obj.put("stacktrace", Utils.getStackTrace(error));
      addToQueue(obj);
    } catch (JSONException e) {
      // thrown when key is null in put(), so should never happen
      Log.e(TAG, "Failed to construct json object", e);
    }
  }

  /**
   * Send a custom event.
   * @param object the event data.
   */
  public void event(JSONObject object) {
    Utils.requireNonNull(object);
    addToQueue(object);
  }

  /**
   * Sets the default meta properties.
   *
   * These will be included with every request.
   *
   * @param metadata the default meta properties, use null to disable.
   */
  public static void setDefaultMeta(JSONObject metadata) {
    defaultMeta = metadata;
  }

  /**
   * Flush the message queue.
   *
   * This call is optional, but it is recommended to be called when application is destroyed.
   */
  public void flushMessageQueue() {
    Log.d(TAG, "Flushing message queue, message queue size = " + preflightQueue.size());
    scheduleUnconstrainedWorker();
  }

  private void scheduleUnconstrainedWorker() {
    Data workerData = new Data.Builder()
        .putString(KEY_RECEIVERURL, receiverUrl)
        .putString(KEY_APPTOKEN, appToken)
        .putString(KEY_TYPE, type)
        .build();

    OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(LogWorker.class)
        .addTag(UNCONSTRAINED_WORKER_TAG)
        .setInputData(workerData)
        .build();

    WorkManager.getInstance().enqueueUniqueWork(UNCONSTRAINED_WORKER_TAG,
        ExistingWorkPolicy.KEEP, workRequest);
  }

  private void scheduleConstrainedWorker() {
    Data workerData = new Data.Builder()
        .putString(KEY_RECEIVERURL, receiverUrl)
        .putString(KEY_APPTOKEN, appToken)
        .putString(KEY_TYPE, type)
        .build();

    Constraints workerConstraints = new Constraints.Builder()
        .setRequiredNetworkType(
            sendRequiresUnmeteredNetwork ? NetworkType.UNMETERED : NetworkType.CONNECTED)
        .setRequiresDeviceIdle(sendRequiresDeviceIdle)
        .setRequiresBatteryNotLow(sendRequiresBatteryNotLow)
        .build();

    OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(LogWorker.class)
        .addTag(CONSTRAINED_WORKER_TAG)
        .setInputData(workerData)
        .setConstraints(workerConstraints)
        .build();

    WorkManager.getInstance().enqueueUniqueWork(CONSTRAINED_WORKER_TAG,
        ExistingWorkPolicy.KEEP, workRequest);
  }

  private void addToQueue(JSONObject obj) {
    if (preflightQueue == null) {
      Log.e(TAG, "Message queue has not been initialized, message dropped.");
      return;
    }

    assert obj != null;
    enrich(obj);

    preflightQueue.add(obj);
    if (preflightQueue.size() > maxOfflineMessages) {
      Log.e(TAG, "Message queue overflowed (" + preflightQueue.size() + " > "
          + maxOfflineMessages + "), some logs are lost.");
    }

    // Approaching message queue limit, schedule a one-time unconstrained worker
    if (preflightQueue.size() >= (maxOfflineMessages * 0.9)) {
      scheduleUnconstrainedWorker();

      lastScheduled = SystemClock.elapsedRealtime();
      //Log.d(TAG, "Unconstrained worker enqueued, message queue size = " + preflightQueue.size());

    // Schedule constrained worker if minimum batch size met, or the time from last scheduled
    // exceeds maxTimeTrigger.
    // The time threshold is used to account for the case where the message queue stays below
    // the minBatchSize for a long time.
    } else if (preflightQueue.size() >= minBatchSize
        || (SystemClock.elapsedRealtime() - lastScheduled) > maxTimeTrigger) {
      scheduleConstrainedWorker();

      lastScheduled = SystemClock.elapsedRealtime();
      //Log.d(TAG, "Constrained worker enqueued, message queue size = " + preflightQueue.size());
    }

    /*
    try {
        Log.d(TAG, "Unconstrained Worker is "
            + WorkManager.getInstance().getWorkInfosForUniqueWork(UNCONSTRAINED_WORKER_TAG).get().get(0).getState());
        Log.d(TAG, "Constrained Worker is "
            + WorkManager.getInstance().getWorkInfosForUniqueWork(CONSTRAINED_WORKER_TAG).get().get(0).getState());
    } catch (Exception e) {
    }
    */
  }

  private String getVersionName() {
    if (versionName == null) {
      PackageInfo pInfo = null;
      try {
        pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        versionName = pInfo.versionName;
      } catch (PackageManager.NameNotFoundException e) {
        Log.e(TAG, e.getMessage(), e);
        // set to n/a so we don't try again
        versionName = "n/a";
      }
    }

    return versionName;
  }

  private int getVersionCode() {
    if (versionCode == null) {
      PackageInfo pInfo = null;
      try {
        pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        versionCode = pInfo.versionCode;
      } catch (PackageManager.NameNotFoundException e) {
        Log.e(TAG, e.getMessage(), e);
        // set to n/a so we don't try again
        versionCode = -1;
      }
    }

    return versionCode;
  }

  private void enrich(JSONObject obj) {
    assert obj != null;
    try {
      if (!obj.has("@timestamp")) {
        obj.put("@timestamp", Utils.iso8601());
      }

      // if user has specified some value for this field, we don't touch it
      if (!obj.has("meta")) {
        JSONObject metadata = new JSONObject();
        metadata.put("versionName", getVersionName());
        metadata.put("versionCode", getVersionCode());
        metadata.put("osRelease", Build.VERSION.RELEASE);
        metadata.put("uuid", uuid);
        if (defaultMeta != null) {
          Iterator<String> keys = defaultMeta.keys();
          while (keys.hasNext()) {
            String key = keys.next();
            metadata.put(key, defaultMeta.get(key));
          }
        }
        obj.put("meta", metadata);
      }
    } catch (JSONException e) {
      // thrown when key is null in put(), so should never happen
      Log.e(TAG, "Failed to construct json object", e);
    }
  }
}
