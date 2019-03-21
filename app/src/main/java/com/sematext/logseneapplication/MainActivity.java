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
    private Logsene logsene;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logsene = new Logsene(this);

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
                logsene.info("Hello World!");
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