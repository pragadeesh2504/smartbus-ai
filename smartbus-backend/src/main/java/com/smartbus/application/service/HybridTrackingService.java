package com.smartbus.application.service;

import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.dto.EtaResponse;
import com.smartbus.websocket.LiveLocationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class HybridTrackingService {

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final StopRepository stopRepository;
    private final TripLocationRepository tripLocationRepository;
    private final AuditLogService auditLogService;
    private final SettingRepository settingRepository;
    private final GeofencingService geofencingService;
    private final DemoGpsDeviceAdapter demoGpsDeviceAdapter;
    private final LiveLocationWebSocketHandler webSocketHandler;
    private final EtaCalculationService etaCalculationService;
    private final SmartNotificationService smartNotificationService;

    // Kalman Filters for phone coordinates
    private final Map<UUID, KalmanFilter> phoneLatFilters = new HashMap<>();
    private final Map<UUID, KalmanFilter> phoneLngFilters = new HashMap<>();

    // Kalman Filters for device coordinates
    private final Map<UUID, KalmanFilter> deviceLatFilters = new HashMap<>();
    private final Map<UUID, KalmanFilter> deviceLngFilters = new HashMap<>();

    public void processLocationUpdate(UUID tripId, LocationData phoneData, String sourceEventId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BadRequestException("Trip not found"));

        if (!"IN_PROGRESS".equals(trip.getStatus())) {
            return;
        }

        // Idempotency Check
        if (sourceEventId != null) {
            Optional<TripLocation> existingLoc = tripLocationRepository.findBySourceEventId(sourceEventId);
            if (existingLoc.isPresent()) {
                return; // Return immediately for duplicate transmission
            }
        }

        // Validate Phone Data
        if (phoneData != null) {
            validateLocationData(phoneData);
        }

        // Check Dedicated GPS Device telemetry
        GpsDevice gpsDevice = trip.getBus().getGpsDevice();
        LocationData deviceData = null;
        if (gpsDevice != null) {
            deviceData = demoGpsDeviceAdapter.getLatestLocation(gpsDevice.getId());
            if (deviceData != null) {
                validateLocationData(deviceData);
            }
        }

        LocationData selectedData = null;
        String trackingSource = "LAST_KNOWN";

        // Kalman Smoothing & Hybrid Selection Logic
        if (deviceData != null && phoneData != null) {
            // Both available: prefer GPS DEVICE, cross-check for anomalies
            double distanceDiff = geofencingService.calculateDistance(
                    phoneData.getLatitude(), phoneData.getLongitude(),
                    deviceData.getLatitude(), deviceData.getLongitude()
            );

            double anomalyThreshold = getAnomalyThreshold();
            if (distanceDiff > anomalyThreshold) {
                triggerTelemetryAnomaly(trip, phoneData, deviceData, distanceDiff);
            }

            selectedData = smoothDeviceData(tripId, deviceData);
            trackingSource = "GPS_DEVICE";

        } else if (deviceData != null) {
            // Only Device available
            selectedData = smoothDeviceData(tripId, deviceData);
            trackingSource = "GPS_DEVICE";

        } else if (phoneData != null) {
            // Only Phone available (Device is offline)
            selectedData = smoothPhoneData(tripId, phoneData);
            trackingSource = "DRIVER_PHONE";

        } else {
            // Both offline
            trackingSource = "LAST_KNOWN";
            triggerTrackingDegraded(trip);
        }

        if (selectedData != null) {
            Double speedVal = selectedData.getSpeed() != null ? selectedData.getSpeed() : 0.0;
            Double headingVal = selectedData.getHeading() != null ? selectedData.getHeading() : 0.0;
            Double accuracyVal = selectedData.getAccuracy() != null ? selectedData.getAccuracy() : 5.0;
            LocalDateTime timestampVal = selectedData.getTimestamp() != null ? selectedData.getTimestamp() : LocalDateTime.now();

            // Persist location
            TripLocation tripLoc = TripLocation.builder()
                    .trip(trip)
                    .latitude(selectedData.getLatitude())
                    .longitude(selectedData.getLongitude())
                    .speed(speedVal)
                    .heading(headingVal)
                    .accuracy(accuracyVal)
                    .timestamp(timestampVal)
                    .trackingSource(trackingSource)
                    .sourceEventId(sourceEventId != null ? sourceEventId : UUID.randomUUID().toString())
                    .build();
            tripLocationRepository.save(tripLoc);

            // Update physical bus current position
            Bus bus = trip.getBus();
            bus.setCurrentLatitude(selectedData.getLatitude());
            bus.setCurrentLongitude(selectedData.getLongitude());
            bus.setLastUpdated(LocalDateTime.now());
            busRepository.save(bus);

            // Run geofencing checks for stop detections
            geofencingService.processGeofencing(trip, selectedData.getLatitude(), selectedData.getLongitude());

            // Broadcast standard location update via WebSocket
            Map<String, Object> enrichedPayload = new HashMap<>();
            enrichedPayload.put("type", "BUS_LOCATION_UPDATE");
            enrichedPayload.put("tripId", trip.getId().toString());
            enrichedPayload.put("busId", bus.getId().toString());
            enrichedPayload.put("busCode", bus.getBusCode());
            enrichedPayload.put("busNumber", bus.getBusNumber());
            enrichedPayload.put("latitude", selectedData.getLatitude());
            enrichedPayload.put("longitude", selectedData.getLongitude());
            enrichedPayload.put("speed", speedVal);
            enrichedPayload.put("heading", headingVal);
            enrichedPayload.put("accuracy", accuracyVal);
            enrichedPayload.put("trackingSource", trackingSource);
            enrichedPayload.put("timestamp", timestampVal.toString());
            webSocketHandler.broadcast(enrichedPayload);

            // Phase 5: Calculate Live ETA and Check Route Deviation & Delay Events
            try {
                EtaResponse eta = etaCalculationService.calculateLiveTripEta(trip.getId());
                if (eta != null) {
                    // Check Route Deviation alert
                    if (eta.isOffRoute()) {
                        smartNotificationService.handleRouteDeviation(trip, eta.getRouteDeviationMeters(), true);
                    } else if (eta.getRouteDeviationMeters() != null && eta.getRouteDeviationMeters() <= 500.0) {
                        // Return to route check
                        smartNotificationService.handleRouteDeviation(trip, eta.getRouteDeviationMeters(), false);
                    }

                    // Check Delay alert
                    if ("DELAYED".equalsIgnoreCase(eta.getStatus()) && eta.getDelayMinutes() != null && eta.getDelayMinutes() > 0) {
                        Stop nextStop = null;
                        if (eta.getNextStopId() != null) {
                            nextStop = stopRepository.findById(eta.getNextStopId()).orElse(null);
                        }
                        smartNotificationService.handleTripDelayed(trip, eta.getDelayMinutes(), nextStop);
                    }

                    // Broadcast ETA_UPDATED event
                    Map<String, Object> etaPayload = new HashMap<>();
                    etaPayload.put("type", "ETA_UPDATED");
                    etaPayload.put("tripId", trip.getId().toString());
                    etaPayload.put("busId", bus.getId().toString());
                    etaPayload.put("busNumber", bus.getBusNumber());
                    etaPayload.put("busCode", bus.getBusCode());
                    etaPayload.put("routeName", trip.getRoute().getRouteName());
                    etaPayload.put("currentStopId", eta.getCurrentStopId() != null ? eta.getCurrentStopId().toString() : null);
                    etaPayload.put("currentStopName", eta.getCurrentStopName());
                    etaPayload.put("nextStopId", eta.getNextStopId() != null ? eta.getNextStopId().toString() : null);
                    etaPayload.put("nextStopName", eta.getNextStopName());
                    etaPayload.put("nextStopSequence", eta.getNextStopSequence());
                    etaPayload.put("distanceMeters", eta.getDistanceMeters());
                    etaPayload.put("minutesRemaining", eta.getMinutesRemaining());
                    etaPayload.put("estimatedArrivalTime", eta.getEstimatedArrivalTime());
                    etaPayload.put("status", eta.getStatus());
                    etaPayload.put("confidence", eta.getConfidence());
                    etaPayload.put("offRoute", eta.isOffRoute());
                    etaPayload.put("gpsStale", eta.isGpsStale());
                    etaPayload.put("gpsStatus", eta.getGpsStatus() != null ? eta.getGpsStatus() : (eta.isGpsStale() ? "GPS_STALE" : "LIVE"));
                    etaPayload.put("routeDeviationMeters", eta.getRouteDeviationMeters());
                    etaPayload.put("delayMinutes", eta.getDelayMinutes());
                    etaPayload.put("upcomingStops", eta.getUpcomingStops());
                    etaPayload.put("routeProgress", eta.getRouteProgress());
                    etaPayload.put("timestamp", LocalDateTime.now().toString());

                    webSocketHandler.broadcast(etaPayload);
                }
            } catch (Exception e) {
                // Log ETA broadcast error without interrupting core telemetry ingestion
            }
        }
    }

    private void validateLocationData(LocationData data) {
        if (data == null) {
            throw new BadRequestException("Location data cannot be null");
        }
        // SEC-08: Validate coordinates, speed, heading, accuracy, and timestamp
        GpsValidationUtil.validateLatitude(data.getLatitude());
        GpsValidationUtil.validateLongitude(data.getLongitude());
        GpsValidationUtil.validateSpeed(data.getSpeed());
        GpsValidationUtil.validateHeading(data.getHeading());
        GpsValidationUtil.validateAccuracy(data.getAccuracy());
        GpsValidationUtil.validateTimestamp(data.getTimestamp());
    }

    private LocationData smoothPhoneData(UUID tripId, LocationData raw) {
        if (raw == null) return null;
        if (raw.getAccuracy() != null && raw.getAccuracy() <= 20.0) {
            return raw;
        }
        KalmanFilter latFilter = phoneLatFilters.computeIfAbsent(tripId, id -> new KalmanFilter(0.001, 0.0001));
        KalmanFilter lngFilter = phoneLngFilters.computeIfAbsent(tripId, id -> new KalmanFilter(0.001, 0.0001));
        return LocationData.builder()
                .latitude(latFilter.filter(raw.getLatitude()))
                .longitude(lngFilter.filter(raw.getLongitude()))
                .speed(raw.getSpeed())
                .heading(raw.getHeading())
                .accuracy(raw.getAccuracy())
                .timestamp(raw.getTimestamp())
                .build();
    }

    private LocationData smoothDeviceData(UUID tripId, LocationData raw) {
        if (raw == null) return null;
        if (raw.getAccuracy() != null && raw.getAccuracy() <= 20.0) {
            return raw;
        }
        KalmanFilter latFilter = deviceLatFilters.computeIfAbsent(tripId, id -> new KalmanFilter(0.001, 0.0001));
        KalmanFilter lngFilter = deviceLngFilters.computeIfAbsent(tripId, id -> new KalmanFilter(0.001, 0.0001));
        return LocationData.builder()
                .latitude(latFilter.filter(raw.getLatitude()))
                .longitude(lngFilter.filter(raw.getLongitude()))
                .speed(raw.getSpeed())
                .heading(raw.getHeading())
                .accuracy(raw.getAccuracy())
                .timestamp(raw.getTimestamp())
                .build();
    }

    private void triggerTelemetryAnomaly(Trip trip, LocationData phone, LocationData device, double diff) {
        String logDesc = String.format("GPS anomaly detected for Bus: %s. Phone (lat: %s, lon: %s) differed from Device (lat: %s, lon: %s) by %.2f meters.",
                trip.getBus().getBusNumber(), phone.getLatitude(), phone.getLongitude(), device.getLatitude(), device.getLongitude(), diff);

        auditLogService.logAction(
                trip.getDriver().getUser(),
                "GPS_ANOMALY_DETECTED",
                logDesc,
                "127.0.0.1",
                "Trip",
                trip.getId().toString(),
                null,
                logDesc
        );

        // Broadcast warning to clients
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "GPS_SOURCE_ANOMALY");
        alert.put("tripId", trip.getId().toString());
        alert.put("busNumber", trip.getBus().getBusNumber());
        alert.put("driverName", trip.getDriver().getUser().getFirstName() + " " + trip.getDriver().getUser().getLastName());
        alert.put("diffMeters", diff);
        alert.put("timestamp", LocalDateTime.now().toString());
        webSocketHandler.broadcast(alert);
    }

    private void triggerTrackingDegraded(Trip trip) {
        auditLogService.logAction(
                trip.getDriver().getUser(),
                "GPS_SOURCE_CHANGED",
                "TRACKING_DEGRADED: Both dedicated GPS device and phone GPS are offline.",
                "127.0.0.1",
                "Trip",
                trip.getId().toString(),
                null,
                "TRACKING_DEGRADED: Both dedicated GPS device and phone GPS are offline."
        );

        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "TRACKING_DEGRADED");
        alert.put("tripId", trip.getId().toString());
        alert.put("busNumber", trip.getBus().getBusNumber());
        alert.put("timestamp", LocalDateTime.now().toString());
        webSocketHandler.broadcast(alert);
    }

    public void clearTripCache(UUID tripId) {
        phoneLatFilters.remove(tripId);
        phoneLngFilters.remove(tripId);
        deviceLatFilters.remove(tripId);
        deviceLngFilters.remove(tripId);
        etaCalculationService.clearTripState(tripId);
        smartNotificationService.clearTripCache(tripId);
    }

    private double getAnomalyThreshold() {
        return settingRepository.findById("GPS_ANOMALY_THRESHOLD_METERS")
                .map(s -> {
                    try {
                        return Double.parseDouble(s.getValue());
                    } catch (NumberFormatException e) {
                        return 500.0;
                    }
                }).orElse(500.0);
    }
}
