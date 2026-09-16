package com.smartbus.application.service;

import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.NotificationRepository;
import com.smartbus.infrastructure.adapter.jpa.RouteStopRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AnomalyDetectionService {

    private final TripRepository tripRepository;
    private final RouteStopRepository routeStopRepository;
    private final NotificationRepository notificationRepository;
    private final GeofencingService geofencingService;

    /**
     * Checks a location ping telemetry for driver safety anomalies and route deviations.
     */
    public void analyzeTelemetry(UUID tripId, double latitude, double longitude, double speed) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        Bus bus = trip.getBus();

        // 1. Overspeeding Anomaly (Limit: 60 km/h)
        if (speed > 60.0) {
            String alertMsg = "Vehicle " + bus.getBusNumber() + " (Route: " + trip.getRoute().getRouteName() + ") is overspeeding at " + Math.round(speed) + " km/h!";
            triggerAnomalyNotification("Overspeeding Alert", alertMsg, "EMERGENCY");
        }

        // 2. Route Deviation Anomaly
        // Verify if the vehicle is within 500 meters of any stop on its assigned route
        List<RouteStop> stops = routeStopRepository.findByRouteOrderBySequenceNumberAsc(trip.getRoute());
        boolean onTrack = false;
        for (RouteStop rs : stops) {
            double dist = geofencingService.calculateDistance(latitude, longitude, rs.getStop().getLatitude(), rs.getStop().getLongitude());
            if (dist <= 500.0) { // Route corridor: 500m buffer from scheduled stops
                onTrack = true;
                break;
            }
        }

        if (!stops.isEmpty() && !onTrack) {
            String alertMsg = "Vehicle " + bus.getBusNumber() + " has deviated from Route " + trip.getRoute().getRouteName() + "!";
            triggerAnomalyNotification("Route Deviation", alertMsg, "EMERGENCY");
        }
    }

    private void triggerAnomalyNotification(String title, String message, String type) {
        Notification notification = Notification.builder()
                .title(title)
                .message(message)
                .type(type)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
        log.warn("Anomaly alert: [{}] - {}", title, message);
    }
}
