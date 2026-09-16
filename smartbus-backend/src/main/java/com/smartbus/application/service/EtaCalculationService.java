package com.smartbus.application.service;

import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.dto.EtaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EtaCalculationService {

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final RouteStopRepository routeStopRepository;
    private final TripLocationRepository tripLocationRepository;
    private final TripStopEventRepository tripStopEventRepository;
    private final SettingRepository settingRepository;
    private final GeofencingService geofencingService;

    // Track consecutive off-route deviations per trip for noise debouncing
    private final Map<UUID, Integer> tripDeviationCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> tripOffRouteState = new ConcurrentHashMap<>();

    /**
     * Calculates ETA and operational status for an active trip.
     */
    public EtaResponse calculateLiveTripEta(UUID tripId) {
        return calculateEta(tripId, null);
    }

    /**
     * Calculates ETA for an active trip toward a target stop (or next stop if targetStopId is null).
     */
    public EtaResponse calculateEta(UUID tripId, UUID targetStopId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));

        return buildEtaResponse(trip, targetStopId);
    }

    /**
     * Calculates ETA for an active bus.
     */
    public EtaResponse calculateBusEta(UUID busId, UUID targetStopId) {
        Optional<Trip> activeTrip = tripRepository.findByBusIdAndStatusIn(busId, Arrays.asList("IN_PROGRESS", "PAUSED"));
        if (activeTrip.isEmpty()) {
            Bus bus = busRepository.findById(busId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bus not found: " + busId));
            return createUnavailableResponse(bus, null, "Bus is not on an active trip");
        }

        return buildEtaResponse(activeTrip.get(), targetStopId);
    }

    private EtaResponse buildEtaResponse(Trip trip, UUID targetStopId) {
        Bus bus = trip.getBus();
        Route route = trip.getRoute();
        List<RouteStop> routeStops = getRouteStopsForTrip(trip);

        if (routeStops.isEmpty()) {
            return createUnavailableResponse(bus, trip, "No stops defined for route");
        }

        Double busLat = null;
        Double busLng = null;
        LocalDateTime lastUpdated = null;

        if (tripLocationRepository != null) {
            Optional<TripLocation> latestLoc = tripLocationRepository.findFirstByTripIdOrderByTimestampDesc(trip.getId());
            if (latestLoc.isPresent() && latestLoc.get().getLatitude() != null && latestLoc.get().getLongitude() != null) {
                busLat = latestLoc.get().getLatitude();
                busLng = latestLoc.get().getLongitude();
                lastUpdated = latestLoc.get().getTimestamp();
            }
        }

        if ((busLat == null || busLng == null) && bus.getCurrentLatitude() != null && bus.getLastUpdated() != null) {
            if (trip.getStartTime() == null || !bus.getLastUpdated().isBefore(trip.getStartTime())) {
                busLat = bus.getCurrentLatitude();
                busLng = bus.getCurrentLongitude();
                lastUpdated = bus.getLastUpdated();
            }
        }

        // 1. Check for missing coordinates
        if (busLat == null || busLng == null || lastUpdated == null) {
            return createUnavailableResponse(bus, trip, "Waiting for reliable GPS coordinates");
        }

        // 2. Check Stale GPS (threshold 60 seconds)
        long staleThresholdSec = getGpsStaleThresholdSeconds();
        long secondsSinceLastUpdate = Math.abs(Duration.between(lastUpdated, LocalDateTime.now()).getSeconds());
        boolean isGpsStale = secondsSinceLastUpdate > staleThresholdSec;
        String gpsStatus = isGpsStale ? "GPS_STALE" : "LIVE";

        // 3. Find visited stops from events (with sequential ordering)
        List<TripStopEvent> events = tripStopEventRepository.findByTripIdOrderByTimestampAsc(trip.getId());
        Set<UUID> arrivedStopIds = new HashSet<>();
        Set<UUID> departedStopIds = new HashSet<>();
        for (TripStopEvent ev : events) {
            if ("ARRIVED_AT_STOP".equalsIgnoreCase(ev.getEventType())) {
                arrivedStopIds.add(ev.getStop().getId());
            } else if ("DEPARTED_STOP".equalsIgnoreCase(ev.getEventType())) {
                departedStopIds.add(ev.getStop().getId());
            }
        }

        // 4. Authoritative Route-Progression State Machine Cursor
        // Invariant: currentRequiredSeq (K) defines the progression state.
        // sequences 1..K-1 are strictly PASSED.
        // sequence K is strictly NEXT (until reached).
        // sequences K+1..N are strictly UPCOMING.
        int currentRequiredSeq = (trip.getCurrentStopSequence() != null && trip.getCurrentStopSequence() >= 1)
                ? trip.getCurrentStopSequence()
                : 1;

        RouteStop currentRouteStop = null;
        RouteStop nextRouteStop = null;

        for (RouteStop rs : routeStops) {
            if (rs.getSequenceNumber() == (currentRequiredSeq - 1)) {
                currentRouteStop = rs;
            }
            if (rs.getSequenceNumber() == currentRequiredSeq) {
                nextRouteStop = rs;
            }
        }

        // If trip completed all stops (currentRequiredSeq > total stops)
        boolean allStopsPassed = (currentRequiredSeq > routeStops.size());
        if (nextRouteStop == null) {
            nextRouteStop = routeStops.get(routeStops.size() - 1);
        }

        // 5. Target Stop Resolution
        RouteStop targetRouteStop = nextRouteStop;
        if (targetStopId != null) {
            for (RouteStop rs : routeStops) {
                if (rs.getStop().getId().equals(targetStopId)) {
                    targetRouteStop = rs;
                    break;
                }
            }
        }

        // 6. Calculate Route Deviation
        double deviationMeters = calculateRouteDeviationDistance(busLat, busLng, routeStops);
        double deviationThreshold = getRouteDeviationThresholdMeters();
        int minConsecutive = getMinConsecutiveDeviations();

        boolean isOffRoute = updateRouteDeviationState(trip.getId(), deviationMeters, deviationThreshold, minConsecutive);

        // 7. Calculate Speed & Travel Time
        double speedKmh = determineEffectiveSpeed(trip.getId(), bus, targetRouteStop);
        if (speedKmh <= 0.0) {
            speedKmh = getDefaultAverageSpeedKmh();
        }

        // Peak hour check
        LocalTime now = LocalTime.now();
        boolean isPeak = (now.isAfter(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(10, 0))) ||
                         (now.isAfter(LocalTime.of(16, 30)) && now.isBefore(LocalTime.of(18, 30)));
        double peakMultiplier = isPeak ? 1.25 : 1.0;

        // Calculate Distance to Next / Target Stop
        double distanceToNextStopMeters = allStopsPassed ? 0.0 : geofencingService.calculateDistance(
                busLat, busLng,
                nextRouteStop.getStop().getLatitude().doubleValue(),
                nextRouteStop.getStop().getLongitude().doubleValue()
        );

        // Calculate direct road travel seconds from CURRENT GPS to NEXT STOP
        double distKmToNext = distanceToNextStopMeters / 1000.0;
        long travelSecToNext = allStopsPassed ? 0 : Math.round((distKmToNext / Math.max(1.0, speedKmh)) * 3600.0 * peakMultiplier);
        travelSecToNext = Math.max(0, Math.min(86400, travelSecToNext));

        // 8. Dynamic Upcoming Stops Calculation from CURRENT DRIVER GPS
        List<EtaResponse.UpcomingStopEta> upcomingStops = new ArrayList<>();
        long cumulativeTravelSeconds = travelSecToNext;
        LocalDateTime runningEtaTime = LocalDateTime.now().plusSeconds(cumulativeTravelSeconds);

        int passedCount = Math.min(routeStops.size(), currentRequiredSeq - 1);
        int nextStopSeqNum = nextRouteStop.getSequenceNumber();

        for (int i = 0; i < routeStops.size(); i++) {
            RouteStop rs = routeStops.get(i);
            int seq = rs.getSequenceNumber();
            boolean isPassed = (seq < currentRequiredSeq);
            boolean isNext = !allStopsPassed && (seq == currentRequiredSeq);

            Integer stopEtaMins = null;
            String stopArrivalIso = null;
            Double stopDistMeters = null;

            if (isPassed) {
                stopEtaMins = 0;
                stopArrivalIso = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                stopDistMeters = 0.0;
            } else if (isNext) {
                stopEtaMins = (int) Math.round(travelSecToNext / 60.0);
                stopArrivalIso = LocalDateTime.now().plusSeconds(travelSecToNext).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                stopDistMeters = distanceToNextStopMeters;
            } else {
                // Inter-stop cumulative addition
                RouteStop prevRs = routeStops.get(i - 1);
                double legDistance = geofencingService.calculateDistance(
                        prevRs.getStop().getLatitude().doubleValue(), prevRs.getStop().getLongitude().doubleValue(),
                        rs.getStop().getLatitude().doubleValue(), rs.getStop().getLongitude().doubleValue()
                );
                double legKm = legDistance / 1000.0;
                long legSec = Math.round((legKm / Math.max(1.0, speedKmh)) * 3600.0 * peakMultiplier);
                legSec += 60; // 1 min dwell time at stop

                cumulativeTravelSeconds += legSec;
                cumulativeTravelSeconds = Math.min(86400, cumulativeTravelSeconds);
                stopEtaMins = (int) Math.round(cumulativeTravelSeconds / 60.0);
                stopArrivalIso = LocalDateTime.now().plusSeconds(cumulativeTravelSeconds).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                stopDistMeters = distanceToNextStopMeters + (legDistance);
            }

            upcomingStops.add(EtaResponse.UpcomingStopEta.builder()
                    .stopId(rs.getStop().getId())
                    .stopName(rs.getStop().getStopName())
                    .sequence(seq)
                    .latitude(rs.getStop().getLatitude().doubleValue())
                    .longitude(rs.getStop().getLongitude().doubleValue())
                    .distanceMeters(stopDistMeters)
                    .etaMinutes(stopEtaMins)
                    .estimatedArrivalTime(stopArrivalIso)
                    .passed(isPassed)
                    .isNext(isNext)
                    .build());
        }

        // Target stop specific metrics
        long targetTravelSeconds = travelSecToNext;
        double targetDistanceMeters = distanceToNextStopMeters;
        if (targetRouteStop.getSequenceNumber() > nextRouteStop.getSequenceNumber()) {
            for (EtaResponse.UpcomingStopEta us : upcomingStops) {
                if (us.getStopId().equals(targetRouteStop.getStop().getId())) {
                    targetTravelSeconds = us.getEtaMinutes() != null ? us.getEtaMinutes() * 60L : travelSecToNext;
                    targetDistanceMeters = us.getDistanceMeters() != null ? us.getDistanceMeters() : distanceToNextStopMeters;
                    break;
                }
            }
        }
        int minutesRemaining = (int) Math.round(targetTravelSeconds / 60.0);
        LocalDateTime estimatedArrivalTime = LocalDateTime.now().plusSeconds(targetTravelSeconds);

        // 9. Delay Detection
        int delayMinutes = 0;
        boolean isDelayed = false;
        if (trip.getSchedule() != null && trip.getSchedule().getDepartureTime() != null) {
            LocalTime scheduledDep = trip.getSchedule().getDepartureTime();
            int scheduledDurationMins = targetRouteStop.getDurationFromStartMins();
            LocalTime expectedStopArrival = scheduledDep.plusMinutes(scheduledDurationMins);

            LocalTime predictedArrival = estimatedArrivalTime.toLocalTime();
            long diffMins = java.time.temporal.ChronoUnit.MINUTES.between(expectedStopArrival, predictedArrival);

            int delayThresholdMins = getDelayThresholdMinutes();
            if (diffMins >= delayThresholdMins) {
                isDelayed = true;
                delayMinutes = (int) diffMins;
            }
        }

        // 10. Status Resolution
        String status = "ON_TIME";
        double confidence = 0.90;

        if (isGpsStale) {
            status = "GPS_STALE";
            confidence = 0.30;
        } else if (isOffRoute) {
            status = "OFF_ROUTE";
            confidence = 0.50;
        } else if (isDelayed) {
            status = "DELAYED";
            confidence = 0.85;
        } else if (distanceToNextStopMeters <= 100.0) {
            status = "ARRIVED";
            confidence = 0.98;
        } else if (distanceToNextStopMeters <= 500.0) {
            status = "ARRIVING";
            confidence = 0.95;
        }

        String trackingSource = trip.getPrimaryTrackingSource();
        if (trackingSource == null) trackingSource = "GPS_DEVICE";

        double progressPercent = routeStops.isEmpty() ? 0.0 : ((double) passedCount / routeStops.size()) * 100.0;
        EtaResponse.RouteProgressInfo progressInfo = EtaResponse.RouteProgressInfo.builder()
                .passedStops(passedCount)
                .totalStops(routeStops.size())
                .nextStopSequence(nextRouteStop.getSequenceNumber())
                .nextStopName(nextRouteStop.getStop().getStopName())
                .progressPercent(progressPercent)
                .build();

        return EtaResponse.builder()
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busCode(bus.getBusCode())
                .tripId(trip.getId())
                .routeId(route.getId())
                .routeName(route.getRouteName())
                .currentStopId(currentRouteStop != null ? currentRouteStop.getStop().getId() : null)
                .currentStopName(currentRouteStop != null ? currentRouteStop.getStop().getStopName() : "In Transit")
                .currentStopSequence(currentRouteStop != null ? currentRouteStop.getSequenceNumber() : null)
                .nextStopId(nextRouteStop.getStop().getId())
                .nextStopName(nextRouteStop.getStop().getStopName())
                .nextStopSequence(nextRouteStop.getSequenceNumber())
                .targetStopId(targetRouteStop.getStop().getId())
                .targetStopName(targetRouteStop.getStop().getStopName())
                .distanceMeters(targetDistanceMeters)
                .estimatedTravelSeconds(targetTravelSeconds)
                .minutesRemaining(minutesRemaining)
                .estimatedArrivalTime(estimatedArrivalTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .status(status)
                .confidence(confidence)
                .lastGpsUpdate(lastUpdated.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .routeDeviationMeters(deviationMeters)
                .offRoute(isOffRoute)
                .gpsStale(isGpsStale)
                .gpsStatus(gpsStatus)
                .trackingSource(trackingSource)
                .scheduledDeparture(trip.getSchedule() != null ? trip.getSchedule().getDepartureTime().toString() : null)
                .delayMinutes(delayMinutes)
                .upcomingStops(upcomingStops)
                .routeProgress(progressInfo)
                .build();
    }

    private double determineEffectiveSpeed(UUID tripId, Bus bus, RouteStop targetRouteStop) {
        // Level 2: calculate recent speed from last 3 GPS points
        List<TripLocation> recent = tripLocationRepository.findTop5ByTripIdOrderByTimestampDesc(tripId);
        if (recent != null && recent.size() >= 2) {
            double sumSpeed = 0.0;
            int count = 0;
            for (TripLocation loc : recent) {
                if (loc.getSpeed() != null && loc.getSpeed() > 5.0 && loc.getSpeed() < 120.0) {
                    sumSpeed += loc.getSpeed();
                    count++;
                }
            }
            if (count > 0) {
                return sumSpeed / count;
            }
        }

        // Level 3: Scheduled segment speed if duration and distance available
        if (targetRouteStop.getDurationFromStartMins() > 0 && targetRouteStop.getDistanceFromStart() > 0) {
            double scheduledSpeed = (targetRouteStop.getDistanceFromStart() / targetRouteStop.getDurationFromStartMins()) * 60.0;
            if (scheduledSpeed >= 10.0 && scheduledSpeed <= 90.0) {
                return scheduledSpeed;
            }
        }

        // Level 4: Fallback
        return getDefaultAverageSpeedKmh();
    }

    /**
     * Computes the minimum distance from the bus coordinates to any segment on the route.
     */
    public double calculateRouteDeviationDistance(double busLat, double busLng, List<RouteStop> routeStops) {
        if (routeStops == null || routeStops.isEmpty()) {
            return 0.0;
        }
        if (routeStops.size() == 1) {
            Stop s = routeStops.get(0).getStop();
            return geofencingService.calculateDistance(busLat, busLng, s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
        }

        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < routeStops.size() - 1; i++) {
            Stop s1 = routeStops.get(i).getStop();
            Stop s2 = routeStops.get(i + 1).getStop();

            double distToSegment = distanceToSegment(
                    busLat, busLng,
                    s1.getLatitude().doubleValue(), s1.getLongitude().doubleValue(),
                    s2.getLatitude().doubleValue(), s2.getLongitude().doubleValue()
            );

            if (distToSegment < minDistance) {
                minDistance = distToSegment;
            }
        }

        return minDistance;
    }

    private double distanceToSegment(double pLat, double pLng, double aLat, double aLng, double bLat, double bLng) {
        double segDist = geofencingService.calculateDistance(aLat, aLng, bLat, bLng);
        if (segDist < 1.0) {
            return geofencingService.calculateDistance(pLat, pLng, aLat, aLng);
        }

        // Project point P onto segment AB using planar projection for short segments
        double x = pLng - aLng;
        double y = pLat - aLat;
        double dx = bLng - aLng;
        double dy = bLat - aLat;

        double t = (x * dx + y * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));

        double projLat = aLat + t * dy;
        double projLng = aLng + t * dx;

        return geofencingService.calculateDistance(pLat, pLng, projLat, projLng);
    }

    private boolean updateRouteDeviationState(UUID tripId, double deviationMeters, double threshold, int minConsecutive) {
        int currentCount = tripDeviationCounts.getOrDefault(tripId, 0);
        boolean previousOffRoute = tripOffRouteState.getOrDefault(tripId, false);

        if (deviationMeters > threshold) {
            currentCount++;
            tripDeviationCounts.put(tripId, currentCount);

            if (currentCount >= minConsecutive) {
                tripOffRouteState.put(tripId, true);
                return true;
            }
            return previousOffRoute;
        } else {
            // Back within threshold
            tripDeviationCounts.put(tripId, 0);
            tripOffRouteState.put(tripId, false);
            return false;
        }
    }

    private EtaResponse createUnavailableResponse(Bus bus, Trip trip, String message) {
        String nextStopName = null;
        Integer nextStopSeq = null;
        UUID nextStopId = null;
        EtaResponse.RouteProgressInfo progressInfo = null;

        if (trip != null && trip.getRoute() != null) {
            int seq = (trip.getCurrentStopSequence() != null && trip.getCurrentStopSequence() >= 1)
                    ? trip.getCurrentStopSequence() : 1;
            List<RouteStop> rStops = getRouteStopsForTrip(trip);
            if (!rStops.isEmpty()) {
                int totalStops = rStops.size();
                int passedCount = Math.min(totalStops, Math.max(0, seq - 1));
                double progressPct = ((double) passedCount / (double) totalStops) * 100.0;
                for (RouteStop rs : rStops) {
                    if (rs.getSequenceNumber() == seq) {
                        nextStopSeq = seq;
                        nextStopName = rs.getStop().getStopName();
                        nextStopId = rs.getStop().getId();
                        break;
                    }
                }
                progressInfo = EtaResponse.RouteProgressInfo.builder()
                        .totalStops(totalStops)
                        .passedStops(passedCount)
                        .nextStopSequence(seq)
                        .progressPercent(Math.round(progressPct * 10.0) / 10.0)
                        .build();
            }
        }

        return EtaResponse.builder()
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busCode(bus.getBusCode())
                .tripId(trip != null ? trip.getId() : null)
                .routeId(trip != null && trip.getRoute() != null ? trip.getRoute().getId() : null)
                .routeName(trip != null && trip.getRoute() != null ? trip.getRoute().getRouteName() : "N/A")
                .status("ETA_UNAVAILABLE")
                .gpsStatus("UNAVAILABLE")
                .nextStopId(nextStopId)
                .nextStopName(nextStopName)
                .nextStopSequence(nextStopSeq)
                .routeProgress(progressInfo)
                .minutesRemaining(-1)
                .estimatedTravelSeconds(-1L)
                .confidence(0.0)
                .offRoute(false)
                .gpsStale(true)
                .lastGpsUpdate(bus.getLastUpdated() != null ? bus.getLastUpdated().toString() : null)
                .trackingSource("LAST_KNOWN")
                .upcomingStops(Collections.emptyList())
                .build();
    }

    public void clearTripState(UUID tripId) {
        tripDeviationCounts.remove(tripId);
        tripOffRouteState.remove(tripId);
    }

    private double getRouteDeviationThresholdMeters() {
        return settingRepository.findById("ROUTE_DEVIATION_THRESHOLD_METERS")
                .map(s -> {
                    try { return Double.parseDouble(s.getValue()); }
                    catch (Exception e) { return 500.0; }
                }).orElse(500.0);
    }

    private int getDelayThresholdMinutes() {
        return settingRepository.findById("ETA_DELAY_THRESHOLD_MINUTES")
                .map(s -> {
                    try { return Integer.parseInt(s.getValue()); }
                    catch (Exception e) { return 5; }
                }).orElse(5);
    }

    private long getGpsStaleThresholdSeconds() {
        return settingRepository.findById("GPS_STALE_THRESHOLD_SECONDS")
                .map(s -> {
                    try { return Long.parseLong(s.getValue()); }
                    catch (Exception e) { return 60L; }
                }).orElse(60L);
    }

    private double getDefaultAverageSpeedKmh() {
        return settingRepository.findById("DEFAULT_AVERAGE_SPEED_KMH")
                .map(s -> {
                    try { return Double.parseDouble(s.getValue()); }
                    catch (Exception e) { return 30.0; }
                }).orElse(30.0);
    }

    private int getMinConsecutiveDeviations() {
        return settingRepository.findById("MIN_CONSECUTIVE_DEVIATIONS_FOR_ALERT")
                .map(s -> {
                    try { return Integer.parseInt(s.getValue()); }
                    catch (Exception e) { return 3; }
                }).orElse(3);
    }

    private List<RouteStop> getRouteStopsForTrip(Trip trip) {
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
                                .expectedArrivalTime(arr != null && arr.length() >= 5 ? java.time.LocalTime.parse(arr.substring(0, 5)) : null)
                                .expectedDepartureTime(dep != null && dep.length() >= 5 ? java.time.LocalTime.parse(dep.substring(0, 5)) : null)
                                .build();
                        list.add(rs);
                    }
                    list.sort(Comparator.comparingInt(RouteStop::getSequenceNumber));
                    return list;
                }
            } catch (Exception ignored) {}
        }
        return routeStopRepository.findByRouteOrderBySequenceNumberAsc(trip.getRoute());
    }
}
