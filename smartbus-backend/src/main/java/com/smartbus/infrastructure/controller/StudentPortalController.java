package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuditLogService;
import com.smartbus.application.service.EtaService;
import com.smartbus.application.service.EtaCalculationService;
import com.smartbus.application.service.GeofencingService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.security.UserPrincipal;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import com.smartbus.infrastructure.controller.DriverPortalController.RouteProgressResponse;

@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('STUDENT')")
public class StudentPortalController {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;
    private final TripRepository tripRepository;
    private final NotificationRepository notificationRepository;
    private final ScheduleRepository scheduleRepository;
    private final AuditLogService auditLogService;
    private final EtaService etaService;
    private final EtaCalculationService etaCalculationService;
    private final GeofencingService geofencingService;
    private final TripLocationRepository tripLocationRepository;

    private Student getAuthenticatedStudent() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = principal.getUser();
        return studentRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<StudentDashboardResponse> getDashboard() {
        Student student = getAuthenticatedStudent();
        
        // Greeting
        String greeting = "Good Day, " + student.getUser().getFirstName();
        LocalTime nowTime = LocalTime.now();
        if (nowTime.isBefore(LocalTime.of(12, 0))) {
            greeting = "Good Morning, " + student.getUser().getFirstName();
        } else if (nowTime.isBefore(LocalTime.of(17, 0))) {
            greeting = "Good Afternoon, " + student.getUser().getFirstName();
        } else {
            greeting = "Good Evening, " + student.getUser().getFirstName();
        }

        String homeStop = student.getPreferredStop() != null ? student.getPreferredStop().getStopName() : "Not Set";

        // Find active trip for student's preferred bus, favorite buses or any active trip in the system
        List<Bus> candidates = new ArrayList<>();
        if (student.getPreferredBus() != null) {
            candidates.add(student.getPreferredBus());
        }
        candidates.addAll(student.getFavoriteBuses());
        
        Trip activeTrip = null;
        if (!candidates.isEmpty()) {
            for (Bus cand : candidates) {
                Optional<Trip> tripOpt = tripRepository.findByBusIdAndStatusIn(cand.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"));
                if (tripOpt.isPresent()) {
                    activeTrip = tripOpt.get();
                    break;
                }
            }
        }
        
        // Fallback: if no preferred/favorite bus is active, check if there is ANY active trip in the student's college only if no preferred bus is configured
        if (activeTrip == null && student.getPreferredBus() == null) {
            List<Trip> activeTrips = student.getCollege() != null
                    ? tripRepository.findByCollegeIdAndStatusIn(student.getCollege().getId(), Arrays.asList("IN_PROGRESS", "PAUSED"))
                    : tripRepository.findByStatusIn(Arrays.asList("IN_PROGRESS", "PAUSED"));
            if (!activeTrips.isEmpty()) {
                activeTrip = activeTrips.get(0);
            }
        }

        ActiveBusSummary yourBus = null;
        Integer homeEta = -1;

        if (activeTrip != null) {
            UUID targetStopId = student.getPreferredStop() != null ? student.getPreferredStop().getId() : null;
            com.smartbus.infrastructure.dto.EtaResponse etaResp = etaCalculationService.calculateEta(activeTrip.getId(), targetStopId);

            if (targetStopId != null && etaResp.getMinutesRemaining() != null) {
                homeEta = etaResp.getMinutesRemaining();
            }

            Double lat = activeTrip.getBus().getCurrentLatitude();
            Double lng = activeTrip.getBus().getCurrentLongitude();
            Double speed = 0.0;
            Double heading = 0.0;
            String lastUpdated = activeTrip.getBus().getLastUpdated() != null ? activeTrip.getBus().getLastUpdated().toString() : null;

            if (tripLocationRepository != null) {
                Optional<TripLocation> latestLoc = tripLocationRepository.findFirstByTripIdOrderByTimestampDesc(activeTrip.getId());
                if (latestLoc.isPresent()) {
                    TripLocation loc = latestLoc.get();
                    if (loc.getLatitude() != null && loc.getLongitude() != null) {
                        lat = loc.getLatitude();
                        lng = loc.getLongitude();
                    }
                    if (loc.getSpeed() != null) speed = loc.getSpeed();
                    if (loc.getHeading() != null) heading = loc.getHeading();
                    if (loc.getTimestamp() != null) lastUpdated = loc.getTimestamp().toString();
                }
            }

            boolean hasAcceptedGps = (lat != null && lng != null && lastUpdated != null);
            boolean isGpsStale = false;
            String gpsStatus = "UNAVAILABLE";

            if (hasAcceptedGps) {
                try {
                    LocalDateTime lu = LocalDateTime.parse(lastUpdated);
                    if (Duration.between(lu, LocalDateTime.now()).getSeconds() > 60) {
                        isGpsStale = true;
                        gpsStatus = "GPS_STALE";
                    } else {
                        gpsStatus = "LIVE";
                    }
                } catch (Exception e) {
                    gpsStatus = "LIVE";
                }
            } else {
                lat = null;
                lng = null;
            }

            if (etaResp.getGpsStatus() != null) {
                gpsStatus = etaResp.getGpsStatus();
            }

            yourBus = ActiveBusSummary.builder()
                    .busId(activeTrip.getBus().getId())
                    .busNumber(activeTrip.getBus().getBusNumber())
                    .busCode(activeTrip.getBus().getBusCode())
                    .routeName(activeTrip.getRoute().getRouteName())
                    .driverName(activeTrip.getDriver().getUser().getFirstName() + " " + activeTrip.getDriver().getUser().getLastName())
                    .currentStop(etaResp.getCurrentStopName() != null ? etaResp.getCurrentStopName() : "In Transit")
                    .nextStop(etaResp.getNextStopName() != null ? etaResp.getNextStopName() : "None")
                    .eta(etaResp.getMinutesRemaining() != null ? etaResp.getMinutesRemaining() : -1)
                    .status("PAUSED".equals(activeTrip.getStatus()) ? "PAUSED" : "LIVE")
                    .etaStatus(etaResp.getStatus())
                    .distanceMeters(etaResp.getDistanceMeters())
                    .estimatedArrivalTime(etaResp.getEstimatedArrivalTime())
                    .offRoute(etaResp.isOffRoute())
                    .gpsStale(isGpsStale)
                    .gpsStatus(gpsStatus)
                    .routeDeviationMeters(etaResp.getRouteDeviationMeters())
                    .delayMinutes(etaResp.getDelayMinutes())
                    .trackingSource(etaResp.getTrackingSource())
                    .latitude(lat)
                    .longitude(lng)
                    .speed(speed)
                    .heading(heading)
                    .lastUpdated(lastUpdated)
                    .upcomingStops(etaResp.getUpcomingStops() != null ? etaResp.getUpcomingStops() : Collections.emptyList())
                    .build();
        } else {
            // Find scheduled bus for student
            Schedule scheduledTrip = null;
            if (student.getPreferredBus() != null) {
                List<Schedule> prefScheds = scheduleRepository.findByBusIdAndDeletedAtIsNull(student.getPreferredBus().getId());
                if (!prefScheds.isEmpty()) {
                    scheduledTrip = prefScheds.get(0);
                }
            }
            if (scheduledTrip == null && !candidates.isEmpty()) {
                for (Bus cand : candidates) {
                    List<Schedule> scheds = scheduleRepository.findByBusIdAndDeletedAtIsNull(cand.getId());
                    if (!scheds.isEmpty()) {
                        scheduledTrip = scheds.get(0);
                        break;
                    }
                }
            }
            if (scheduledTrip == null && student.getPreferredRoute() != null) {
                List<Schedule> routeScheds = scheduleRepository.findByRouteIdAndDeletedAtIsNull(student.getPreferredRoute().getId());
                if (!routeScheds.isEmpty()) {
                    scheduledTrip = routeScheds.get(0);
                }
            }
            if (scheduledTrip == null && student.getPreferredStop() != null) {
                List<RouteStop> rStops = routeStopRepository.findAll();
                for (RouteStop rs : rStops) {
                    if (rs.getStop().getId().equals(student.getPreferredStop().getId())) {
                        List<Schedule> scheds = scheduleRepository.findByRouteIdAndDeletedAtIsNull(rs.getRoute().getId());
                        if (!scheds.isEmpty()) {
                            scheduledTrip = scheds.get(0);
                            break;
                        }
                    }
                }
            }
            if (scheduledTrip == null) {
                List<Schedule> allScheds = student.getCollege() != null
                        ? scheduleRepository.findByCollegeIdAndDeletedAtIsNull(student.getCollege().getId())
                        : scheduleRepository.findByDeletedAtIsNull();
                if (!allScheds.isEmpty()) {
                    scheduledTrip = allScheds.get(0);
                }
            }

            if (scheduledTrip != null && scheduledTrip.getBus() != null) {
                Bus sBus = scheduledTrip.getBus();
                String driverFullName = scheduledTrip.getDriver() != null
                        ? scheduledTrip.getDriver().getUser().getFirstName() + " " + scheduledTrip.getDriver().getUser().getLastName()
                        : "Assigned Driver";
                yourBus = ActiveBusSummary.builder()
                        .busId(sBus.getId())
                        .busNumber(sBus.getBusNumber())
                        .busCode(sBus.getBusCode())
                        .routeName(scheduledTrip.getRoute() != null ? scheduledTrip.getRoute().getRouteName() : "Scheduled Route")
                        .driverName(driverFullName)
                        .currentStop("Not Started")
                        .nextStop(scheduledTrip.getRoute() != null ? scheduledTrip.getRoute().getStartPoint() : "Depot")
                        .eta(-1)
                        .status("SCHEDULED")
                        .etaStatus("NOT_STARTED")
                        .trackingSource(sBus.getGpsDevice() != null ? "GPS_DEVICE" : "MOBILE_PORTAL")
                        .build();
            }
        }

        // List other active buses in student's college
        List<Trip> activeTrips = student.getCollege() != null
                ? tripRepository.findByCollegeIdAndStatusIn(student.getCollege().getId(), Arrays.asList("IN_PROGRESS", "PAUSED"))
                : tripRepository.findByStatusIn(Arrays.asList("IN_PROGRESS", "PAUSED"));
        List<OtherBusSummary> otherBuses = new ArrayList<>();
        for (Trip t : activeTrips) {
            if (activeTrip != null && t.getId().equals(activeTrip.getId())) {
                continue; // skip current tracked bus
            }
            otherBuses.add(OtherBusSummary.builder()
                    .busId(t.getBus().getId())
                    .busNumber(t.getBus().getBusNumber())
                    .busCode(t.getBus().getBusCode())
                    .routeName(t.getRoute().getRouteName())
                    .status(t.getStatus())
                    .build());
        }

        StudentDashboardResponse response = StudentDashboardResponse.builder()
                .greeting(greeting)
                .homeStop(homeStop)
                .yourBus(yourBus)
                .homeEta(homeEta)
                .otherBuses(otherBuses)
                .build();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/buses")
    public ResponseEntity<List<StudentBusDetails>> getAllBuses() {
        Student student = getAuthenticatedStudent();
        List<Bus> buses = student.getCollege() != null
                ? busRepository.findByCollegeIdAndDeletedAtIsNull(student.getCollege().getId())
                : busRepository.findByDeletedAtIsNull();
        List<StudentBusDetails> details = buses.stream().map(this::mapToStudentBusDetails).collect(Collectors.toList());
        return ResponseEntity.ok(details);
    }

    @GetMapping("/buses/{id}")
    public ResponseEntity<StudentBusDetails> getBusDetails(@PathVariable UUID id) {
        Student student = getAuthenticatedStudent();
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        if (student.getCollege() != null && (bus.getCollege() == null || !bus.getCollege().getId().equals(student.getCollege().getId()))) {
            throw new ResourceNotFoundException("Bus not found");
        }
        return ResponseEntity.ok(mapToStudentBusDetails(bus));
    }

    @GetMapping("/buses/{id}/stops")
    public ResponseEntity<List<RouteProgressResponse.StopInfo>> getBusStops(@PathVariable UUID id) {
        Student student = getAuthenticatedStudent();
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        if (student.getCollege() != null && (bus.getCollege() == null || !bus.getCollege().getId().equals(student.getCollege().getId()))) {
            throw new ResourceNotFoundException("Bus not found");
        }
        // Check active trip first
        Optional<Trip> activeTrip = tripRepository.findByBusIdAndStatusIn(id, Arrays.asList("IN_PROGRESS", "PAUSED"));
        Route route = null;
        if (activeTrip.isPresent()) {
            route = activeTrip.get().getRoute();
        } else {
            // Check scheduled routes for this bus
            List<Schedule> schedules = scheduleRepository.findByBusIdAndDeletedAtIsNull(id);
            if (!schedules.isEmpty()) {
                route = schedules.get(0).getRoute();
            }
        }
        if (route == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<RouteStop> rStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(route.getId());
        List<RouteProgressResponse.StopInfo> stops = rStops.stream().map(rs -> RouteProgressResponse.StopInfo.builder()
                .stopId(rs.getStop().getId())
                .stopName(rs.getStop().getStopName())
                .latitude(rs.getStop().getLatitude().doubleValue())
                .longitude(rs.getStop().getLongitude().doubleValue())
                .sequence(rs.getSequenceNumber())
                .arrivalTime(rs.getExpectedArrivalTime() != null ? rs.getExpectedArrivalTime().toString() : null)
                .departureTime(rs.getExpectedDepartureTime() != null ? rs.getExpectedDepartureTime().toString() : null)
                .build()).collect(Collectors.toList());
        return ResponseEntity.ok(stops);
    }

    @GetMapping("/buses/search")
    public ResponseEntity<List<StudentBusDetails>> searchBuses(@RequestParam(value = "q", defaultValue = "") String query) {
        Student student = getAuthenticatedStudent();
        String lowerQuery = query.toLowerCase().trim();
        List<Bus> allBuses = student.getCollege() != null
                ? busRepository.findByCollegeIdAndDeletedAtIsNull(student.getCollege().getId())
                : busRepository.findByDeletedAtIsNull();
        
        List<Bus> filtered = allBuses.stream().filter(b -> {
            if (b.getBusNumber().toLowerCase().contains(lowerQuery)) return true;
            if (b.getBusCode() != null && b.getBusCode().toLowerCase().contains(lowerQuery)) return true;
            
            // Search routes serving this bus
            List<Schedule> schedules = scheduleRepository.findByBusIdAndDeletedAtIsNull(b.getId());
            for (Schedule s : schedules) {
                if (s.getRoute().getRouteName().toLowerCase().contains(lowerQuery)) return true;
                
                // Search stop names serving this route
                List<RouteStop> rStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(s.getRoute().getId());
                for (RouteStop rs : rStops) {
                    if (rs.getStop().getStopName().toLowerCase().contains(lowerQuery)) return true;
                }
            }
            return false;
        }).collect(Collectors.toList());

        List<StudentBusDetails> detailsList = filtered.stream().map(this::mapToStudentBusDetails).collect(Collectors.toList());
        return ResponseEntity.ok(detailsList);
    }

    @GetMapping("/routes")
    public ResponseEntity<List<Route>> getRoutes() {
        Student student = getAuthenticatedStudent();
        return ResponseEntity.ok(student.getCollege() != null
                ? routeRepository.findByCollegeIdAndDeletedAtIsNull(student.getCollege().getId())
                : routeRepository.findByDeletedAtIsNull());
    }

    @GetMapping("/routes/{id}")
    public ResponseEntity<StudentRouteDetails> getRouteDetails(@PathVariable UUID id) {
        Student student = getAuthenticatedStudent();
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        if (student.getCollege() != null && (route.getCollege() == null || !route.getCollege().getId().equals(student.getCollege().getId()))) {
            throw new ResourceNotFoundException("Route not found");
        }

        List<RouteStop> rStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(route.getId());
        List<RouteProgressResponse.StopInfo> stops = rStops.stream().map(rs -> RouteProgressResponse.StopInfo.builder()
                .stopId(rs.getStop().getId())
                .stopName(rs.getStop().getStopName())
                .latitude(rs.getStop().getLatitude().doubleValue())
                .longitude(rs.getStop().getLongitude().doubleValue())
                .sequence(rs.getSequenceNumber())
                .arrivalTime(rs.getExpectedArrivalTime() != null ? rs.getExpectedArrivalTime().toString() : null)
                .departureTime(rs.getExpectedDepartureTime() != null ? rs.getExpectedDepartureTime().toString() : null)
                .build()).collect(Collectors.toList());

        return ResponseEntity.ok(StudentRouteDetails.builder()
                .routeId(route.getId())
                .routeName(route.getRouteName())
                .startPoint(route.getStartPoint())
                .endPoint(route.getEndPoint())
                .distance(route.getDistance().doubleValue())
                .estimatedDurationMins(route.getEstimatedDurationMins())
                .startLatitude(route.getStartLatitude() != null ? route.getStartLatitude().doubleValue() : null)
                .startLongitude(route.getStartLongitude() != null ? route.getStartLongitude().doubleValue() : null)
                .endLatitude(route.getEndLatitude() != null ? route.getEndLatitude().doubleValue() : null)
                .endLongitude(route.getEndLongitude() != null ? route.getEndLongitude().doubleValue() : null)
                .polyline(route.getPolyline())
                .stops(stops)
                .build());
    }

    @GetMapping("/live/{busId}")
    public ResponseEntity<LiveBusLocationResponse> getLiveBusLocation(@PathVariable UUID busId) {
        Student student = getAuthenticatedStudent();
        Trip trip = tripRepository.findByBusIdAndStatusIn(busId, Arrays.asList("IN_PROGRESS", "PAUSED"))
                .orElseThrow(() -> new BadRequestException("Bus is not currently on a trip."));

        if (student.getCollege() != null && (trip.getBus().getCollege() == null || !trip.getBus().getCollege().getId().equals(student.getCollege().getId()))) {
            throw new ResourceNotFoundException("Bus not found");
        }

        Bus bus = trip.getBus();
        com.smartbus.infrastructure.dto.EtaResponse etaResp = etaCalculationService.calculateBusEta(busId, null);

        Double lat = null;
        Double lng = null;
        Double speed = 0.0;
        Double heading = 0.0;
        String trackingSource = etaResp.getTrackingSource();
        LocalDateTime lastUpdated = null;

        if (tripLocationRepository != null) {
            Optional<TripLocation> latestLoc = tripLocationRepository.findFirstByTripIdOrderByTimestampDesc(trip.getId());
            if (latestLoc.isPresent()) {
                TripLocation loc = latestLoc.get();
                if (loc.getLatitude() != null && loc.getLongitude() != null) {
                    lat = loc.getLatitude();
                    lng = loc.getLongitude();
                }
                if (loc.getSpeed() != null) speed = loc.getSpeed();
                if (loc.getHeading() != null) heading = loc.getHeading();
                if (loc.getTrackingSource() != null) trackingSource = loc.getTrackingSource();
                if (loc.getTimestamp() != null) lastUpdated = loc.getTimestamp();
            }
        }

        if ((lat == null || lng == null) && bus.getCurrentLatitude() != null && bus.getLastUpdated() != null) {
            if (trip.getStartTime() == null || !bus.getLastUpdated().isBefore(trip.getStartTime())) {
                lat = bus.getCurrentLatitude();
                lng = bus.getCurrentLongitude();
                lastUpdated = bus.getLastUpdated();
            }
        }

        boolean hasAcceptedGps = (lat != null && lng != null && lastUpdated != null);
        boolean isGpsStale = false;
        String gpsStatus = "UNAVAILABLE";

        if (hasAcceptedGps) {
            if (Duration.between(lastUpdated, LocalDateTime.now()).getSeconds() > 60) {
                isGpsStale = true;
                gpsStatus = "GPS_STALE";
            } else {
                gpsStatus = "LIVE";
            }
        } else {
            lat = null;
            lng = null;
        }

        if (!hasAcceptedGps) {
            gpsStatus = "UNAVAILABLE";
        } else if (etaResp.getGpsStatus() != null) {
            gpsStatus = etaResp.getGpsStatus();
        }

        LiveBusLocationResponse live = LiveBusLocationResponse.builder()
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busCode(bus.getBusCode())
                .tripId(trip.getId())
                .latitude(lat)
                .longitude(lng)
                .speed(speed)
                .heading(heading)
                .trackingSource(trackingSource)
                .lastUpdate(lastUpdated != null ? lastUpdated.toString() : null)
                .currentStop(etaResp.getCurrentStopName() != null ? etaResp.getCurrentStopName() : "In Transit")
                .nextStop(etaResp.getNextStopName() != null ? etaResp.getNextStopName() : "None")
                .eta(etaResp.getMinutesRemaining() != null ? etaResp.getMinutesRemaining() : -1)
                .etaStatus(etaResp.getStatus())
                .distanceMeters(etaResp.getDistanceMeters())
                .estimatedArrivalTime(etaResp.getEstimatedArrivalTime())
                .offRoute(etaResp.isOffRoute())
                .gpsStale(isGpsStale)
                .gpsStatus(gpsStatus)
                .routeDeviationMeters(etaResp.getRouteDeviationMeters())
                .delayMinutes(etaResp.getDelayMinutes())
                .upcomingStops(etaResp.getUpcomingStops() != null ? etaResp.getUpcomingStops() : Collections.emptyList())
                .routeProgress(etaResp.getRouteProgress())
                .build();

        return ResponseEntity.ok(live);
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getNotifications() {
        Student student = getAuthenticatedStudent();
        List<Notification> list = notificationRepository.findNotificationsForUser(student.getUser().getId());
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Boolean>> markNotificationAsRead(@PathVariable UUID id) {
        Student student = getAuthenticatedStudent();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        
        // Enforce owner check: must belong to authenticated student user
        if (notification.getUser() != null && !notification.getUser().getId().equals(student.getUser().getId())) {
            throw new BadRequestException("UNAUTHORIZED_ACCESS");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok(Collections.singletonMap("success", true));
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<StudentBusDetails>> getFavorites() {
        Student student = getAuthenticatedStudent();
        List<StudentBusDetails> list = student.getFavoriteBuses().stream().map(this::mapToStudentBusDetails).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/favorites/{busId}")
    public ResponseEntity<Map<String, Boolean>> favoriteBus(@PathVariable UUID busId) {
        Student student = getAuthenticatedStudent();
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        if (student.getCollege() != null && (bus.getCollege() == null || !bus.getCollege().getId().equals(student.getCollege().getId()))) {
            throw new ResourceNotFoundException("Bus not found");
        }

        student.getFavoriteBuses().add(bus);
        studentRepository.save(student);

        auditLogService.logAction(
                student.getUser(),
                "STUDENT_BUS_FAVORITED",
                "Student favorited Bus " + bus.getBusNumber(),
                "127.0.0.1",
                "Bus",
                busId.toString(),
                null,
                null
        );

        return ResponseEntity.ok(Collections.singletonMap("success", true));
    }

    @DeleteMapping("/favorites/{busId}")
    public ResponseEntity<Map<String, Boolean>> unfavoriteBus(@PathVariable UUID busId) {
        Student student = getAuthenticatedStudent();
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        if (student.getCollege() != null && (bus.getCollege() == null || !bus.getCollege().getId().equals(student.getCollege().getId()))) {
            throw new ResourceNotFoundException("Bus not found");
        }

        student.getFavoriteBuses().remove(bus);
        studentRepository.save(student);

        auditLogService.logAction(
                student.getUser(),
                "STUDENT_BUS_UNFAVORITED",
                "Student unfavorited Bus " + bus.getBusNumber(),
                "127.0.0.1",
                "Bus",
                busId.toString(),
                null,
                null
        );

        return ResponseEntity.ok(Collections.singletonMap("success", true));
    }

    @GetMapping("/profile")
    public ResponseEntity<StudentProfileResponse> getProfile() {
        Student student = getAuthenticatedStudent();
        return ResponseEntity.ok(mapToStudentProfileResponse(student));
    }

    @PutMapping("/profile")
    public ResponseEntity<StudentProfileResponse> updateProfile(@RequestBody Map<String, String> request) {
        Student student = getAuthenticatedStudent();
        User user = student.getUser();
        if (request.containsKey("phone") || request.containsKey("phoneNumber")) {
            String phone = request.getOrDefault("phone", request.get("phoneNumber"));
            if (phone != null && !phone.isBlank()) {
                if (!phone.matches("^\\+?[0-9]{7,15}$")) {
                    throw new BadRequestException("Invalid phone number format. Must contain 7 to 15 digits with optional leading +.");
                }
                user.setPhoneNumber(phone);
            }
        }
        if (request.containsKey("name")) {
            String name = request.get("name");
            if (name != null && !name.isBlank()) {
                String[] parts = name.trim().split(" ", 2);
                user.setFirstName(parts[0]);
                if (parts.length > 1) {
                    user.setLastName(parts[1]);
                }
            }
        }
        userRepository.save(user);
        return ResponseEntity.ok(mapToStudentProfileResponse(student));
    }

    @RequestMapping(value = {"/profile/home-location", "/location/home"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<StudentProfileResponse> updateHomeLocation(@RequestBody HomeLocationRequest request) {
        Student student = getAuthenticatedStudent();
        student.setHomeLatitude(request.getLatitude());
        student.setHomeLongitude(request.getLongitude());
        student.setHomeAddress(request.getAddress());
        studentRepository.save(student);

        auditLogService.logAction(
                student.getUser(),
                "STUDENT_HOME_LOCATION_UPDATED",
                "Student updated home location: " + request.getAddress(),
                "127.0.0.1",
                "Student",
                student.getId().toString(),
                null,
                null
        );

        return ResponseEntity.ok(mapToStudentProfileResponse(student));
    }

    @PutMapping("/profile/preferred-stop")
    public ResponseEntity<StudentProfileResponse> updatePreferredStop(@RequestBody Map<String, String> request) {
        Student student = getAuthenticatedStudent();
        UUID stopId = UUID.fromString(request.get("stopId"));
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found"));
        if (student.getCollege() != null && (stop.getCollege() == null || !stop.getCollege().getId().equals(student.getCollege().getId()))) {
            throw new ResourceNotFoundException("Stop not found");
        }

        student.setPreferredStop(stop);
        studentRepository.save(student);

        auditLogService.logAction(
                student.getUser(),
                "STUDENT_PREFERRED_STOP_UPDATED",
                "Student updated preferred stop to: " + stop.getStopName(),
                "127.0.0.1",
                "Stop",
                stopId.toString(),
                null,
                null
        );

        return ResponseEntity.ok(mapToStudentProfileResponse(student));
    }

    @PostMapping("/stops/preferred/{stopId}")
    public ResponseEntity<Map<String, Object>> setPreferredStopPost(@PathVariable UUID stopId) {
        Student student = getAuthenticatedStudent();
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found"));
        if (student.getCollege() != null && (stop.getCollege() == null || !stop.getCollege().getId().equals(student.getCollege().getId()))) {
            throw new ResourceNotFoundException("Stop not found");
        }

        student.setPreferredStop(stop);
        studentRepository.save(student);

        auditLogService.logAction(
                student.getUser(),
                "STUDENT_PREFERRED_STOP_UPDATED",
                "Student updated preferred stop to: " + stop.getStopName(),
                "127.0.0.1",
                "Stop",
                stopId.toString(),
                null,
                null
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("preferredStopId", stop.getId().toString());
        resp.put("preferredStopName", stop.getStopName());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/preferences")
    public ResponseEntity<StudentPreferencesResponse> getPreferences() {
        Student student = getAuthenticatedStudent();
        UUID prefStopId = student.getPreferredStop() != null ? student.getPreferredStop().getId() : null;
        String prefStopName = student.getPreferredStop() != null ? student.getPreferredStop().getStopName() : null;

        StudentPreferencesResponse resp = StudentPreferencesResponse.builder()
                .preferredRouteId(student.getPreferredRoute() != null ? student.getPreferredRoute().getId() : null)
                .preferredRouteName(student.getPreferredRoute() != null ? student.getPreferredRoute().getRouteName() : null)
                .preferredBusId(student.getPreferredBus() != null ? student.getPreferredBus().getId() : null)
                .preferredBusNumber(student.getPreferredBus() != null ? student.getPreferredBus().getBusNumber() : null)
                .preferredStopId(prefStopId)
                .preferredStopName(prefStopName)
                .homeStopId(prefStopId)
                .homeStopName(prefStopName)
                .homeLatitude(student.getHomeLatitude())
                .homeLongitude(student.getHomeLongitude())
                .homeAddress(student.getHomeAddress())
                .notificationPreferences(student.getNotificationPreferences() != null ? student.getNotificationPreferences() : "APPROACHING,ARRIVED,DEPARTED,DELAYED")
                .build();
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/preferences")
    public ResponseEntity<StudentPreferencesResponse> savePreferences(@RequestBody StudentPreferencesRequest request) {
        Student student = getAuthenticatedStudent();

        UUID reqRouteId = request.getResolvedRouteId();
        UUID reqBusId = request.getResolvedBusId();
        UUID reqStopId = request.getResolvedStopId();

        Route route = null;
        if (reqRouteId != null) {
            route = routeRepository.findById(reqRouteId)
                    .orElseThrow(() -> new BadRequestException("Route not found"));
            if (student.getCollege() != null && (route.getCollege() == null || !route.getCollege().getId().equals(student.getCollege().getId()))) {
                throw new BadRequestException("Route not found");
            }
            if (route.getDeletedAt() != null || (route.getStatus() != null && !"ACTIVE".equalsIgnoreCase(route.getStatus()))) {
                throw new BadRequestException("Selected route is inactive");
            }
        }

        Bus bus = null;
        if (reqBusId != null) {
            bus = busRepository.findById(reqBusId)
                    .orElseThrow(() -> new BadRequestException("Bus not found"));
            if (student.getCollege() != null && (bus.getCollege() == null || !bus.getCollege().getId().equals(student.getCollege().getId()))) {
                throw new BadRequestException("Bus not found");
            }
            if (bus.getDeletedAt() != null || (bus.getStatus() != null && !"ACTIVE".equalsIgnoreCase(bus.getStatus()))) {
                throw new BadRequestException("Selected bus is inactive");
            }
            if (route != null) {
                // Validate bus serves route via Schedule
                final UUID finalBusId = bus.getId();
                List<Schedule> scheds = scheduleRepository.findByRouteIdAndDeletedAtIsNull(route.getId());
                boolean serves = scheds.stream().anyMatch(s -> s.getBus() != null && s.getBus().getId().equals(finalBusId));
                if (!serves) {
                    throw new BadRequestException("Bus does not belong to selected route");
                }
            }
        }

        Stop stop = null;
        if (reqStopId != null) {
            stop = stopRepository.findById(reqStopId)
                    .orElseThrow(() -> new BadRequestException("Stop not found"));
            if (student.getCollege() != null && (stop.getCollege() == null || !stop.getCollege().getId().equals(student.getCollege().getId()))) {
                throw new BadRequestException("Stop not found");
            }
            if (route != null) {
                final UUID finalStopId = stop.getId();
                List<RouteStop> rStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(route.getId());
                boolean stopBelongs = rStops.stream().anyMatch(rs -> rs.getStop().getId().equals(finalStopId));
                if (!stopBelongs) {
                    throw new BadRequestException("Stop does not belong to selected route");
                }
            }
        }

        student.setPreferredRoute(route);
        student.setPreferredBus(bus);
        student.setPreferredStop(stop);

        if (request.getHomeLatitude() != null) student.setHomeLatitude(request.getHomeLatitude());
        if (request.getHomeLongitude() != null) student.setHomeLongitude(request.getHomeLongitude());
        if (request.getHomeAddress() != null) student.setHomeAddress(request.getHomeAddress());
        if (request.getNotificationPreferences() != null) student.setNotificationPreferences(request.getNotificationPreferences());

        studentRepository.save(student);

        auditLogService.logAction(
                student.getUser(),
                "STUDENT_PREFERENCES_UPDATED",
                "Updated preferences: Route=" + (route != null ? route.getRouteName() : "None") +
                        ", Bus=" + (bus != null ? bus.getBusNumber() : "None") +
                        ", Stop=" + (stop != null ? stop.getStopName() : "None"),
                "127.0.0.1",
                "Student",
                student.getId().toString(),
                null,
                null
        );

        UUID prefStopId = student.getPreferredStop() != null ? student.getPreferredStop().getId() : null;
        String prefStopName = student.getPreferredStop() != null ? student.getPreferredStop().getStopName() : null;

        StudentPreferencesResponse resp = StudentPreferencesResponse.builder()
                .preferredRouteId(student.getPreferredRoute() != null ? student.getPreferredRoute().getId() : null)
                .preferredRouteName(student.getPreferredRoute() != null ? student.getPreferredRoute().getRouteName() : null)
                .preferredBusId(student.getPreferredBus() != null ? student.getPreferredBus().getId() : null)
                .preferredBusNumber(student.getPreferredBus() != null ? student.getPreferredBus().getBusNumber() : null)
                .preferredStopId(prefStopId)
                .preferredStopName(prefStopName)
                .homeStopId(prefStopId)
                .homeStopName(prefStopName)
                .homeLatitude(student.getHomeLatitude())
                .homeLongitude(student.getHomeLongitude())
                .homeAddress(student.getHomeAddress())
                .notificationPreferences(student.getNotificationPreferences())
                .build();

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/schedules")
    public ResponseEntity<List<StudentScheduleItem>> getSchedules() {
        Student student = getAuthenticatedStudent();

        List<Schedule> allScheds = student.getCollege() != null
                ? scheduleRepository.findByCollegeIdAndDeletedAtIsNull(student.getCollege().getId())
                : scheduleRepository.findByDeletedAtIsNull();
        // Filter to active schedules with non-deleted bus and route
        List<Schedule> activeScheds = allScheds.stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus()) && s.getBus() != null && s.getRoute() != null)
                .filter(s -> s.getBus().getDeletedAt() == null && s.getRoute().getDeletedAt() == null)
                .collect(Collectors.toList());

        List<Trip> activeTrips = student.getCollege() != null
                ? tripRepository.findByCollegeIdAndStatusIn(student.getCollege().getId(), Arrays.asList("IN_PROGRESS", "PAUSED"))
                : tripRepository.findByStatusIn(Arrays.asList("IN_PROGRESS", "PAUSED"));
        Map<UUID, Trip> activeTripsByBus = new HashMap<>();
        Map<UUID, Trip> activeTripsBySchedule = new HashMap<>();
        for (Trip t : activeTrips) {
            if (t.getBus() != null) activeTripsByBus.put(t.getBus().getId(), t);
            if (t.getSchedule() != null) activeTripsBySchedule.put(t.getSchedule().getId(), t);
        }

        List<StudentScheduleItem> result = new ArrayList<>();
        for (Schedule s : activeScheds) {
            Trip trip = activeTripsBySchedule.get(s.getId());
            if (trip == null && s.getBus() != null) {
                trip = activeTripsByBus.get(s.getBus().getId());
            }

            String tripStatus = "SCHEDULED";
            UUID activeTripId = null;
            if (trip != null) {
                tripStatus = "PAUSED".equalsIgnoreCase(trip.getStatus()) ? "PAUSED" : "LIVE";
                activeTripId = trip.getId();
            } else {
                List<Trip> scheduleTrips = tripRepository.findByScheduleId(s.getId());
                if (!scheduleTrips.isEmpty() && "COMPLETED".equalsIgnoreCase(scheduleTrips.get(0).getStatus())) {
                    tripStatus = "COMPLETED";
                }
            }

            // Get stops for route
            List<RouteStop> rStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(s.getRoute().getId());
            List<RouteProgressResponse.StopInfo> stops = rStops.stream().map(rs -> RouteProgressResponse.StopInfo.builder()
                    .stopId(rs.getStop().getId())
                    .stopName(rs.getStop().getStopName())
                    .latitude(rs.getStop().getLatitude().doubleValue())
                    .longitude(rs.getStop().getLongitude().doubleValue())
                    .sequence(rs.getSequenceNumber())
                    .arrivalTime(rs.getExpectedArrivalTime() != null ? rs.getExpectedArrivalTime().toString() : null)
                    .departureTime(rs.getExpectedDepartureTime() != null ? rs.getExpectedDepartureTime().toString() : null)
                    .build()).collect(Collectors.toList());

            String driverName = s.getDriver() != null && s.getDriver().getUser() != null
                    ? s.getDriver().getUser().getFirstName() + " " + s.getDriver().getUser().getLastName()
                    : "Assigned Driver";

            result.add(StudentScheduleItem.builder()
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
                    .estimatedDurationMins(s.getRoute().getEstimatedDurationMins())
                    .daysOfWeek(s.getDaysOfWeek())
                    .scheduleStatus(s.getStatus())
                    .tripStatus(tripStatus)
                    .activeTripId(activeTripId)
                    .driverName(driverName)
                    .stops(stops)
                    .build());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stops/nearby")
    public ResponseEntity<List<NearbyStopResponse>> getNearbyStops() {
        Student student = getAuthenticatedStudent();
        List<Stop> stops = student.getCollege() != null
                ? stopRepository.findByCollegeId(student.getCollege().getId())
                : stopRepository.findAll();
        
        if (student.getHomeLatitude() == null || student.getHomeLongitude() == null) {
            // If no home location set, return default empty list
            return ResponseEntity.ok(Collections.emptyList());
        }

        double homeLat = student.getHomeLatitude();
        double homeLng = student.getHomeLongitude();

        List<NearbyStopResponse> response = stops.stream().map(s -> {
            double distance = geofencingService.calculateDistance(homeLat, homeLng, s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
            return NearbyStopResponse.builder()
                    .stopId(s.getId())
                    .stopName(s.getStopName())
                    .latitude(s.getLatitude().doubleValue())
                    .longitude(s.getLongitude().doubleValue())
                    .distanceMeters(distance)
                    .build();
        }).sorted(Comparator.comparing(NearbyStopResponse::getDistanceMeters))
          .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    private StudentBusDetails mapToStudentBusDetails(Bus bus) {
        Optional<Trip> activeTrip = tripRepository.findByBusIdAndStatusIn(bus.getId(), Arrays.asList("IN_PROGRESS", "PAUSED"));
        
        String currentStop = "N/A";
        String nextStop = "N/A";
        int eta = -1;
        String trackingSource = "GPS_DEVICE";
        String driverName = "N/A";
        String routeName = "N/A";
        String status = "NOT_ACTIVE";

        if (activeTrip.isPresent()) {
            Trip trip = activeTrip.get();
            status = "LIVE";
            driverName = trip.getDriver().getUser().getFirstName() + " " + trip.getDriver().getUser().getLastName();
            routeName = trip.getRoute().getRouteName();
            trackingSource = trip.getPrimaryTrackingSource() != null ? trip.getPrimaryTrackingSource() : "GPS_DEVICE";
            
            // stops sequences
            List<RouteStop> rStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(trip.getRoute().getId());
            RouteStop closest = null;
            double minDist = Double.MAX_VALUE;
            if (bus.getCurrentLatitude() != null && bus.getCurrentLongitude() != null) {
                for (RouteStop rs : rStops) {
                    double dist = geofencingService.calculateDistance(bus.getCurrentLatitude(), bus.getCurrentLongitude(), rs.getStop().getLatitude(), rs.getStop().getLongitude());
                    if (dist < minDist) {
                        minDist = dist;
                        closest = rs;
                    }
                }
            }

            if (closest != null) {
                currentStop = closest.getStop().getStopName();
                final RouteStop finalClosest = closest;
                Optional<RouteStop> nextOpt = rStops.stream()
                        .filter(rs -> rs.getSequenceNumber() == finalClosest.getSequenceNumber() + 1)
                        .findFirst();
                if (nextOpt.isPresent()) {
                    nextStop = nextOpt.get().getStop().getStopName();
                    eta = etaService.calculateEtaMinutes(trip.getId(), nextOpt.get().getStop().getId());
                }
            }
        } else {
            // Find scheduled route
            List<Schedule> schedules = scheduleRepository.findByBusIdAndDeletedAtIsNull(bus.getId());
            if (!schedules.isEmpty()) {
                Schedule s = schedules.get(0);
                routeName = s.getRoute().getRouteName();
                driverName = s.getDriver().getUser().getFirstName() + " " + s.getDriver().getUser().getLastName();
                status = "SCHEDULED";
            }
        }

        Route r = activeTrip.map(Trip::getRoute).orElseGet(() -> {
            List<Schedule> scheds = scheduleRepository.findByBusIdAndDeletedAtIsNull(bus.getId());
            return !scheds.isEmpty() ? scheds.get(0).getRoute() : null;
        });

        return StudentBusDetails.builder()
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busCode(bus.getBusCode())
                .registrationNumber(bus.getRegistrationNumber())
                .manufacturer(bus.getManufacturer())
                .model(bus.getModel())
                .busType(bus.getBusType())
                .capacity(bus.getCapacity())
                .availableSeats(-1) // PASSENGER COUNT telemetry not active
                .status(status)
                .driverName(driverName)
                .routeName(routeName)
                .currentStop(currentStop)
                .nextStop(nextStop)
                .eta(eta)
                .gpsDeviceOnline(bus.getGpsDevice() != null && "ONLINE".equalsIgnoreCase(bus.getGpsDevice().getStatus()))
                .trackingSource(trackingSource)
                .routeId(r != null ? r.getId() : null)
                .startLatitude(r != null && r.getStartLatitude() != null ? r.getStartLatitude().doubleValue() : null)
                .startLongitude(r != null && r.getStartLongitude() != null ? r.getStartLongitude().doubleValue() : null)
                .endLatitude(r != null && r.getEndLatitude() != null ? r.getEndLatitude().doubleValue() : null)
                .endLongitude(r != null && r.getEndLongitude() != null ? r.getEndLongitude().doubleValue() : null)
                .polyline(r != null ? r.getPolyline() : null)
                .build();
    }

    private StudentProfileResponse mapToStudentProfileResponse(Student s) {
        UUID collegeId = s.getCollege() != null ? s.getCollege().getId() : (s.getUser().getCollege() != null ? s.getUser().getCollege().getId() : null);
        String collegeName = s.getCollege() != null ? s.getCollege().getName() : (s.getUser().getCollege() != null ? s.getUser().getCollege().getName() : null);
        String collegeCode = s.getCollege() != null ? s.getCollege().getCollegeCode() : (s.getUser().getCollege() != null ? s.getUser().getCollege().getCollegeCode() : null);

        return StudentProfileResponse.builder()
                .id(s.getId())
                .userId(s.getUser().getId())
                .collegeEmail(s.getUser().getEmail())
                .collegeId(collegeId)
                .collegeName(collegeName)
                .collegeCode(collegeCode)
                .name(s.getUser().getFirstName() + " " + s.getUser().getLastName())
                .registerNumber(s.getRegisterNumber() != null ? s.getRegisterNumber() : s.getStudentId())
                .department(s.getDepartment())
                .batch(s.getBatch())
                .phone(s.getUser().getPhoneNumber())
                .homeLatitude(s.getHomeLatitude())
                .homeLongitude(s.getHomeLongitude())
                .homeAddress(s.getHomeAddress())
                .preferredRouteId(s.getPreferredRoute() != null ? s.getPreferredRoute().getId() : null)
                .preferredRouteName(s.getPreferredRoute() != null ? s.getPreferredRoute().getRouteName() : null)
                .preferredBusId(s.getPreferredBus() != null ? s.getPreferredBus().getId() : null)
                .preferredBusNumber(s.getPreferredBus() != null ? s.getPreferredBus().getBusNumber() : null)
                .preferredStopId(s.getPreferredStop() != null ? s.getPreferredStop().getId() : null)
                .preferredStopName(s.getPreferredStop() != null ? s.getPreferredStop().getStopName() : null)
                .homeStopId(s.getPreferredStop() != null ? s.getPreferredStop().getId() : null)
                .homeStopName(s.getPreferredStop() != null ? s.getPreferredStop().getStopName() : null)
                .notificationPreferences(s.getNotificationPreferences() != null ? s.getNotificationPreferences() : "APPROACHING,ARRIVED,DEPARTED,DELAYED")
                .build();
    }

    // --- DTO Static Definition Classes ---

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentDashboardResponse {
        private String greeting;
        private String homeStop;
        private ActiveBusSummary yourBus;
        private Integer homeEta;
        private List<OtherBusSummary> otherBuses;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ActiveBusSummary {
        private UUID busId;
        private String busNumber;
        private String busCode;
        private String routeName;
        private String driverName;
        private String currentStop;
        private String nextStop;
        private Integer eta;
        private String status;
        private String etaStatus; // ON_TIME, ARRIVING, ARRIVED, DELAYED, OFF_ROUTE, GPS_STALE, ETA_UNAVAILABLE
        private Double distanceMeters;
        private String estimatedArrivalTime;
        private boolean offRoute;
        private boolean gpsStale;
        private String gpsStatus; // LIVE, GPS_STALE, UNAVAILABLE
        private Double routeDeviationMeters;
        private Integer delayMinutes;
        private String trackingSource;
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double heading;
        private String lastUpdated;
        private java.util.List<com.smartbus.infrastructure.dto.EtaResponse.UpcomingStopEta> upcomingStops;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OtherBusSummary {
        private UUID busId;
        private String busNumber;
        private String busCode;
        private String routeName;
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StopInfo {
        private UUID stopId;
        private String stopName;
        private Integer sequence;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentBusDetails {
        private UUID busId;
        private String busNumber;
        private String busCode;
        private String registrationNumber;
        private String manufacturer;
        private String model;
        private String busType;
        private Integer capacity;
        private Integer availableSeats;
        private String status; // LIVE, SCHEDULED, NOT_ACTIVE
        private String driverName;
        private String routeName;
        private String currentStop;
        private String nextStop;
        private Integer eta;
        private boolean gpsDeviceOnline;
        private String trackingSource;
        private UUID routeId;
        private Double startLatitude;
        private Double startLongitude;
        private Double endLatitude;
        private Double endLongitude;
        private String polyline;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentRouteDetails {
        private UUID routeId;
        private String routeName;
        private String startPoint;
        private String endPoint;
        private Double distance;
        private Integer estimatedDurationMins;
        private Double startLatitude;
        private Double startLongitude;
        private Double endLatitude;
        private Double endLongitude;
        private String polyline;
        private List<RouteProgressResponse.StopInfo> stops;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LiveBusLocationResponse {
        private UUID busId;
        private String busNumber;
        private String busCode;
        private UUID tripId;
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double heading;
        private String trackingSource;
        private String lastUpdate;
        private String currentStop;
        private String nextStop;
        private Integer eta;
        private String etaStatus;
        private Double distanceMeters;
        private String estimatedArrivalTime;
        private boolean offRoute;
        private boolean gpsStale;
        private String gpsStatus; // LIVE, GPS_STALE, UNAVAILABLE
        private Double routeDeviationMeters;
        private Integer delayMinutes;
        private java.util.List<com.smartbus.infrastructure.dto.EtaResponse.UpcomingStopEta> upcomingStops;
        private com.smartbus.infrastructure.dto.EtaResponse.RouteProgressInfo routeProgress;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentProfileResponse {
        private UUID id;
        private UUID userId;
        private String collegeEmail;
        private UUID collegeId;
        private String collegeName;
        private String collegeCode;
        private String name;
        private String registerNumber;
        private String department;
        private String batch;
        private String phone;
        private Double homeLatitude;
        private Double homeLongitude;
        private String homeAddress;
        private UUID preferredRouteId;
        private String preferredRouteName;
        private UUID preferredBusId;
        private String preferredBusNumber;
        private UUID preferredStopId;
        private String preferredStopName;
        private UUID homeStopId;
        private String homeStopName;
        private String notificationPreferences;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentPreferencesResponse {
        private UUID preferredRouteId;
        private String preferredRouteName;
        private UUID preferredBusId;
        private String preferredBusNumber;
        private UUID preferredStopId;
        private String preferredStopName;
        private UUID homeStopId;
        private String homeStopName;
        private Double homeLatitude;
        private Double homeLongitude;
        private String homeAddress;
        private String notificationPreferences;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentPreferencesRequest {
        private UUID routeId;
        private UUID preferredRouteId;
        private UUID busId;
        private UUID preferredBusId;
        private UUID stopId;
        private UUID preferredStopId;
        private UUID homeStopId;
        private Double homeLatitude;
        private Double homeLongitude;
        private String homeAddress;
        private String notificationPreferences;

        public UUID getResolvedRouteId() {
            return routeId != null ? routeId : preferredRouteId;
        }

        public UUID getResolvedBusId() {
            return busId != null ? busId : preferredBusId;
        }

        public UUID getResolvedStopId() {
            if (stopId != null) return stopId;
            if (preferredStopId != null) return preferredStopId;
            return homeStopId;
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentScheduleItem {
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
        private Integer estimatedDurationMins;
        private String daysOfWeek;
        private String scheduleStatus; // ACTIVE, INACTIVE
        private String tripStatus; // LIVE, PAUSED, SCHEDULED, COMPLETED
        private UUID activeTripId;
        private String driverName;
        private List<RouteProgressResponse.StopInfo> stops;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class HomeLocationRequest {
        private Double latitude;
        private Double longitude;
        private String address;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class NearbyStopResponse {
        private UUID stopId;
        private String stopName;
        private Double latitude;
        private Double longitude;
        private Double distanceMeters;
    }
}
