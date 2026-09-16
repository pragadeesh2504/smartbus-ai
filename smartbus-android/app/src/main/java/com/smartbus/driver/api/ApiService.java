package com.smartbus.driver.api;

import com.smartbus.driver.api.model.LoginRequest;
import com.smartbus.driver.api.model.LoginResponse;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("trips/{tripId}/location")
    Call<Void> updateLocation(
            @Path("tripId") String tripId,
            @Query("latitude") double lat,
            @Query("longitude") double lng,
            @Query("speed") double speed,
            @Query("heading") double heading
    );

    @POST("trips/{tripId}/end")
    Call<Void> endTrip(@Path("tripId") String tripId);
}
