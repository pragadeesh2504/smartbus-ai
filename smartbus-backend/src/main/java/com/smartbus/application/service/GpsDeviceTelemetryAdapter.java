package com.smartbus.application.service;

import java.util.UUID;

public interface GpsDeviceTelemetryAdapter {
    void pushDeviceLocation(UUID deviceId, LocationData locationData);
    LocationData getLatestLocation(UUID deviceId);
}
