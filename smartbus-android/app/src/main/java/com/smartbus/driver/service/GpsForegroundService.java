package com.smartbus.driver.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import com.smartbus.driver.database.LocationDbHelper;
import com.smartbus.driver.api.ApiClient;
import com.smartbus.driver.ui.MainActivity;
import java.time.LocalDateTime;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GpsForegroundService extends Service implements LocationListener {

    private static final String CHANNEL_ID = "GpsForegroundServiceChannel";
    private LocationManager locationManager;
    private LocationDbHelper dbHelper;
    private String tripId;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new LocationDbHelper(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        tripId = intent.getStringExtra("tripId");
        
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SmartBus GPS Service")
                .setContentText("Broadcasting live bus coordinates to campus tracking system...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        try {
            // Request location updates every 4 seconds or 2 meters
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 
                    4000, 
                    2.0f, 
                    this
            );
        } catch (SecurityException e) {
            System.err.println("GPS Permissions missing: " + e.getMessage());
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (tripId == null) return;

        double lat = location.getLatitude();
        double lng = location.getLongitude();
        double speed = location.hasSpeed() ? location.getSpeed() * 3.6 : 0.0; // convert to km/h
        double heading = location.hasBearing() ? location.getBearing() : 0.0;

        // Attempt to send live update to backend
        ApiClient.getApiService().updateLocation(tripId, lat, lng, speed, heading).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    cacheLocation(lat, lng, speed, heading);
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                cacheLocation(lat, lng, speed, heading);
            }
        });
    }

    private void cacheLocation(double lat, double lng, double speed, double heading) {
        dbHelper.insertLocation(lat, lng, speed, heading, LocalDateTime.now().toString());
        System.out.println("Network offline. GPS Telemetry cached locally in SQLite database.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.removeUpdates(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "GPS Tracking Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    // Unused lifecycle overrides
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
}
