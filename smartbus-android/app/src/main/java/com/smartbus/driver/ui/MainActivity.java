package com.smartbus.driver.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.smartbus.driver.R;
import com.smartbus.driver.api.ApiClient;
import com.smartbus.driver.service.GpsForegroundService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private TextView tvDriverName, tvTripStatus;
    private Button btnToggleTrip, btnSos;
    private boolean isTripActive = false;
    private String activeTripId = "b1111111-1111-1111-1111-111111111111"; // seeded loop trip

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDriverName = findViewById(R.id.tvDriverName);
        tvTripStatus = findViewById(R.id.tvTripStatus);
        btnToggleTrip = findViewById(R.id.btnToggleTrip);
        btnSos = findViewById(R.id.btnSos);

        String driverName = getIntent().getStringExtra("driverName");
        if (driverName != null) {
            tvDriverName.setText("Driver: " + driverName);
        }

        btnToggleTrip.setOnClickListener(v -> {
            if (!isTripActive) {
                startBroadcasting();
            } else {
                stopBroadcasting();
            }
        });

        btnSos.setOnClickListener(v -> {
            Toast.makeText(this, "SOS DISPATCHED TO CAMPUS SECURITY!", Toast.LENGTH_LONG).show();
        });
    }

    private void startBroadcasting() {
        Intent serviceIntent = new Intent(this, GpsForegroundService.class);
        serviceIntent.putExtra("tripId", activeTripId);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        isTripActive = true;
        tvTripStatus.setText("Status: EN_ROUTE (Broadcasting GPS...)");
        btnToggleTrip.setText("Stop Current Trip");
        Toast.makeText(this, "Trip started successfully!", Toast.LENGTH_SHORT).show();
    }

    private void stopBroadcasting() {
        Intent serviceIntent = new Intent(this, GpsForegroundService.class);
        stopService(serviceIntent);

        // Tell backend trip has finished
        ApiClient.getApiService().endTrip(activeTripId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                // Ignore response, user has finished trip
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Ignore failure
            }
        });

        isTripActive = false;
        tvTripStatus.setText("Status: Idle");
        btnToggleTrip.setText("Start Trip");
        Toast.makeText(this, "Trip finished successfully!", Toast.LENGTH_SHORT).show();
    }
}
