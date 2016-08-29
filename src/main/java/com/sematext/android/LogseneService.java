package com.sematext.android;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.sematext.android.logsene.LogseneClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Android service for uploading Logsene data.
 *
 * <p>The service will periodically upload data to Logsene to preserve battery. The
 * service needs to be defined in your manifest file with two required meta data
 * tags. See the following example:</p>
 *
 * <pre>
 * {@code
 * <service android:name="com.sematext.android.LogseneService" android:exported="false">
 *     <meta-data android:name="appToken" android:value="yourapptoken" />
 *     <meta-data android:name="type" android:value="example" />
 * </service>
 * }</pre>
 *
 * <b>Important:</b> Don't send intents to this service directly, use {@link Logsene} instead.
 */
public class LogseneService extends Service {
  private final String TAG = getClass().getSimpleName();
  private final String RECEIVER_URL = "https://logsene-receiver.sematext.com";
  private final int OFFLINE_MAX_MESSAGES = 5000;
  private LogseneClient client;
  private LogWorker worker;

  private void config(Bundle data) {
    // required fields
    if (!data.containsKey("appToken")) {
      throw new RuntimeException("Please provide <meta-data name=\"appToken\" value=\"yourapptoken\"> in <service>");
    } else if (!data.containsKey("type")) {
      throw new RuntimeException("Please provide <meta-data name=\"type\" value=\"example\"> in <service>");
    }
    String appToken = data.getString("appToken");
    String type = data.getString("type");

    // optional fields
    String receiverUrl = data.getString("receiverUrl", RECEIVER_URL);
    int offlineMaxMessages = data.getInt("maxOfflineMessages", OFFLINE_MAX_MESSAGES);

    this.client = new LogseneClient(receiverUrl, appToken);
    try {
      this.worker = new LogWorker(this, client, appToken, type, offlineMaxMessages);
    } catch (IOException e) {
      // don't throw runtime exception as this is probably not the developers fault
      Log.e(TAG, "Cannot initialize log worker", e);
      stopSelf();
    }
  }

  @Override
  public void onCreate() {
    Log.d(TAG, "Logsene service starting");
    ComponentName myService = new ComponentName(this, this.getClass());
    Bundle data = null;
    try {
      data = getPackageManager().getServiceInfo(myService, PackageManager.GET_META_DATA).metaData;
    } catch (PackageManager.NameNotFoundException e) {
      throw new RuntimeException(e);
    }
    config(data);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null) {
      String jsonString = intent.getStringExtra("obj");
      if (jsonString != null) {
        try {
          JSONObject obj = new JSONObject(jsonString);
          this.worker.addToQueue(obj);
        } catch (JSONException e) {
          throw new RuntimeException(e);
        }
      }
    }
    // todo: stop service and log worker when no more messages available
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    // don't allow binding
    return null;
  }
}
