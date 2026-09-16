package com.smartbus.application.service;

import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.RouteStopRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EtaPredictorService {

    private final TripRepository tripRepository;
    private final RouteStopRepository routeStopRepository;
    private final GeofencingService geofencingService;

    /**
     * Predicts arrival ETA (in minutes) for a target stop on an active trip.
     */
    public int predictEtaMins(UUID tripId, UUID stopId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        List<RouteStop> stops = routeStopRepository.findByRouteOrderBySequenceNumberAsc(trip.getRoute());
        
        // 1. Locate the target stop sequence
        RouteStop targetStop = null;
        for (RouteStop rs : stops) {
            if (rs.getStop().getId().equals(stopId)) {
                targetStop = rs;
                break;
            }
        }
        if (targetStop == null) {
            throw new ResourceNotFoundException("Stop is not on this trip's route");
        }

        // 2. Find closest stop sequence (current position estimation of the bus)
        Double busLat = trip.getBus().getCurrentLatitude();
        Double busLng = trip.getBus().getCurrentLongitude();
        if (busLat == null || busLng == null) {
            // Fallback: return default duration from start
            return targetStop.getDurationFromStartMins();
        }

        RouteStop closestStop = null;
        double minDistance = Double.MAX_VALUE;
        for (RouteStop rs : stops) {
            double dist = geofencingService.calculateDistance(busLat, busLng, rs.getStop().getLatitude(), rs.getStop().getLongitude());
            if (dist < minDistance) {
                minDistance = dist;
                closestStop = rs;
            }
        }

        if (closestStop == null) {
            return targetStop.getDurationFromStartMins();
        }

        // If target stop is behind current bus sequence, return 0 (already passed)
        if (closestStop.getSequenceNumber() >= targetStop.getSequenceNumber()) {
            return 0;
        }

        // 3. Distance calculation along route stops
        double remainingDistance = targetStop.getDistanceFromStart() - closestStop.getDistanceFromStart();

        // 4. Base transit travel estimation (average speed 30 km/h -> 0.5 km per min)
        double baseSpeedKmMin = 0.5; // 30 km/h
        double eta = remainingDistance / baseSpeedKmMin;

        // 5. Peak Hour Traffic Adjustment (Dynamic Multiplier)
        LocalTime now = LocalTime.now();
        boolean isMorningPeak = now.isAfter(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(10, 0));
        boolean isEveningPeak = now.isAfter(LocalTime.of(16, 30)) && now.isBefore(LocalTime.of(18, 30));
        
        if (isMorningPeak || isEveningPeak) {
            eta *= 1.35; // 35% delay increase during rush hours
        }

        // 6. Live Delay Adjustment
        // If bus is behind schedule, scale remaining segment times proportionally
        if (trip.getSchedule() != null) {
            LocalTime scheduledDep = trip.getSchedule().getDepartureTime();
            LocalTime actualNow = LocalTime.now();
            long expectedMinsElapsed = java.time.temporal.ChronoUnit.MINUTES.between(scheduledDep, actualNow);
            long scheduledMinsElapsed = closestStop.getDurationFromStartMins();
            
            if (expectedMinsElapsed > scheduledMinsElapsed) {
                double delayFactor = (double) expectedMinsElapsed / Math.max(1, scheduledMinsElapsed);
                // Clamp delay factor to avoid extreme spikes
                delayFactor = Math.min(2.0, Math.max(1.0, delayFactor));
                eta *= delayFactor;
            }
        }

        return (int) Math.round(eta);
    }
}
