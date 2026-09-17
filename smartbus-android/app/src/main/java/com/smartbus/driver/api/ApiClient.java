package com.smartbus.driver.api;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    /**
     * Default production Render URL placeholder.
     * Replace <RENDER_BACKEND_DOMAIN> with your actual Render service domain
     * (e.g. https://smartbus-backend.onrender.com/api/)
     */
    public static final String PRODUCTION_BASE_URL = "https://<RENDER_BACKEND_DOMAIN>/api/";
    public static final String LOCAL_DEV_BASE_URL = "http://10.0.2.2:8080/api/";

    // Active base URL: configurable at runtime or defaults to BuildConfig/placeholder
    private static String activeBaseUrl = null;
    private static Retrofit retrofit = null;
    private static String token = null;

    /**
     * Resolve the active base URL.
     * Resolution order:
     * 1. Dynamically set base URL via setBaseUrl(String)
     * 2. BuildConfig.BASE_URL (if configured by Gradle build type)
     * 3. PRODUCTION_BASE_URL placeholder
     */
    public static String getBaseUrl() {
        if (activeBaseUrl != null && !activeBaseUrl.isBlank()) {
            return activeBaseUrl;
        }
        try {
            // Check if BuildConfig has a custom BASE_URL defined
            java.lang.reflect.Field field = Class.forName("com.smartbus.driver.BuildConfig").getField("BASE_URL");
            String buildConfigUrl = (String) field.get(null);
            if (buildConfigUrl != null && !buildConfigUrl.isBlank()) {
                return buildConfigUrl;
            }
        } catch (Throwable ignored) {
            // Fallback to default
        }
        return PRODUCTION_BASE_URL;
    }

    /**
     * Configure base URL dynamically at runtime (e.g. for switching between local emulator, LAN IP, and production Render)
     */
    public static void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        activeBaseUrl = baseUrl;
        retrofit = null; // Reinitialize Retrofit instance with new URL
    }

    public static void setToken(String jwtToken) {
        token = jwtToken;
    }

    public static ApiService getApiService() {
        if (retrofit == null) {
            OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
            
            // Add authorization header interceptor
            httpClient.addInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder();
                if (token != null) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }
                Request request = requestBuilder.build();
                return chain.proceed(request);
            });

            retrofit = new Retrofit.Builder()
                    .baseUrl(getBaseUrl())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(httpClient.build())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}
