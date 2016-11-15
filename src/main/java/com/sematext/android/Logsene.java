package com.sematext.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Logs messages to Logsene from Android applications.
 */
public class Logsene {
  private final String TAG = getClass().getSimpleName();
  private static JSONObject defaultMeta;
  private final Context context;
  private String versionName;
  private Integer versionCode;
  private String uuid;

  public Logsene(Context context) {
    Utils.requireNonNull(context);
    this.context = context;
    this.uuid = Installation.id(context);
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
      sendServiceIntent(obj);
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
      sendServiceIntent(obj);
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
    sendServiceIntent(object);
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

  private void sendServiceIntent(JSONObject obj) {
    assert obj != null;
    enrich(obj);
    Intent intent = new Intent(context, LogseneService.class);
    intent.putExtra("obj", obj.toString());
    context.startService(intent);
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
