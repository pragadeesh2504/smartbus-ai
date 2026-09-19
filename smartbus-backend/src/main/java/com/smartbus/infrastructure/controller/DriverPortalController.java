package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.*;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.security.UserPrincipal;
import com.smartbus.websocket.LiveLocationWebSocketHandler;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/driver")
@RequiredArgsConstructor
public class DriverPortalController {

    private final DriverRepository driverRepository;
    private final BusRepository busRepository;
    private final BusQrTokenRepository busQrTokenRepository;
    private final BusAssignmentRepository busAssignmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final TripRepository tripRepository;
    private final TripLocationRepository tripLocationRepository;
    private final TripStopEventRepository tripStopEventRepository;
    private final BreakdownReportRepository breakdownReportRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final RouteStopRepository routeStopRepository;
    private final DriverNotificationRepository driverNotificationRepository;
    private final EmergencyLogRepository emergencyLogRepository;
    private final AuditLogService auditLogService;
    private final GeofencingService geofencingService;
    private final HybridTrackingService hybridTrackingService;
    private final LiveLocationWebSocketHandler webSocketHandler;
    private final EtaCalculationService etaCalculationService;

    @Autowired(required = false)
    private DemoGpsSimulator demoGpsSimulator;

    @Autowired(required = false)
    private com.smartbus.application.service.DemoGpsDeviceAdapter demoGpsDeviceAdapter;

    @Autowired(required = false)
    private com.smartbus.security.ratelimit.RateLimitService rateLimitService;

    private final SmartNotificationService smartNotificationService;

    private Driver getAuthenticatedDriver() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Driver driver = driverRepository.findByUser(principal.getUser())
                .orElseThrow(() -> new BadRequestException("DRIVER_NOT_APPROVED"));

