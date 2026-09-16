package com.smartbus.application.service;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DemoGpsDeviceAdapter implements GpsDeviceTelemetryAdapter, GpsTrackingProvider {

    private final Map<UUID, LocationData> deviceLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> deviceStatus = new ConcurrentHashMap<>();

    @Override
    public void pushDeviceLocation(UUID deviceId, LocationData locationData) {
        deviceLocations.put(deviceId, locationData);
        if (!deviceStatus.containsKey(deviceId)) {
            deviceStatus.put(deviceId, true);
        }
    }

    @Override
    public LocationData getLatestLocation(UUID deviceId) {
        Boolean online = deviceStatus.getOrDefault(deviceId, true);
        if (!online) {
            return null;
        }
        LocationData data = deviceLocations.get(deviceId);
        if (data != null && data.getTimestamp().isBefore(LocalDateTime.now().minusSeconds(15))) {
            // Treat as offline/stale if last update is older than 15 seconds
            return null;
        }
        return data;
    }

    public void setDeviceOnline(UUID deviceId, boolean online) {
        deviceStatus.put(deviceId, online);
    }
}
