package com.sematext.logseneandroid;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.List;
import java.util.Locale;

/**
 * Location listener.
 */
public class LogseneLocationListener {
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationManager locationManager;
    private Location location;
    private boolean enabled;

    public LogseneLocationListener(Context context) {
        this.enabled = true;
        this.fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (checkPermissions(context)) {
            setupLocationRequests();
            retrieveLocation();
        } else {
            Log.e("WARN", "Location services permissions not granted, location not available");
        }
    }

    @SuppressWarnings("MissingPermission")
    private void setupLocationRequests() {
        List<String> enabledLocationProviders = locationManager.getProviders(true);
        for (String provider : enabledLocationProviders) {
            locationManager.requestLocationUpdates(provider, 1000, 0, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {}

                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {}

                @Override
                public void onProviderEnabled(String s) {}

                @Override
                public void onProviderDisabled(String s) {}
            });
        }
    }

    @SuppressWarnings("MissingPermission")
    private void retrieveLocation() {
        this.fusedLocationProviderClient.getLastLocation().
                addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        location = task.getResult();
                    }
                });
    }

    public boolean isLocationPresent() {
        return location != null;
    }

    public String getLocationAsString() {
        try {
            if (isLocationPresent()) {
                return String.format("%.2f,%.2f", location.getLatitude(), location.getLongitude());
            } else {
                retrieveLocation();
            }
        } catch (SecurityException se) {
            Log.e("ERROR", "Location services not allowed", se);
        }
        return "";
    }

    private boolean checkPermissions(Context context) {
        int permissionState = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }
}