        if (!driver.getUser().isActive() || !"APPROVED".equalsIgnoreCase(driver.getApprovalStatus())) {
            throw new BadRequestException("DRIVER_NOT_APPROVED");
        }
        if ("SUSPENDED".equalsIgnoreCase(driver.getStatus())) {
            throw new BadRequestException("DRIVER_NOT_APPROVED");
        }
        return driver;
    }

    private LocalDateTime parseTimestamp(String ts) {
        if (ts == null || ts.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return java.time.Instant.parse(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e1) {
            try {
                return LocalDateTime.parse(ts);
            } catch (Exception e2) {
                return LocalDateTime.now();
            }
        }
    }

    @PostMapping("/qr/verify")
    public ResponseEntity<QrVerifyResponse> verifyQr(@RequestBody QrVerifyRequest dto) {
        Driver driver = getAuthenticatedDriver();

        String qrContent = dto.getQrContent();
        if (qrContent == null || !qrContent.startsWith("smartbus://bus/")) {
            throw new BadRequestException("QR_INVALID");
        }

        String[] parts = qrContent.replace("smartbus://bus/", "").split("/");
        if (parts.length < 2) {
            throw new BadRequestException("QR_INVALID");
        }

        String busCode = parts[0];
        String secureToken = parts[1];

        Bus bus = busRepository.findByBusCodeAndDeletedAtIsNull(busCode)
                .orElseThrow(() -> new BadRequestException("QR_INVALID"));

        if (!"ACTIVE".equalsIgnoreCase(bus.getStatus())) {
            throw new BadRequestException("BUS_INACTIVE");
        }

        BusQrToken qrToken = busQrTokenRepository.findByTokenAndIsActiveTrue(secureToken)
                .orElseThrow(() -> new BadRequestException("QR_REVOKED"));

        if (!qrToken.getBus().getId().equals(bus.getId())) {
            throw new BadRequestException("QR_INVALID");
        }

        List<BusAssignment> assignments = busAssignmentRepository.findByDriverIdAndStatus(driver.getId(), "ACTIVE");
        BusAssignment todayAssignment = assignments.stream()
                .filter(a -> a.getBus().getId().equals(bus.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("DRIVER_NOT_ASSIGNED"));

        Schedule schedule = todayAssignment.getSchedule();
        if (schedule == null) {
            throw new BadRequestException("NO_ACTIVE_SCHEDULE");
        }
        if (!"ACTIVE".equalsIgnoreCase(schedule.getStatus())) {
            throw new BadRequestException("SCHEDULE_NOT_ACTIVE");
        }

        Optional<Trip> activeBusTrip = tripRepository.findByBusIdAndStatusIn(bus.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"));
        if (activeBusTrip.isPresent()) {
            throw new BadRequestException("BUS_ALREADY_IN_TRIP");
        }

        // Save QR_VERIFIED audit log
        auditLogService.logAction(
                driver.getUser(),
                "QR_VERIFIED",
                "QR code verified successfully for Bus Code: " + busCode,
                "127.0.0.1",
                "Bus",
                bus.getId().toString(),
                null,
                "QR code verified successfully for Bus Code: " + busCode
        );

        QrVerifyResponse resp = QrVerifyResponse.builder()
                .success(true)
                .bus(QrVerifyResponse.BusInfo.builder()
                        .busNumber(bus.getBusNumber())
                        .busCode(bus.getBusCode())
                        .capacity(bus.getCapacity())
                        .build())
                .driver(QrVerifyResponse.DriverInfo.builder()
                        .name(driver.getUser().getFirstName() + " " + driver.getUser().getLastName())
                        .build())
                .schedule(QrVerifyResponse.ScheduleInfo.builder()
                        .routeName(todayAssignment.getRoute().getRouteName())
                        .departureTime(schedule.getDepartureTime().toString())
                        .scheduleId(schedule.getId())
                        .build())
                .message("Bus verified successfully")
                .build();

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DriverDashboardResponse> getDashboard() {
        Driver driver = getAuthenticatedDriver();
        List<BusAssignment> assignments = busAssignmentRepository.findByDriverIdAndStatus(driver.getId(), "ACTIVE");
        
        DriverDashboardResponse.DriverDashboardResponseBuilder builder = DriverDashboardResponse.builder()
                .driverName(driver.getUser().getFirstName() + " " + driver.getUser().getLastName())
                .driverStatus(driver.getStatus());

        if (!assignments.isEmpty()) {
            BusAssignment current = assignments.get(0);
            builder.busId(current.getBus().getId())
                   .busNumber(current.getBus().getBusNumber())
                   .busCode(current.getBus().getBusCode())
                   .routeId(current.getRoute().getId())
                   .routeName(current.getRoute().getRouteName())
                   .startPoint(current.getRoute().getStartPoint())
                   .endPoint(current.getRoute().getEndPoint())
                   .distance(current.getRoute().getDistance())
                   .departureTime(current.getSchedule().getDepartureTime().toString())
                   .scheduleId(current.getSchedule().getId());

            List<RouteStop> rStops = routeStopRepository.findByRouteOrderBySequenceNumberAsc(current.getRoute());
            builder.totalStops(rStops.size());

            GpsDevice dev = current.getBus().getGpsDevice();
            builder.gpsDeviceOnline(dev != null && "ACTIVE".equalsIgnoreCase(dev.getStatus()));
            builder.phoneGpsReady(true);
        }

        Optional<Trip> activeTrip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"));
        if (activeTrip.isPresent()) {
            builder.activeTripId(activeTrip.get().getId());
            builder.activeTripStatus(activeTrip.get().getStatus());
        }

        return ResponseEntity.ok(builder.build());
    }

    @GetMapping("/assignments/today")
    public ResponseEntity<List<AssignmentSummary>> getAssignmentsToday() {
        Driver driver = getAuthenticatedDriver();
        List<BusAssignment> assignments = busAssignmentRepository.findByDriverIdAndStatus(driver.getId(), "ACTIVE");
        List<AssignmentSummary> summaries = assignments.stream().map(a -> AssignmentSummary.builder()
                .assignmentId(a.getId())
                .busNumber(a.getBus().getBusNumber())
                .busCode(a.getBus().getBusCode())
                .routeName(a.getRoute().getRouteName())
                .departureTime(a.getSchedule().getDepartureTime().toString())
                .scheduleId(a.getSchedule().getId())
                .build()).collect(Collectors.toList());
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/schedules")
    public ResponseEntity<List<DriverScheduleItem>> getDriverSchedules() {
        Driver driver = getAuthenticatedDriver();

        List<Schedule> schedules = scheduleRepository.findByDriverIdAndDeletedAtIsNull(driver.getId());
        List<BusAssignment> assignments = busAssignmentRepository.findByDriverIdAndStatus(driver.getId(), "ACTIVE");
        Map<UUID, Schedule> schedMap = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            Optional<BusAssignment> assignOpt = busAssignmentRepository.findFirstByScheduleId(s.getId());
            if (assignOpt.isPresent()) {
                BusAssignment a = assignOpt.get();
                if (a.getDriver() != null && a.getDriver().getId().equals(driver.getId()) && "ACTIVE".equalsIgnoreCase(a.getStatus())) {
                    schedMap.put(s.getId(), s);
                }
            } else {
                schedMap.put(s.getId(), s);
            }
        }
        for (BusAssignment a : assignments) {
            if (a.getSchedule() != null && a.getSchedule().getDeletedAt() == null) {
                schedMap.put(a.getSchedule().getId(), a.getSchedule());
            }
        }

        Optional<Trip> activeTripOpt = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"));
        Trip activeTrip = activeTripOpt.orElse(null);

        List<DriverScheduleItem> result = new ArrayList<>();
        for (Schedule s : schedMap.values()) {
            if (s.getBus() == null || s.getRoute() == null) continue;
            if (driver.getCollege() != null && s.getCollege() != null && !driver.getCollege().getId().equals(s.getCollege().getId())) {
                continue;
            }

            String tripStatus = "UPCOMING";
            UUID activeTripId = null;

            if (activeTrip != null && activeTrip.getSchedule() != null && activeTrip.getSchedule().getId().equals(s.getId())) {
                tripStatus = activeTrip.getStatus();
                activeTripId = activeTrip.getId();
            } else {
                List<Trip> scheduleTrips = tripRepository.findByScheduleId(s.getId());
                if (!scheduleTrips.isEmpty()) {
                    String lastStatus = scheduleTrips.get(0).getStatus();
                    if ("COMPLETED".equalsIgnoreCase(lastStatus)) {
                        tripStatus = "COMPLETED";
                    }
                }
            }

            List<RouteStop> routeStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(s.getRoute().getId());
            List<RouteProgressResponse.StopInfo> stops = routeStops.stream().map(rs -> RouteProgressResponse.StopInfo.builder()
                    .stopId(rs.getStop().getId())
                    .stopName(rs.getStop().getStopName())
                    .latitude(rs.getStop().getLatitude().doubleValue())
                    .longitude(rs.getStop().getLongitude().doubleValue())
                    .sequence(rs.getSequenceNumber())
                    .arrivalTime(rs.getExpectedArrivalTime() != null ? rs.getExpectedArrivalTime().toString() : null)
                    .departureTime(rs.getExpectedDepartureTime() != null ? rs.getExpectedDepartureTime().toString() : null)
                    .build()).collect(Collectors.toList());

            result.add(DriverScheduleItem.builder()
                    .scheduleId(s.getId())
                    .busId(s.getBus().getId())
                    .busNumber(s.getBus().getBusNumber())
                    .busCode(s.getBus().getBusCode())
                    .routeId(s.getRoute().getId())
                    .routeName(s.getRoute().getRouteName())
                    .startPoint(s.getRoute().getStartPoint())
                    .endPoint(s.getRoute().getEndPoint())
                    .departureTime(s.getDepartureTime() != null ? s.getDepartureTime().toString() : "")
                    .arrivalTime(s.getArrivalTime() != null ? s.getArrivalTime().toString() : "")
                    .daysOfWeek(s.getDaysOfWeek())
                    .scheduleStatus(s.getStatus())
                    .tripStatus(tripStatus)
                    .activeTripId(activeTripId)
                    .stops(stops)
                    .build());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<AssignmentDetails> getAssignmentDetails(@PathVariable UUID id) {
        Driver driver = getAuthenticatedDriver();
        BusAssignment assignment = busAssignmentRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Assignment not found"));
        
        // Enforce owner check: must belong to authenticated driver
        if (!assignment.getDriver().getId().equals(driver.getId())) {
            throw new BadRequestException("UNAUTHORIZED_ACCESS");
        }

        // Fetch stops list
        List<RouteStop> routeStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(assignment.getRoute().getId());
        List<RouteProgressResponse.StopInfo> stops = routeStops.stream().map(rs -> RouteProgressResponse.StopInfo.builder()
                .stopId(rs.getStop().getId())
                .stopName(rs.getStop().getStopName())
                .latitude(rs.getStop().getLatitude().doubleValue())
                .longitude(rs.getStop().getLongitude().doubleValue())
                .sequence(rs.getSequenceNumber())
                .arrivalTime(rs.getExpectedArrivalTime() != null ? rs.getExpectedArrivalTime().toString() : null)
                .departureTime(rs.getExpectedDepartureTime() != null ? rs.getExpectedDepartureTime().toString() : null)
                .build()).collect(Collectors.toList());

        boolean gpsOnline = assignment.getBus().getGpsDevice() != null && "ONLINE".equalsIgnoreCase(assignment.getBus().getGpsDevice().getStatus());

        // Find if there is an active trip on this schedule for today
        // (Default to SCHEDULED if no active trip or COMPLETED if a trip is found)
        Optional<Trip> activeTrip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"));
        String tripStatus = "SCHEDULED";
        if (activeTrip.isPresent() && activeTrip.get().getSchedule().getId().equals(assignment.getSchedule().getId())) {
            tripStatus = activeTrip.get().getStatus();
        } else {
            // Check if there was a trip completed today on this schedule
            // For simplicity, search by schedule id
            List<Trip> scheduleTrips = tripRepository.findByScheduleId(assignment.getSchedule().getId());
            if (!scheduleTrips.isEmpty()) {
                tripStatus = scheduleTrips.get(0).getStatus();
            }
        }

        AssignmentDetails details = AssignmentDetails.builder()
                .assignmentId(assignment.getId())
                .busNumber(assignment.getBus().getBusNumber())
                .busCode(assignment.getBus().getBusCode())
                .routeName(assignment.getRoute().getRouteName())
                .stops(stops)
                .scheduleId(assignment.getSchedule().getId())
                .departureTime(assignment.getSchedule().getDepartureTime().toString())
                .expectedArrivalTime(assignment.getSchedule().getArrivalTime().toString())
                .daysOfWeek(assignment.getSchedule().getDaysOfWeek())
                .status(assignment.getStatus())
                .gpsDeviceOnline(gpsOnline)
                .tripStatus(tripStatus)
                .build();
        
        return ResponseEntity.ok(details);
    }

    @PostMapping("/trips/{scheduleId}/start")
    public ResponseEntity<TripResponse> startTrip(@PathVariable UUID scheduleId) {
        Driver driver = getAuthenticatedDriver();

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BadRequestException("Schedule not found"));

        if (schedule.getDriver() != null && !schedule.getDriver().getId().equals(driver.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Schedule does not belong to authenticated driver");
        }

        if (driver.getCollege() != null && schedule.getCollege() != null && !driver.getCollege().getId().equals(schedule.getCollege().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Schedule does not belong to driver's college");
        }

        List<BusAssignment> assignments = busAssignmentRepository.findByDriverIdAndStatus(driver.getId(), "ACTIVE");
        BusAssignment assignment = assignments.stream()
                .filter(a -> a.getSchedule().getId().equals(scheduleId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("DRIVER_NOT_ASSIGNED"));

        if (!"ACTIVE".equalsIgnoreCase(assignment.getBus().getStatus())) {
            throw new BadRequestException("BUS_INACTIVE");
        }
        if (!"ACTIVE".equalsIgnoreCase(assignment.getRoute().getStatus())) {
            throw new BadRequestException("ROUTE_INACTIVE");
        }
        if (!"ACTIVE".equalsIgnoreCase(schedule.getStatus())) {
            throw new BadRequestException("SCHEDULE_NOT_ACTIVE");
        }

        Optional<Trip> activeBusTrip = tripRepository.findByBusIdAndStatusIn(assignment.getBus().getId(), Arrays.asList("IN_PROGRESS", "PAUSED"));
        if (activeBusTrip.isPresent()) {
            throw new BadRequestException("BUS_ALREADY_IN_TRIP");
        }

        Optional<Trip> activeDriverTrip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"));
        if (activeDriverTrip.isPresent()) {
            throw new BadRequestException("DRIVER_ALREADY_IN_TRIP");
        }

        // Clear old stale bus coordinates on new trip initialization
        Bus bus = assignment.getBus();
        bus.setCurrentLatitude(null);
        bus.setCurrentLongitude(null);
        bus.setLastUpdated(null);
        bus = busRepository.save(bus);
        // Build route snapshot JSON for active trip safety (Requirement 6)
        Route activeRoute = assignment.getRoute();
        List<RouteStop> activeRouteStops = routeStopRepository.findByRouteOrderBySequenceNumberAsc(activeRoute);
        String snapshotJson = buildRouteSnapshot(activeRoute, activeRouteStops);

        // Initialize Trip without fake start coordinates
        Trip trip = Trip.builder()
                .schedule(schedule)
                .bus(bus)
                .driver(driver)
                .route(assignment.getRoute())
                .college(driver.getCollege() != null ? driver.getCollege() : schedule.getCollege())
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now())
                .actualDeparture(LocalDateTime.now())
                .startLatitude(null)
                .startLongitude(null)
                .routeSnapshot(snapshotJson)
                .currentStopSequence(1)
                .build();

        trip = tripRepository.save(trip);

        // Reset simulation cache if in local dev profile
        if (demoGpsSimulator != null) {
            demoGpsSimulator.resetSimulation(trip.getId());
        }

        // Audit Log
        auditLogService.logAction(
                driver.getUser(),
                "TRIP_STARTED",
                "Trip started successfully for Route: " + assignment.getRoute().getRouteName(),
                "127.0.0.1",
                "Trip",
                trip.getId().toString(),
                null,
                "Trip started successfully for Route: " + assignment.getRoute().getRouteName()
        );

        // Broadcast trip status via WebSocket
        Bus tripBus = trip.getBus() != null ? trip.getBus() : assignment.getBus();
        Route tripRoute = trip.getRoute() != null ? trip.getRoute() : assignment.getRoute();
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "TRIP_STATUS_UPDATE");
        alert.put("tripId", trip.getId() != null ? trip.getId().toString() : "");
        alert.put("busId", tripBus != null && tripBus.getId() != null ? tripBus.getId().toString() : "");
        alert.put("busCode", tripBus != null ? tripBus.getBusCode() : "");
        alert.put("busNumber", tripBus != null ? tripBus.getBusNumber() : "");
        alert.put("routeId", tripRoute != null && tripRoute.getId() != null ? tripRoute.getId().toString() : "");
        alert.put("routeName", tripRoute != null ? tripRoute.getRouteName() : "");
        alert.put("driverId", driver.getId() != null ? driver.getId().toString() : "");
        alert.put("driverName", driver.getUser() != null ? driver.getUser().getFirstName() + " " + driver.getUser().getLastName() : "Driver");
        alert.put("status", "IN_PROGRESS");
        alert.put("latitude", null);
        alert.put("longitude", null);
        if (webSocketHandler != null) {
            webSocketHandler.broadcast(alert);
        }

        if (smartNotificationService != null) {
            try {
                smartNotificationService.handleTripStarted(trip);
            } catch (Exception e) {
                // Non-blocking notification dispatch
            }
        }

        return ResponseEntity.ok(mapToTripResponse(trip));
    }

    @PostMapping("/trips/{tripId}/pause")
    public ResponseEntity<TripResponse> pauseTrip(@PathVariable UUID tripId, @RequestBody PauseRequest dto) {
        Driver driver = getAuthenticatedDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BadRequestException("Trip not found"));

        if (!trip.getDriver().getId().equals(driver.getId())) {
            throw new BadRequestException("Unauthorized access to trip");
        }

        if (!"IN_PROGRESS".equals(trip.getStatus())) {
            throw new BadRequestException("Only active trips can be paused");
        }

        trip.setStatus("PAUSED");
        trip = tripRepository.save(trip);

        // Audit Log
        auditLogService.logAction(
                driver.getUser(),
                "TRIP_PAUSED",
                "Trip paused. Reason: " + dto.getReason(),
                "127.0.0.1",
                "Trip",
                trip.getId().toString(),
                null,
                "Trip paused. Reason: " + dto.getReason()
        );

        // Broadcast to clients
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "TRIP_STATUS_UPDATE");
        alert.put("tripId", trip.getId().toString());
        alert.put("busId", trip.getBus().getId().toString());
        alert.put("busCode", trip.getBus().getBusCode());
        alert.put("busNumber", trip.getBus().getBusNumber());
        alert.put("status", "PAUSED");
        alert.put("reason", dto.getReason());
        alert.put("latitude", trip.getBus().getCurrentLatitude());
        alert.put("longitude", trip.getBus().getCurrentLongitude());
        webSocketHandler.broadcast(alert);

        return ResponseEntity.ok(mapToTripResponse(trip));
    }

    @PostMapping("/trips/{tripId}/resume")
    public ResponseEntity<TripResponse> resumeTrip(@PathVariable UUID tripId) {
        Driver driver = getAuthenticatedDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BadRequestException("Trip not found"));

        if (!trip.getDriver().getId().equals(driver.getId())) {
            throw new BadRequestException("Unauthorized access to trip");
        }

        if (!"PAUSED".equals(trip.getStatus())) {
            throw new BadRequestException("Only paused trips can be resumed");
        }

        trip.setStatus("IN_PROGRESS");
        trip = tripRepository.save(trip);

        // Audit Log
        auditLogService.logAction(
                driver.getUser(),
                "TRIP_RESUMED",
                "Trip resumed successfully",
                "127.0.0.1",
                "Trip",
                trip.getId().toString(),
                null,
                "Trip resumed successfully"
        );

        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "TRIP_STATUS_UPDATE");
        alert.put("tripId", trip.getId().toString());
        alert.put("busId", trip.getBus().getId().toString());
        alert.put("busCode", trip.getBus().getBusCode());
        alert.put("busNumber", trip.getBus().getBusNumber());
        alert.put("status", "IN_PROGRESS");
        alert.put("latitude", trip.getBus().getCurrentLatitude());
        alert.put("longitude", trip.getBus().getCurrentLongitude());
        webSocketHandler.broadcast(alert);

        return ResponseEntity.ok(mapToTripResponse(trip));
    }

    @PostMapping("/trips/{tripId}/end")
    public ResponseEntity<TripResponse> endTrip(@PathVariable UUID tripId) {
        Driver driver = getAuthenticatedDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BadRequestException("Trip not found"));

        if (!trip.getDriver().getId().equals(driver.getId())) {
            throw new BadRequestException("Unauthorized access to trip");
        }

        if (!"IN_PROGRESS".equals(trip.getStatus()) && !"PAUSED".equals(trip.getStatus())) {
            throw new BadRequestException("Trip has already ended or is invalid");
        }

        LocalDateTime now = LocalDateTime.now();
        trip.setStatus("COMPLETED");
        trip.setEndTime(now);
        trip.setActualArrival(now);

        // Calculate final Duration (in minutes)
        long durationSec = java.time.Duration.between(trip.getStartTime(), now).toSeconds();
        trip.setDuration((int) (durationSec / 60));

        // Get coordinates list to compute path distance
        List<TripLocation> locations = tripLocationRepository.findByTripIdOrderByTimestampAsc(tripId);
        double totalDistanceMeters = 0.0;
        if (locations.size() > 1) {
            trip.setStartLatitude(locations.get(0).getLatitude());
            trip.setStartLongitude(locations.get(0).getLongitude());
            trip.setEndLatitude(locations.get(locations.size() - 1).getLatitude());
            trip.setEndLongitude(locations.get(locations.size() - 1).getLongitude());

            for (int i = 0; i < locations.size() - 1; i++) {
                TripLocation l1 = locations.get(i);
                TripLocation l2 = locations.get(i + 1);
                totalDistanceMeters += geofencingService.calculateDistance(
                        l1.getLatitude(), l1.getLongitude(),
                        l2.getLatitude(), l2.getLongitude()
                );
            }
        } else {
            trip.setEndLatitude(trip.getBus().getCurrentLatitude());
            trip.setEndLongitude(trip.getBus().getCurrentLongitude());
        }

        trip.setDistance(totalDistanceMeters / 1000.0); // Store in Kilometers
        trip = tripRepository.save(trip);

        // Clear tracking cache
        hybridTrackingService.clearTripCache(tripId);

        // Audit Log
        auditLogService.logAction(
                driver.getUser(),
                "TRIP_COMPLETED",
                String.format("Trip completed successfully. Duration: %d mins, Distance: %.2f km", trip.getDuration(), trip.getDistance()),
                "127.0.0.1",
                "Trip",
                trip.getId().toString(),
                null,
                String.format("Trip completed successfully. Duration: %d mins, Distance: %.2f km", trip.getDuration(), trip.getDistance())
        );

        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "TRIP_STATUS_UPDATE");
        alert.put("tripId", trip.getId().toString());
        alert.put("busId", trip.getBus().getId().toString());
        alert.put("busCode", trip.getBus().getBusCode());
        alert.put("busNumber", trip.getBus().getBusNumber());
        alert.put("status", "COMPLETED");
        webSocketHandler.broadcast(alert);

        return ResponseEntity.ok(mapToTripResponse(trip));
    }

    @PostMapping("/trip/pause")
    public ResponseEntity<TripResponse> pauseActiveTrip(@RequestBody(required = false) PauseRequest dto) {
        Driver driver = getAuthenticatedDriver();
        Trip trip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Collections.singletonList("IN_PROGRESS"))
                .orElseThrow(() -> new BadRequestException("No active trip found to pause"));
        return pauseTrip(trip.getId(), dto != null ? dto : new PauseRequest("Driver pause", null, null));
    }

    @PostMapping("/trip/resume")
    public ResponseEntity<TripResponse> resumeActiveTrip() {
        Driver driver = getAuthenticatedDriver();
        Trip trip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Collections.singletonList("PAUSED"))
                .orElseThrow(() -> new BadRequestException("No paused trip found to resume"));
        return resumeTrip(trip.getId());
    }

    @PostMapping("/trip/end")
    public ResponseEntity<TripResponse> endActiveTrip() {
        Driver driver = getAuthenticatedDriver();
        Trip trip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"))
                .orElseThrow(() -> new BadRequestException("No active trip found to end"));
        return endTrip(trip.getId());
    }

    @PostMapping("/location")
    public ResponseEntity<Map<String, Boolean>> pushLocation(@RequestBody LocationUpdateRequest dto) {
        Driver driver = getAuthenticatedDriver();
        if (dto == null) {
            throw new BadRequestException("Request body cannot be null");
        }
        // SEC-08: Validate controller input ranges
        GpsValidationUtil.validateTelemetry(dto.getLatitude(), dto.getLongitude(), dto.getSpeed(), dto.getHeading());
        GpsValidationUtil.validateAccuracy(dto.getAccuracy());

        Trip trip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"))
                .orElseThrow(() -> new BadRequestException("No active trip"));

        // SEC-11: Rate limit driver location submissions
        if (rateLimitService != null) {
            String rateLimitKey = driver.getId() + ":" + trip.getId();
            com.smartbus.security.ratelimit.RateLimitResult limit = rateLimitService.checkGpsLimit(rateLimitKey);
            if (!limit.isAllowed()) {
                throw new com.smartbus.domain.exception.RateLimitExceededException(
                        "GPS telemetry rate limit exceeded. Please try again later.",
                        limit.getRetryAfterSeconds());
            }
        }

        Double heading = dto.getHeading() != null ? dto.getHeading() : 0.0;
        Double speed = dto.getSpeed() != null ? dto.getSpeed() : 0.0;
        Double accuracy = dto.getAccuracy() != null ? dto.getAccuracy() : 5.0;

        if (trip.getBus() != null && trip.getBus().getGpsDevice() != null && demoGpsDeviceAdapter != null) {
            demoGpsDeviceAdapter.setDeviceOnline(trip.getBus().getGpsDevice().getId(), false);
        }

        LocationData data = LocationData.builder()
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .speed(speed)
                .heading(heading)
                .accuracy(accuracy)
                .timestamp(parseTimestamp(dto.getTimestamp()))
                .build();

        hybridTrackingService.processLocationUpdate(trip.getId(), data, dto.getSourceEventId());
        return ResponseEntity.ok(Collections.singletonMap("success", true));
    }

    @PostMapping("/location/batch")
    public ResponseEntity<Map<String, Boolean>> pushLocationBatch(@RequestBody List<LocationUpdateRequest> dtoList) {
        Driver driver = getAuthenticatedDriver();
        if (dtoList == null || dtoList.isEmpty()) {
            throw new BadRequestException("Location batch cannot be empty");
        }
        // SEC-08: Validate all batch items
        for (LocationUpdateRequest dto : dtoList) {
            if (dto == null) {
                throw new BadRequestException("Location item cannot be null");
            }
            GpsValidationUtil.validateTelemetry(dto.getLatitude(), dto.getLongitude(), dto.getSpeed(), dto.getHeading());
            GpsValidationUtil.validateAccuracy(dto.getAccuracy());
        }

        Trip trip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"))
                .orElseThrow(() -> new BadRequestException("No active trip"));

        if (trip.getBus() != null && trip.getBus().getGpsDevice() != null && demoGpsDeviceAdapter != null) {
            demoGpsDeviceAdapter.setDeviceOnline(trip.getBus().getGpsDevice().getId(), false);
        }

        // SEC-11: Rate limit driver location submissions
        if (rateLimitService != null) {
            String rateLimitKey = driver.getId() + ":" + trip.getId();
            com.smartbus.security.ratelimit.RateLimitResult limit = rateLimitService.checkGpsLimit(rateLimitKey);
            if (!limit.isAllowed()) {
                throw new com.smartbus.domain.exception.RateLimitExceededException(
                        "GPS telemetry rate limit exceeded. Please try again later.",
                        limit.getRetryAfterSeconds());
            }
        }

        // Sort chronologically just in case
        List<LocationUpdateRequest> sorted = dtoList.stream()
                .filter(d -> d.getTimestamp() != null)
                .sorted(Comparator.comparing(LocationUpdateRequest::getTimestamp))
                .collect(Collectors.toList());

        for (LocationUpdateRequest dto : sorted) {
            Double heading = dto.getHeading() != null ? dto.getHeading() : 0.0;
            Double speed = dto.getSpeed() != null ? dto.getSpeed() : 0.0;
            Double accuracy = dto.getAccuracy() != null ? dto.getAccuracy() : 5.0;

            LocationData data = LocationData.builder()
                    .latitude(dto.getLatitude())
                    .longitude(dto.getLongitude())
                    .speed(speed)
                    .heading(heading)
                    .accuracy(accuracy)
                    .timestamp(parseTimestamp(dto.getTimestamp()))
                    .build();

            hybridTrackingService.processLocationUpdate(trip.getId(), data, dto.getSourceEventId());
        }

        return ResponseEntity.ok(Collections.singletonMap("success", true));
    }

    @GetMapping("/route")
    public ResponseEntity<RouteProgressResponse> getRouteProgress() {
        Driver driver = getAuthenticatedDriver();
        Trip trip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"))
                .orElseThrow(() -> new BadRequestException("No active trip"));

        List<RouteStop> routeStops;
        if (trip.getRouteSnapshot() != null) {
            routeStops = parseRouteStopsFromSnapshot(trip.getRoute(), trip.getRouteSnapshot());
        } else {
            routeStops = routeStopRepository.findByRouteOrderBySequenceNumberAsc(trip.getRoute());
        }
        List<TripStopEvent> events = tripStopEventRepository.findByTripIdOrderByTimestampAsc(trip.getId());

        Set<UUID> visitedStopIds = new HashSet<>();
        Map<UUID, String> arrivals = new HashMap<>();
        Map<UUID, String> departures = new HashMap<>();

        for (TripStopEvent event : events) {
            if ("ARRIVED_AT_STOP".equalsIgnoreCase(event.getEventType())) {
                visitedStopIds.add(event.getStop().getId());
                arrivals.put(event.getStop().getId(), event.getTimestamp().toString());
            } else if ("DEPARTED_STOP".equalsIgnoreCase(event.getEventType())) {
                departures.put(event.getStop().getId(), event.getTimestamp().toString());
            }
        }

        List<RouteProgressResponse.StopInfo> visited = new ArrayList<>();
        List<RouteProgressResponse.StopInfo> remaining = new ArrayList<>();

        RouteProgressResponse.StopInfo currentStop = null;
        RouteProgressResponse.StopInfo nextStop = null;

        com.smartbus.infrastructure.dto.EtaResponse eta = null;
        try {
            eta = etaCalculationService.calculateLiveTripEta(trip.getId());
        } catch (Exception ignored) {}

        int nextStopSeq = (trip.getCurrentStopSequence() != null && trip.getCurrentStopSequence() >= 1)
                ? trip.getCurrentStopSequence()
                : ((eta != null && eta.getNextStopSequence() != null) ? eta.getNextStopSequence() : 1);

        for (RouteStop rs : routeStops) {
            Stop stop = rs.getStop();
            int seq = rs.getSequenceNumber();
            // INVARIANT: Only stops strictly before the cursor (seq < nextStopSeq) are PASSED.
            // A stop can never be PASSED if an earlier sequence is not passed.
            boolean isPassed = (seq < nextStopSeq);

            RouteProgressResponse.StopInfo sInfo = RouteProgressResponse.StopInfo.builder()
                    .stopId(stop.getId())
                    .stopName(stop.getStopName())
                    .latitude(stop.getLatitude())
                    .longitude(stop.getLongitude())
                    .sequence(seq)
                    .arrivalTime(arrivals.get(stop.getId()))
                    .departureTime(departures.get(stop.getId()))
                    .build();

            if (isPassed) {
                visited.add(sInfo);
                if (seq == (nextStopSeq - 1)) {
                    currentStop = sInfo;
                }
            } else {
                remaining.add(sInfo);
                if (seq == nextStopSeq && nextStop == null) {
                    nextStop = sInfo;
                }
            }
        }

        double progress = (eta != null && eta.getRouteProgress() != null)
                ? eta.getRouteProgress().getProgressPercent()
                : (routeStops.isEmpty() ? 0.0 : (double) visited.size() * 100.0 / routeStops.size());

        Integer nextStopEta = eta != null ? eta.getMinutesRemaining() : null;
        Double nextStopDist = eta != null ? eta.getDistanceMeters() : null;
        String etaStatus = eta != null ? eta.getStatus() : "ON_TIME";
        boolean isOffRoute = eta != null && eta.isOffRoute();
        Double deviationMeters = eta != null ? eta.getRouteDeviationMeters() : null;
        Integer delayMins = (eta != null && eta.getDelayMinutes() != null) ? eta.getDelayMinutes() : 0;
        boolean isGpsStale = eta != null && eta.isGpsStale();

        Double busLat = null;
        Double busLng = null;
        Double busSpeed = 0.0;
        Double busHeading = 0.0;
        Double busAccuracy = 10.0;
        String gpsStatus = "UNAVAILABLE";
        String trackingSource = "NONE";
        String lastGpsUpdate = null;

        Optional<TripLocation> latestLoc = tripLocationRepository.findFirstByTripIdOrderByTimestampDesc(trip.getId());
        if (latestLoc.isPresent() && latestLoc.get().getLatitude() != null && latestLoc.get().getLongitude() != null) {
            busLat = latestLoc.get().getLatitude();
            busLng = latestLoc.get().getLongitude();
            busSpeed = latestLoc.get().getSpeed() != null ? latestLoc.get().getSpeed() : 0.0;
            busHeading = latestLoc.get().getHeading() != null ? latestLoc.get().getHeading() : 0.0;
            busAccuracy = latestLoc.get().getAccuracy() != null ? latestLoc.get().getAccuracy() : 10.0;
            trackingSource = latestLoc.get().getTrackingSource() != null ? latestLoc.get().getTrackingSource() : "DRIVER_PHONE";
            if (latestLoc.get().getTimestamp() != null) {
                lastGpsUpdate = latestLoc.get().getTimestamp().toString();
                long sec = Math.abs(Duration.between(latestLoc.get().getTimestamp(), LocalDateTime.now()).getSeconds());
                gpsStatus = sec > 60 ? "GPS_STALE" : "LIVE";
            }
        } else if (trip.getBus() != null && trip.getBus().getCurrentLatitude() != null && trip.getBus().getLastUpdated() != null &&
                   (trip.getStartTime() == null || !trip.getBus().getLastUpdated().isBefore(trip.getStartTime()))) {
            busLat = trip.getBus().getCurrentLatitude();
            busLng = trip.getBus().getCurrentLongitude();
            lastGpsUpdate = trip.getBus().getLastUpdated().toString();
            long sec = Math.abs(Duration.between(trip.getBus().getLastUpdated(), LocalDateTime.now()).getSeconds());
            gpsStatus = sec > 60 ? "GPS_STALE" : "LIVE";
            trackingSource = "DRIVER_PHONE";
        }

        return ResponseEntity.ok(RouteProgressResponse.builder()
                .currentStop(currentStop)
                .nextStop(nextStop)
                .visitedStops(visited)
                .remainingStops(remaining)
                .progressPercentage(progress)
                .nextStopEtaMinutes(nextStopEta)
                .nextStopDistanceMeters(nextStopDist)
                .etaStatus(etaStatus)
                .offRoute(isOffRoute)
                .routeDeviationMeters(deviationMeters)
                .delayMinutes(delayMins)
                .gpsStale(isGpsStale)
                .routeName(trip.getRoute() != null ? trip.getRoute().getRouteName() : "")
                .polyline(trip.getRoute() != null ? trip.getRoute().getPolyline() : null)
                .startLatitude(trip.getRoute() != null ? trip.getRoute().getStartLatitude() : null)
                .startLongitude(trip.getRoute() != null ? trip.getRoute().getStartLongitude() : null)
                .endLatitude(trip.getRoute() != null ? trip.getRoute().getEndLatitude() : null)
                .endLongitude(trip.getRoute() != null ? trip.getRoute().getEndLongitude() : null)
                .currentLatitude(busLat)
                .currentLongitude(busLng)
                .speed(busSpeed)
                .heading(busHeading)
                .accuracy(busAccuracy)
                .gpsStatus(gpsStatus)
                .trackingSource(trackingSource)
                .lastGpsUpdate(lastGpsUpdate)
                .tripStatus(trip.getStatus())
                .tripId(trip.getId())
                .busNumber(trip.getBus() != null ? trip.getBus().getBusNumber() : "")
                .build());
    }

    @GetMapping("/trips/active")
    public ResponseEntity<TripResponse> getActiveTrip() {
        Driver driver = getAuthenticatedDriver();
        Optional<Trip> active = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"));
        return active.map(t -> ResponseEntity.ok(mapToTripResponse(t))).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/trips")
    public ResponseEntity<List<TripResponse>> getTripsHistory() {
        Driver driver = getAuthenticatedDriver();
        List<Trip> trips = tripRepository.findByDriverId(driver.getId());
        List<TripResponse> responses = trips.stream()
                .map(this::mapToTripResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationSummary>> getNotifications() {
        Driver driver = getAuthenticatedDriver();
        List<DriverNotification> notifs = driverNotificationRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId());
        List<NotificationSummary> summaries = notifs.stream().map(n -> NotificationSummary.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt().toString())
                .build()).collect(Collectors.toList());
        return ResponseEntity.ok(summaries);
    }

    @PostMapping("/emergency")
    public ResponseEntity<Map<String, Boolean>> triggerEmergency(@RequestBody EmergencyRequest dto) {
        Driver driver = getAuthenticatedDriver();
        Trip trip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"))
                .orElseThrow(() -> new BadRequestException("No active trip"));

        EmergencyLog log = EmergencyLog.builder()
                .trip(trip)
                .user(driver.getUser())
                .type("OTHER")
                .description("SOS TRIGGERED BY DRIVER")
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .resolved(false)
                .status("OPEN")
                .build();

        emergencyLogRepository.save(log);

        // Audit Log
        auditLogService.logAction(
                driver.getUser(),
                "SOS_TRIGGERED",
                String.format("Emergency SOS triggered by driver at coordinates [%.5f, %.5f]", dto.getLatitude(), dto.getLongitude()),
                "127.0.0.1",
                "Trip",
                trip.getId().toString(),
                null,
                String.format("Emergency SOS triggered by driver at coordinates [%.5f, %.5f]", dto.getLatitude(), dto.getLongitude())
        );

        // Broadcast to WebSocket subscribers
        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("type", "EMERGENCY_ALERT");
        wsPayload.put("tripId", trip.getId().toString());
        wsPayload.put("busNumber", trip.getBus().getBusNumber());
        wsPayload.put("driverName", driver.getUser().getFirstName() + " " + driver.getUser().getLastName());
        wsPayload.put("latitude", dto.getLatitude());
        wsPayload.put("longitude", dto.getLongitude());
        wsPayload.put("timestamp", LocalDateTime.now().toString());
        webSocketHandler.broadcast(wsPayload);

        return ResponseEntity.ok(Collections.singletonMap("success", true));
    }

    @PostMapping("/breakdown")
    public ResponseEntity<Map<String, Boolean>> reportBreakdown(@RequestBody BreakdownRequest dto) {
        Driver driver = getAuthenticatedDriver();
        Trip trip = tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"))
                .orElseThrow(() -> new BadRequestException("No active trip"));

        BreakdownReport report = BreakdownReport.builder()
                .driver(driver)
                .bus(trip.getBus())
                .trip(trip)
                .issueType(dto.getIssueType().toUpperCase())
                .description(dto.getDescription())
                .photoUrl(dto.getPhotoUrl())
                .build();

        breakdownReportRepository.save(report);

        Maintenance maint = Maintenance.builder()
                .bus(trip.getBus())
                .description("DRIVER REPORTED BREAKDOWN: [" + dto.getIssueType().toUpperCase() + "] " + dto.getDescription())
                .cost(BigDecimal.ZERO)
                .status("SCHEDULED")
                .scheduledDate(LocalDateTime.now())
                .build();

        maintenanceRepository.save(maint);

        // Audit Log
        auditLogService.logAction(
                driver.getUser(),
                "BREAKDOWN_REPORTED",
                "Breakdown reported: [" + dto.getIssueType() + "] - " + dto.getDescription(),
                "127.0.0.1",
                "Bus",
                trip.getBus().getId().toString(),
                null,
                "Breakdown reported: [" + dto.getIssueType() + "] - " + dto.getDescription()
        );

        // Broadcast warning
        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("type", "BREAKDOWN_ALERT");
        wsPayload.put("busNumber", trip.getBus().getBusNumber());
        wsPayload.put("issueType", dto.getIssueType());
        wsPayload.put("description", dto.getDescription());
        webSocketHandler.broadcast(wsPayload);

        return ResponseEntity.ok(Collections.singletonMap("success", true));
    }

    private TripResponse mapToTripResponse(Trip trip) {
        return TripResponse.builder()
                .tripId(trip.getId())
                .busId(trip.getBus() != null ? trip.getBus().getId() : null)
                .busNumber(trip.getBus() != null ? trip.getBus().getBusNumber() : "")
                .busCode(trip.getBus() != null ? trip.getBus().getBusCode() : "")
                .routeId(trip.getRoute() != null ? trip.getRoute().getId() : null)
                .routeName(trip.getRoute() != null ? trip.getRoute().getRouteName() : "")
                .status(trip.getStatus())
                .startTime(trip.getStartTime() != null ? trip.getStartTime().toString() : null)
                .endTime(trip.getEndTime() != null ? trip.getEndTime().toString() : null)
                .distance(trip.getDistance())
                .duration(trip.getDuration())
                .startLatitude(trip.getStartLatitude())
                .startLongitude(trip.getStartLongitude())
                .endLatitude(trip.getEndLatitude())
                .endLongitude(trip.getEndLongitude())
                .polyline(trip.getRoute() != null ? trip.getRoute().getPolyline() : null)
                .build();
    }

    // DTO Classes
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QrVerifyRequest {
        private String qrContent;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QrVerifyResponse {
        private boolean success;
        private BusInfo bus;
        private DriverInfo driver;
        private ScheduleInfo schedule;
        private String message;

        @Data @NoArgsConstructor @AllArgsConstructor @Builder
        public static class BusInfo {
            private String busNumber;
            private String busCode;
            private Integer capacity;
        }

        @Data @NoArgsConstructor @AllArgsConstructor @Builder
        public static class DriverInfo {
            private String name;
        }

        @Data @NoArgsConstructor @AllArgsConstructor @Builder
        public static class ScheduleInfo {
            private String routeName;
            private String departureTime;
            private UUID scheduleId;
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DriverDashboardResponse {
        private String driverName;
        private String driverStatus;
        private UUID busId;
        private String busNumber;
        private String busCode;
        private UUID routeId;
        private String routeName;
        private String startPoint;
        private String endPoint;
        private Double distance;
        private Integer totalStops;
        private String departureTime;
        private UUID scheduleId;
        private boolean gpsDeviceOnline;
        private boolean phoneGpsReady;
        private UUID activeTripId;
        private String activeTripStatus;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AssignmentSummary {
        private UUID assignmentId;
        private String busNumber;
        private String busCode;
        private String routeName;
        private String departureTime;
        private UUID scheduleId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AssignmentDetails {
        private UUID assignmentId;
        private String busNumber;
        private String busCode;
        private String routeName;
        private List<RouteProgressResponse.StopInfo> stops;
        private UUID scheduleId;
        private String departureTime;
        private String expectedArrivalTime;
        private String daysOfWeek;
        private String status;
        private boolean gpsDeviceOnline;
        private String tripStatus; // e.g. SCHEDULED, EN_ROUTE, PAUSED, COMPLETED
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DriverScheduleItem {
        private UUID scheduleId;
        private UUID busId;
        private String busNumber;
        private String busCode;
        private UUID routeId;
        private String routeName;
        private String startPoint;
        private String endPoint;
        private String departureTime;
        private String arrivalTime;
        private String daysOfWeek;
        private String scheduleStatus; // ACTIVE, INACTIVE
        private String tripStatus; // UPCOMING, IN_PROGRESS, PAUSED, COMPLETED
        private UUID activeTripId;
        private List<RouteProgressResponse.StopInfo> stops;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TripResponse {
        private UUID tripId;
        private UUID busId;
        private String busNumber;
        private String busCode;
        private UUID routeId;
        private String routeName;
        private String status;
        private String startTime;
        private String endTime;
        private Double distance;
        private Integer duration;
        private Double startLatitude;
        private Double startLongitude;
        private Double endLatitude;
        private Double endLongitude;
        private String polyline;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PauseRequest {
        private String reason;
        private Double latitude;
        private Double longitude;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class LocationUpdateRequest {
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double heading;
        private Double accuracy;
        private String timestamp;
        private String sourceEventId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class EmergencyRequest {
        private Double latitude;
        private Double longitude;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BreakdownRequest {
        private String issueType;
        private String description;
        private String photoUrl;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class NotificationSummary {
        private UUID id;
        private String title;
        private String message;
        private String type;
        private boolean isRead;
        private String createdAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RouteProgressResponse {
        private StopInfo currentStop;
        private StopInfo nextStop;
        private List<StopInfo> visitedStops;
        private List<StopInfo> remainingStops;
        private double progressPercentage;
        private Integer nextStopEtaMinutes;
        private Double nextStopDistanceMeters;
        private String etaStatus;
        private boolean offRoute;
        private Double routeDeviationMeters;
        private Integer delayMinutes;
        private boolean gpsStale;
        private String routeName;
        private String polyline;
        private Double startLatitude;
        private Double startLongitude;
        private Double endLatitude;
        private Double endLongitude;
        private Double currentLatitude;
        private Double currentLongitude;
        private Double speed;
        private Double heading;
        private Double accuracy;
        private String gpsStatus;
        private String trackingSource;
        private String lastGpsUpdate;
        private String tripStatus;
        private UUID tripId;
        private String busNumber;

        @Data @NoArgsConstructor @AllArgsConstructor @Builder
        public static class StopInfo {
            private UUID stopId;
            private String stopName;
            private Double latitude;
            private Double longitude;
            private Integer sequence;
            private String arrivalTime;
            private String departureTime;
        }
    }

    private String buildRouteSnapshot(Route route, List<RouteStop> stops) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("routeId", route.getId().toString());
            map.put("routeName", route.getRouteName());
            map.put("startPoint", route.getStartPoint());
            map.put("endPoint", route.getEndPoint());
            map.put("startLatitude", route.getStartLatitude());
            map.put("startLongitude", route.getStartLongitude());
            map.put("endLatitude", route.getEndLatitude());
            map.put("endLongitude", route.getEndLongitude());
            map.put("distance", route.getDistance());
            map.put("estimatedDurationMins", route.getEstimatedDurationMins());
            map.put("polyline", route.getPolyline());
            map.put("version", route.getVersion());

            List<Map<String, Object>> stopsList = new ArrayList<>();
            for (RouteStop rs : stops) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("stopId", rs.getStop().getId().toString());
                sm.put("stopName", rs.getStop().getStopName());
                sm.put("latitude", rs.getStop().getLatitude());
                sm.put("longitude", rs.getStop().getLongitude());
                sm.put("sequence", rs.getSequenceNumber());
                sm.put("distanceFromStart", rs.getDistanceFromStart());
                sm.put("durationFromStartMins", rs.getDurationFromStartMins());
                sm.put("arrivalTime", rs.getExpectedArrivalTime() != null ? rs.getExpectedArrivalTime().toString() : null);
                sm.put("departureTime", rs.getExpectedDepartureTime() != null ? rs.getExpectedDepartureTime().toString() : null);
                stopsList.add(sm);
            }
            map.put("stops", stopsList);
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private List<RouteStop> parseRouteStopsFromSnapshot(Route route, String snapshotJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(snapshotJson);
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
                            .route(route)
                            .stop(s)
                            .sequenceNumber(seq)
                            .distanceFromStart(dist)
                            .durationFromStartMins(dur)
                            .expectedArrivalTime(safeParseLocalTime(arr))
                            .expectedDepartureTime(safeParseLocalTime(dep))
                            .build();
                    list.add(rs);
                }
                return list;
            }
        } catch (Exception ignored) {}
        return routeStopRepository.findByRouteOrderBySequenceNumberAsc(route);
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
}
