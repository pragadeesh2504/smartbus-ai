package com.smartbus.application.service;

import com.smartbus.application.port.in.GpsUseCase;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GpsService implements GpsUseCase {

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final RouteStopRepository routeStopRepository;
    private final NotificationRepository notificationRepository;
    private final GeofencingService geofencingService;
    private final AnomalyDetectionService anomalyDetectionService;

    // Filters cache: tripId -> KalmanFilter
    private final Map<UUID, KalmanFilter> latFilters = new ConcurrentHashMap<>();
    private final Map<UUID, KalmanFilter> lngFilters = new ConcurrentHashMap<>();
    
    // Visited stops tracking: tripId -> Set of stopIds visited
    private final Map<UUID, Set<UUID>> visitedStops = new ConcurrentHashMap<>();

    @Override
    public void processLocationUpdate(UUID tripId, Double latitude, Double longitude, Double speed, Double heading) {
        // SEC-08: Validate telemetry at service/business layer
        GpsValidationUtil.validateTelemetry(latitude, longitude, speed, heading);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));

        if (!"EN_ROUTE".equals(trip.getStatus())) {
            // Only process updates for active trips
            return;
        }

        // Initialize or get Kalman Filters
        KalmanFilter latFilter = latFilters.computeIfAbsent(tripId, id -> new KalmanFilter(0.00001, 0.0001));
        KalmanFilter lngFilter = lngFilters.computeIfAbsent(tripId, id -> new KalmanFilter(0.00001, 0.0001));

        double smoothedLat = latFilter.filter(latitude);
        double smoothedLng = lngFilter.filter(longitude);

        // 1. Save smoothed telemetry to GPS locations log
        GpsLocation gpsLocation = GpsLocation.builder()
                .trip(trip)
                .latitude(smoothedLat)
                .longitude(smoothedLng)
                .speed(speed)
                .heading(heading)
                .build();
        gpsLocationRepository.save(gpsLocation);

        // 2. Update the physical Bus object live coordinates
        Bus bus = trip.getBus();
        bus.setCurrentLatitude(smoothedLat);
        bus.setCurrentLongitude(smoothedLng);
        bus.setLastUpdated(LocalDateTime.now());
        busRepository.save(bus);

        // 3. Geofencing check for stop detection via strict progression state machine
        geofencingService.processGeofencing(trip, smoothedLat, smoothedLng);
        
        // 4. Run AI telemetry anomaly check
        anomalyDetectionService.analyzeTelemetry(tripId, smoothedLat, smoothedLng, speed);
    }
    
    // Cleanup filters when trip ends
    public void clearTripCache(UUID tripId) {
        latFilters.remove(tripId);
        lngFilters.remove(tripId);
        visitedStops.remove(tripId);
    }
}
