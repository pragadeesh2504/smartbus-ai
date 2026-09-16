package com.smartbus.application.service;

import java.util.UUID;

public interface GpsTrackingProvider {
    LocationData getLatestLocation(UUID deviceId);
}
