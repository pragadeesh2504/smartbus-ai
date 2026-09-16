package com.smartbus.application.service;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class GeofencingService {

    private static final double EARTH_RADIUS_METERS = 6371000.0;
    
    @Autowired
    private SettingRepository settingRepository;
    @Autowired
    private TripStopEventRepository tripStopEventRepository;
    @Autowired
    private RouteStopRepository routeStopRepository;
    @Autowired
    private TripRepository tripRepository;

    @Autowired @Lazy
    private SmartNotificationService smartNotificationService;
    @Autowired @Lazy
    private EtaCalculationService etaCalculationService;

    /**
     * Calculates the geodetic distance between two points using the Haversine formula.
     * Returns distance in meters.
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
                   
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    public boolean isInsideGeofence(double lat1, double lon1, double lat2, double lon2, double radiusMeters) {
        return calculateDistance(lat1, lon1, lat2, lon2) <= radiusMeters;
    }

    /**
     * Processes geofence entry/exit events for a trip location update.
     * STRICT ROUTE-PROGRESSION STATE MACHINE:
     * - Only the stop matching currentRequiredSequence can be evaluated for ARRIVED_AT_STOP.
     * - Sequence monotonicity: once reached, cursor advances by 1, and only then is the next stop eligible.
     * - Invariant: sequences < currentRequiredSequence are PASSED. sequences > currentRequiredSequence remain UPCOMING.
     */
    public synchronized void processGeofencing(Trip trip, double latitude, double longitude) {
        if (trip == null || !"IN_PROGRESS".equals(trip.getStatus())) {
            return;
        }

        // Re-read latest persisted trip to ensure atomic progression cursor across concurrent packets
        Trip currentTrip = tripRepository.findById(trip.getId()).orElse(trip);
        int currentRequiredSeq = currentTrip.getCurrentStopSequence() != null ? currentTrip.getCurrentStopSequence() : 1;

        double arrivalRadius = getGeofenceRadius(); // defaults to 100
        double approachingRadius = getApproachingRadius(); // defaults to 500
        List<RouteStop> routeStops = getRouteStopsForTrip(currentTrip);

        if (routeStops.isEmpty()) {
            return;
        }

        // Find the single route stop corresponding to currentRequiredSeq
        RouteStop expectedRouteStop = null;
        for (RouteStop rs : routeStops) {
            if (rs.getSequenceNumber() == currentRequiredSeq) {
                expectedRouteStop = rs;
                break;
            }
        }

        if (expectedRouteStop == null) {
            // Already passed all stops or invalid sequence
            return;
        }

        Stop currentTargetStop = expectedRouteStop.getStop();
        double distance = calculateDistance(latitude, longitude, currentTargetStop.getLatitude(), currentTargetStop.getLongitude());

        // 1. Check Approaching (within approachingRadius) for ONLY the expected stop
        if (distance <= approachingRadius) {
            Optional<TripStopEvent> approachingOpt = tripStopEventRepository.findByTripIdAndStopIdAndEventType(
                    currentTrip.getId(), currentTargetStop.getId(), "APPROACHING_STOP");
            if (approachingOpt.isEmpty()) {
                TripStopEvent approachingEvent = TripStopEvent.builder()
                        .trip(currentTrip)
                        .stop(currentTargetStop)
                        .eventType("APPROACHING_STOP")
                        .timestamp(LocalDateTime.now())
                        .build();
                tripStopEventRepository.save(approachingEvent);

                int eta = 3;
                try {
                    if (etaCalculationService != null) {
                        var etaResp = etaCalculationService.calculateEta(currentTrip.getId(), currentTargetStop.getId());
                        eta = etaResp.getMinutesRemaining() != null && etaResp.getMinutesRemaining() >= 0 ? etaResp.getMinutesRemaining() : 3;
                    }
                } catch (Exception ignored) {}

                if (smartNotificationService != null) {
                    smartNotificationService.handleApproachingStop(currentTrip, currentTargetStop, eta);
                }
            }
        }

        // 2. Check Arrived (within arrivalRadius) for ONLY the expected stop
        Optional<TripStopEvent> arrivalOpt = tripStopEventRepository.findByTripIdAndStopIdAndEventType(
                currentTrip.getId(), currentTargetStop.getId(), "ARRIVED_AT_STOP");

        if (distance <= arrivalRadius) {
            if (arrivalOpt.isEmpty()) {
                // Mark current required stop as arrived
                TripStopEvent arrivalEvent = TripStopEvent.builder()
                        .trip(currentTrip)
                        .stop(currentTargetStop)
                        .eventType("ARRIVED_AT_STOP")
                        .timestamp(LocalDateTime.now())
                        .build();
                tripStopEventRepository.save(arrivalEvent);

                if (smartNotificationService != null) {
                    smartNotificationService.handleArrivedStop(currentTrip, currentTargetStop);
                }

                // Advance progression cursor atomically to next sequence: K -> K + 1
                int nextSeq = currentRequiredSeq + 1;
                currentTrip.setCurrentStopSequence(nextSeq);
                trip.setCurrentStopSequence(nextSeq);
                tripRepository.save(currentTrip);
            }
        } else {
            // If outside arrival radius, check if arrived already but not departed
            if (arrivalOpt.isPresent()) {
                Optional<TripStopEvent> departureOpt = tripStopEventRepository.findByTripIdAndStopIdAndEventType(
                        currentTrip.getId(), currentTargetStop.getId(), "DEPARTED_STOP");
                if (departureOpt.isEmpty()) {
                    TripStopEvent departureEvent = TripStopEvent.builder()
                            .trip(currentTrip)
                            .stop(currentTargetStop)
                            .eventType("DEPARTED_STOP")
                            .timestamp(LocalDateTime.now())
                            .build();
                    tripStopEventRepository.save(departureEvent);

                    if (smartNotificationService != null) {
                        smartNotificationService.handleDepartedStop(currentTrip, currentTargetStop);
                    }
                }
            }
        }
    }

    public List<RouteStop> getRouteStopsForTrip(Trip trip) {
        if (trip != null && trip.getRouteSnapshot() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(trip.getRouteSnapshot());
                com.fasterxml.jackson.databind.JsonNode stopsNode = root.get("stops");
                if (stopsNode != null && stopsNode.isArray()) {
                    List<RouteStop> list = new ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode sn : stopsNode) {
                        UUID sId = sn.has("stopId") ? UUID.fromString(sn.get("stopId").asText()) : UUID.randomUUID();
                        String sName = sn.has("stopName") ? sn.get("stopName").asText() : "Stop";
                        double lat = sn.has("latitude") ? sn.get("latitude").asDouble() : 0.0;
                        double lng = sn.has("longitude") ? sn.get("longitude").asDouble() : 0.0;
                        int seq = sn.has("sequence") ? sn.get("sequence").asInt() : 1;
                        double dist = sn.has("distanceFromStart") ? sn.get("distanceFromStart").asDouble() : 0.0;
                        int dur = sn.has("durationFromStartMins") ? sn.get("durationFromStartMins").asInt() : 0;
                        String arr = sn.has("arrivalTime") && !sn.get("arrivalTime").isNull() ? sn.get("arrivalTime").asText() : null;
                        String dep = sn.has("departureTime") && !sn.get("departureTime").isNull() ? sn.get("departureTime").asText() : null;

                        Stop s = Stop.builder()
                                .id(sId)
                                .stopName(sName)
                                .latitude(lat)
                                .longitude(lng)
                                .build();

                        RouteStop rs = RouteStop.builder()
                                .route(trip.getRoute())
                                .stop(s)
                                .sequenceNumber(seq)
                                .distanceFromStart(dist)
                                .durationFromStartMins(dur)
                                .expectedArrivalTime(safeParseLocalTime(arr))
                                .expectedDepartureTime(safeParseLocalTime(dep))
                                .build();
                        list.add(rs);
                    }
                    list.sort(Comparator.comparingInt(RouteStop::getSequenceNumber));
                    return list;
                }
            } catch (Exception ignored) {}
        }
        return routeStopRepository.findByRouteOrderBySequenceNumberAsc(trip != null ? trip.getRoute() : null);
    }

    private LocalTime safeParseLocalTime(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        try {
            if (str.contains("T")) {
                return LocalDateTime.parse(str).toLocalTime();
            }
            if (str.length() >= 5) {
                return LocalTime.parse(str.substring(0, 5));
            }
            return LocalTime.parse(str);
        } catch (Exception e) {
            return null;
        }
    }

    public double getGeofenceRadius() {
        return settingRepository.findById("ARRIVAL_RADIUS_METERS")
                .map(s -> {
                    try {
                        return Double.parseDouble(s.getValue());
                    } catch (NumberFormatException e) {
                        return 100.0;
                    }
                }).orElse(100.0);
    }

    public double getApproachingRadius() {
        return settingRepository.findById("APPROACHING_RADIUS_METERS")
                .map(s -> {
                    try {
                        return Double.parseDouble(s.getValue());
                    } catch (NumberFormatException e) {
                        return 500.0;
                    }
                }).orElse(500.0);
    }
}
