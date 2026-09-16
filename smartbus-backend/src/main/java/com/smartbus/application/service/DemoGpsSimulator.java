package com.smartbus.application.service;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DemoGpsSimulator {

    private final TripRepository tripRepository;
    private final RouteStopRepository routeStopRepository;
    private final HybridTrackingService hybridTrackingService;
    private final DemoGpsDeviceAdapter demoGpsDeviceAdapter;
    private final TripLocationRepository tripLocationRepository;

    // Simulation states
    @Getter @Setter private boolean gpsDeviceOnline = true;
    @Getter @Setter private boolean phoneGpsOnline = true;
    @Getter @Setter private boolean simulateAnomaly = false;

    @org.springframework.beans.factory.annotation.Value("${smartbus.simulation.enabled:false}")
    @Getter @Setter private boolean simulationEnabled = false;

    // Cache to track interpolation state: tripId -> Current Stop Index
    private final Map<UUID, Integer> tripStopIndex = new ConcurrentHashMap<>();
    // Cache to track step count between stops: tripId -> Step (0 to 5)
    private final Map<UUID, Integer> tripSteps = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${smartbus.simulation.interval-ms:5000}")
    @Transactional
    public void runSimulationStep() {
        if (!simulationEnabled) {
            return;
        }
        List<Trip> activeTrips = tripRepository.findByStatus("IN_PROGRESS");
        for (Trip trip : activeTrips) {
            simulateTripLocation(trip);
        }
    }

    private void simulateTripLocation(Trip trip) {
        UUID tripId = trip.getId();

        // If active real driver phone telemetry is streaming, yield and do not overwrite with simulated coordinates
        Optional<TripLocation> latest = tripLocationRepository.findFirstByTripIdOrderByTimestampDesc(tripId);
        if (latest.isPresent()) {
            TripLocation lastLoc = latest.get();
            if ("DRIVER_PHONE".equals(lastLoc.getTrackingSource())
                    && lastLoc.getTimestamp() != null
                    && lastLoc.getTimestamp().isAfter(LocalDateTime.now().minusSeconds(30))) {
                return;
            }
        }

        List<RouteStop> routeStops = routeStopRepository.findByRouteOrderBySequenceNumberAsc(trip.getRoute());
        if (routeStops.isEmpty()) {
            return;
        }

        int stopIndex = tripStopIndex.computeIfAbsent(tripId, id -> 0);
        int step = tripSteps.computeIfAbsent(tripId, id -> 0);

        Stop currentStop = routeStops.get(stopIndex).getStop();
        Stop nextStop = routeStops.get(Math.min(stopIndex + 1, routeStops.size() - 1)).getStop();

        // Interpolate coordinates
        double fraction = (double) step / 5.0;
        double currentLat = currentStop.getLatitude() + (nextStop.getLatitude() - currentStop.getLatitude()) * fraction;
        double currentLng = currentStop.getLongitude() + (nextStop.getLongitude() - currentStop.getLongitude()) * fraction;

        // Advance simulation step
        step++;
        if (step > 5) {
            step = 0;
            if (stopIndex < routeStops.size() - 1) {
                stopIndex++;
            } else {
                // Reached destination, do not auto-complete unless driver ends it, just hold at final stop
                fraction = 1.0;
                currentLat = nextStop.getLatitude();
                currentLng = nextStop.getLongitude();
            }
        }
        tripStopIndex.put(tripId, stopIndex);
        tripSteps.put(tripId, step);

        // Generate data payloads
        LocalDateTime now = LocalDateTime.now();

        // 1. GPS Device Telemetry
        if (gpsDeviceOnline) {
            GpsDevice dev = trip.getBus().getGpsDevice();
            if (dev != null) {
                // If anomaly requested, offset the device location significantly
                double lat = currentLat;
                double lng = currentLng;
                if (simulateAnomaly) {
                    lat += 0.01; // Approx 1.1km offset to trigger > 500m anomaly
                    lng += 0.01;
                }

                LocationData devicePayload = LocationData.builder()
                        .latitude(lat)
                        .longitude(lng)
                        .speed(30.0)
                        .heading(90.0)
                        .accuracy(5.0)
                        .timestamp(now)
                        .batteryLevel(85)
                        .deviceStatus("ONLINE")
                        .build();

                // Push to memory adapter
                demoGpsDeviceAdapter.setDeviceOnline(dev.getId(), true);
                demoGpsDeviceAdapter.pushDeviceLocation(dev.getId(), devicePayload);
            }
        } else {
            GpsDevice dev = trip.getBus().getGpsDevice();
            if (dev != null) {
                demoGpsDeviceAdapter.setDeviceOnline(dev.getId(), false);
            }
        }

        // 2. Driver Phone Telemetry
        LocationData phonePayload = null;
        if (phoneGpsOnline) {
            phonePayload = LocationData.builder()
                    .latitude(currentLat)
                    .longitude(currentLng)
                    .speed(30.0)
                    .heading(90.0)
                    .accuracy(8.0)
                    .timestamp(now)
                    .build();
        }

        // Trigger location update processing in the tracking engine
        // This simulates updates coming in from the phone client (which triggers hybrid matching)
        if (phoneGpsOnline || gpsDeviceOnline) {
            hybridTrackingService.processLocationUpdate(
                    tripId,
                    phonePayload,
                    "SIM-EVENT-" + tripId + "-" + System.currentTimeMillis()
            );
        }
    }

    public void resetSimulation(UUID tripId) {
        tripStopIndex.remove(tripId);
        tripSteps.remove(tripId);
    }
}
