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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.List;

/**
 * Location listener.
 */
public class LogseneLocationListener implements LocationListener {
    private static final int REQUEST_LOCATION = 382173921;
    private LocationManager locationManager;
    private Location location;
    private boolean enabled;

    public LogseneLocationListener(Context context) {
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        if (resultCode != ConnectionResult.SUCCESS) {
            this.enabled = false;
        } else {
            this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            setupLocationRequests();
            this.enabled = true;
            retrieveLocation();
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

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;
    }

    public void onProviderDisabled(String provider) {}

    public void onProviderEnabled(String provider) {}

    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
