package com.sematext.logseneapplication;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.sematext.logseneandroid.Logsene;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private int numberOfRepeatedMessages = 1000;
    private Thread unlimitedLoop = null;
    private Logsene logsene;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Logsene.init(this);
        logsene = logsene.getInstance();

        Log.e("INFO", "Android version: " + Build.VERSION.RELEASE);

        try {
            // Set some default meta properties to be sent with each message
            JSONObject meta = new JSONObject();
            meta.put("user", "user@example.com");
            meta.put("userType", "free");
            Logsene.setDefaultMeta(meta);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("INFO", "Sending single message to Sematext Cloud");
                logsene.info("Hello World!");
            }
        });

        Button logWithLocationButton = findViewById(R.id.logWithLocationButton);
        logWithLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("INFO", "Sending single message with location to Sematext Cloud");
                logsene.info("Hello World with Location!", 53.08, 23.08);
            }
        });

        final Button loopButton = findViewById(R.id.loopButton);
        loopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.e("INFO", String.format("Sending %d messages to Sematext Cloud",
                                numberOfRepeatedMessages));
                        for (int i = 0; i < numberOfRepeatedMessages; i++) {
                            logsene.info(String.format("This is a message number %d", i + 1));
                        }
                    }
                }).start();
            }
        });

        final Button startUnlimitedLoop = findViewById(R.id.startUnlimitedButton);
        startUnlimitedLoop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (unlimitedLoop == null) {
                    unlimitedLoop = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.e("INFO", "Starting unlimited loop");
                            while (true) {
                                logsene.info("This is another Sematext Cloud Logs message");
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException ex) {
                                    Log.e("INFO", "Loop interrupted, exiting");
                                    return;
                                }
                            }
                        }
                    });
                    unlimitedLoop.start();
                }
            }
        });

        final Button stopUnlimitedLoop = findViewById(R.id.stopUnlimitedButton);
        stopUnlimitedLoop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (unlimitedLoop != null) {
                    Log.e("INFO", "Stopping unlimited loop");
                    unlimitedLoop.interrupt();
                    unlimitedLoop = null;
                }
            }
        });

        Button troubleButton = findViewById(R.id.troubleButton);
        troubleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    // will always fail
                    JSONObject obj = new JSONObject("not valid json!");
                } catch (JSONException e) {
                    // send to centralized log with stacktrace
                    logsene.error(e);
                }
            }
        });

        Button crashButton = findViewById(R.id.crashButton);
        crashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int a = 1 / 0;
            }
        });

        try {
            JSONObject event = new JSONObject();
            event.put("activity", this.getClass().getSimpleName());
            event.put("action", "started");
            logsene.event(event);
        } catch (JSONException e) {
            Log.e("myapp", "Unable to construct json", e);
        }
    }
}