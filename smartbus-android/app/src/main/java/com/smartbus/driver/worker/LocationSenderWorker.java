package com.smartbus.driver.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.smartbus.driver.database.LocationDbHelper;
import com.smartbus.driver.api.ApiClient;
import java.io.IOException;
import java.util.List;
import retrofit2.Response;

public class LocationSenderWorker extends Worker {

    private final LocationDbHelper dbHelper;

    public LocationSenderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        dbHelper = new LocationDbHelper(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        String tripId = getInputData().getString("tripId");
        if (tripId == null) {
            return Result.failure();
        }

        List<LocationDbHelper.CachedLocation> cachedLocations = dbHelper.getAllCachedLocations();
        if (cachedLocations.isEmpty()) {
            return Result.success();
        }

        boolean allSynced = true;

        for (LocationDbHelper.CachedLocation cached : cachedLocations) {
            try {
                // Synchronous Retrofit call for reliable background sync
                Response<Void> response = ApiClient.getApiService().updateLocation(
                        tripId,
                        cached.getLatitude(),
                        cached.getLongitude(),
                        cached.getSpeed(),
                        cached.getHeading()
                ).execute();

                if (response.isSuccessful()) {
                    dbHelper.deleteLocation(cached.getId());
                } else {
                    allSynced = false;
                }
            } catch (IOException e) {
                allSynced = false;
            }
        }

        return allSynced ? Result.success() : Result.retry();
    }
}
