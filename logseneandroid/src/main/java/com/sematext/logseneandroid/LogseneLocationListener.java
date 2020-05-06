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

import java.util.List;

/**
 * Location listener.
 */
public class LogseneLocationListener extends Activity implements LocationListener {
    private static final int REQUEST_LOCATION = 382173921;
    private LocationManager locationManager;
    private Location location;
    private boolean enabled;

    public LogseneLocationListener(Context context) {
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION },
                    REQUEST_LOCATION
            );
        } else {
            this.enabled = true;
            setupLocationRequests();
            retrieveLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                this.enabled = true;
                setupLocationRequests();
                retrieveLocation();
            }
        }
    }

    protected void setupLocationRequests() throws SecurityException {
        List<String> enabledLocationProviders = locationManager.getProviders(true);
        for (String provider : enabledLocationProviders) {
            locationManager.requestLocationUpdates(provider, 1000, 0, this);
        }
    }

    public void retrieveLocation() throws SecurityException {
        List<String> enabledLocationProviders = locationManager.getProviders(true);
        Location bestKnownLocation = null;
        for (String provider : enabledLocationProviders) {
            Location locationFromProvider = locationManager.getLastKnownLocation(provider);
            if (locationFromProvider == null) {
                continue;
            }
            if (bestKnownLocation == null || locationFromProvider.getAccuracy() < bestKnownLocation.getAccuracy()) {
                bestKnownLocation = locationFromProvider;
            }
        }
        this.location = bestKnownLocation;
    }

    public boolean isLocationPresent() {
        return enabled && location != null;
    }

    public String getLocationAsString() {
        try {
            if (isLocationPresent()) {
                return String.format("%.2f,%.2f", location.getLatitude(), location.getLongitude());
            }
        } catch (SecurityException se) {
            Log.e("ERROR", "Location services not allowed", se);
        }
        return "";
    }

    public void onLocationChanged(Location location) {}

    public void onProviderDisabled(String provider) {}

    public void onProviderEnabled(String provider) {}

    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
