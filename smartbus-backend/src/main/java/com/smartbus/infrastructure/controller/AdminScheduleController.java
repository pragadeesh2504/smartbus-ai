package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuditLogService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ConflictException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Bus;
import com.smartbus.domain.model.BusAssignment;
import com.smartbus.domain.model.Driver;
import com.smartbus.domain.model.Route;
import com.smartbus.domain.model.Schedule;
import com.smartbus.domain.model.Trip;
import com.smartbus.infrastructure.adapter.jpa.BusAssignmentRepository;
import com.smartbus.infrastructure.adapter.jpa.BusRepository;
import com.smartbus.infrastructure.adapter.jpa.DriverRepository;
import com.smartbus.infrastructure.adapter.jpa.RouteRepository;
import com.smartbus.infrastructure.adapter.jpa.ScheduleRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.ScheduleDto;
import com.smartbus.infrastructure.mapper.ScheduleMapper;
import com.smartbus.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/schedules")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminScheduleController {

    private final ScheduleRepository scheduleRepository;
    private final BusAssignmentRepository busAssignmentRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final AuditLogService auditLogService;
    private final ScheduleMapper scheduleMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ScheduleDto>>> getAllSchedules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "departureTime") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Schedule> schedulesPage;
        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            schedulesPage = scheduleRepository.findByCollegeIdAndDeletedAtIsNull(userPrincipal.getCollegeId(), pageable);
        } else {
            schedulesPage = scheduleRepository.findByDeletedAtIsNull(pageable);
        }
        Page<ScheduleDto> dtosPage = schedulesPage.map(scheduleMapper::toDto);

        return ResponseEntity.ok(ApiResponse.success("Schedules loaded successfully", dtosPage));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduleDto>> getScheduleById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Schedule schedule = scheduleRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + id));
        validateScheduleBelongsToCollege(schedule, userPrincipal);
        return ResponseEntity.ok(ApiResponse.success("Schedule loaded", scheduleMapper.toDto(schedule)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleDto>> createSchedule(
            @RequestBody ScheduleDto scheduleDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Schedule schedule = validateAndBuildSchedule(null, scheduleDto, userPrincipal);
        Schedule saved = scheduleRepository.save(schedule);

        // Synchronize BusAssignment
        if ("ACTIVE".equalsIgnoreCase(saved.getStatus()) && saved.getBus() != null && saved.getDriver() != null && saved.getRoute() != null) {
            Optional<BusAssignment> existingAssignment = busAssignmentRepository.findFirstByScheduleId(saved.getId());
            if (existingAssignment.isEmpty()) {
                BusAssignment assignment = BusAssignment.builder()
                        .bus(saved.getBus())
                        .driver(saved.getDriver())
                        .route(saved.getRoute())
                        .schedule(saved)
                        .college(saved.getCollege())
                        .status("ACTIVE")
                        .build();
                busAssignmentRepository.save(assignment);
            }
        }

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "SCHEDULE_CREATED",
                "Created schedule: Route " + saved.getRoute().getRouteName() + ", Bus " + saved.getBus().getBusNumber() + ", Driver " + saved.getDriver().getUser().getEmail(),
                "0.0.0.0",
                "Schedule",
                saved.getId().toString(),
                null,
                saved.getDepartureTime().toString()
        );

        return ResponseEntity.ok(ApiResponse.success("Schedule created successfully", scheduleMapper.toDto(saved)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduleDto>> updateSchedule(
            @PathVariable UUID id,
            @RequestBody ScheduleDto scheduleDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Schedule existing = scheduleRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + id));
        validateScheduleBelongsToCollege(existing, userPrincipal);

        Schedule updated = validateAndBuildSchedule(id, scheduleDto, userPrincipal);
        updated.setId(id);
        Schedule saved = scheduleRepository.save(updated);

        // Synchronize BusAssignment
        Optional<BusAssignment> existingAssignment = busAssignmentRepository.findFirstByScheduleId(saved.getId());
        if (existingAssignment.isPresent()) {
            BusAssignment a = existingAssignment.get();
            a.setBus(saved.getBus());
            a.setDriver(saved.getDriver());
            a.setRoute(saved.getRoute());
            a.setCollege(saved.getCollege());
            a.setStatus("ACTIVE".equalsIgnoreCase(saved.getStatus()) ? "ACTIVE" : "RELEASED");
            busAssignmentRepository.save(a);
        } else if ("ACTIVE".equalsIgnoreCase(saved.getStatus())) {
            BusAssignment assignment = BusAssignment.builder()
                    .bus(saved.getBus())
                    .driver(saved.getDriver())
                    .route(saved.getRoute())
                    .schedule(saved)
                    .college(saved.getCollege())
                    .status("ACTIVE")
                    .build();
            busAssignmentRepository.save(assignment);
        }

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "SCHEDULE_UPDATED",
                "Updated schedule ID: " + id,
                "0.0.0.0",
                "Schedule",
                saved.getId().toString(),
                existing.getDepartureTime().toString(),
                saved.getDepartureTime().toString()
        );

        return ResponseEntity.ok(ApiResponse.success("Schedule updated successfully", scheduleMapper.toDto(saved)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ScheduleDto>> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Schedule schedule = scheduleRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + id));
        validateScheduleBelongsToCollege(schedule, userPrincipal);

        String oldStatus = schedule.getStatus();
        schedule.setStatus(status.toUpperCase());
        Schedule saved = scheduleRepository.save(schedule);

        // Synchronize BusAssignment
        Optional<BusAssignment> existingAssignment = busAssignmentRepository.findFirstByScheduleId(saved.getId());
        if (existingAssignment.isPresent()) {
            BusAssignment a = existingAssignment.get();
            a.setCollege(saved.getCollege());
            a.setStatus("ACTIVE".equalsIgnoreCase(status) ? "ACTIVE" : "RELEASED");
            busAssignmentRepository.save(a);
        } else if ("ACTIVE".equalsIgnoreCase(status)) {
            BusAssignment assignment = BusAssignment.builder()
                    .bus(saved.getBus())
                    .driver(saved.getDriver())
                    .route(saved.getRoute())
                    .schedule(saved)
                    .college(saved.getCollege())
                    .status("ACTIVE")
                    .build();
            busAssignmentRepository.save(assignment);
        }

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "SCHEDULE_CANCELLED".equalsIgnoreCase(status) ? "SCHEDULE_CANCELLED" : "SCHEDULE_STATUS_CHANGED",
                "Changed status of schedule " + id + " to " + status,
                "0.0.0.0",
                "Schedule",
                schedule.getId().toString(),
                oldStatus,
                status
        );

        return ResponseEntity.ok(ApiResponse.success("Schedule status updated to " + status, scheduleMapper.toDto(saved)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Schedule schedule = scheduleRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + id));
        validateScheduleBelongsToCollege(schedule, userPrincipal);

        // 1. Active Trip Protection: return 409 Conflict if active
        List<Trip> scheduleTrips = tripRepository.findByScheduleId(id);
        boolean hasActiveTrip = scheduleTrips.stream().anyMatch(t ->
                "IN_PROGRESS".equalsIgnoreCase(t.getStatus()) ||
                "PAUSED".equalsIgnoreCase(t.getStatus()) ||
                "EN_ROUTE".equalsIgnoreCase(t.getStatus())
        );
        if (hasActiveTrip) {
            throw new ConflictException("Cannot delete this schedule because its assigned trip is currently active. Please end the trip first.");
        }

        if (schedule.getBus() != null) {
            Optional<Trip> activeBusTrip = tripRepository.findByBusIdAndStatusIn(
                    schedule.getBus().getId(),
                    Arrays.asList("IN_PROGRESS", "PAUSED", "EN_ROUTE")
            );
            if (activeBusTrip.isPresent() && activeBusTrip.get().getSchedule() != null &&
                    activeBusTrip.get().getSchedule().getId().equals(schedule.getId())) {
                throw new ConflictException("Cannot delete this schedule because its assigned trip is currently active. Please end the trip first.");
            }
        }

        // 2. Atomic removal of dependent BusAssignment(s)
        List<BusAssignment> dependentAssignments = busAssignmentRepository.findByScheduleId(id);
        for (BusAssignment a : dependentAssignments) {
            String busNum = a.getBus() != null ? a.getBus().getBusNumber() : "N/A";
            String driverEmail = (a.getDriver() != null && a.getDriver().getUser() != null) ? a.getDriver().getUser().getEmail() : "N/A";
            String routeName = a.getRoute() != null ? a.getRoute().getRouteName() : "N/A";

            auditLogService.logAction(
                    userPrincipal != null ? userPrincipal.getUser() : null,
                    "ASSIGNMENT_DELETED",
                    "Deleted dependent assignment " + a.getId() + " upon schedule deletion " + id + " (Bus: " + busNum + ", Driver: " + driverEmail + ", Route: " + routeName + ")",
                    "0.0.0.0",
                    "BusAssignment",
                    a.getId().toString(),
                    a.getStatus(),
                    "DELETED"
            );
            busAssignmentRepository.delete(a);
        }
        busAssignmentRepository.flush();

        // 3. Mark schedule deleted so foreign keys from historical trips remain valid
        schedule.setDeletedAt(java.time.LocalDateTime.now());
        schedule.setStatus("INACTIVE");
        scheduleRepository.save(schedule);
        scheduleRepository.flush();

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "SCHEDULE_DELETED",
                "Deleted schedule " + id + " for route " + (schedule.getRoute() != null ? schedule.getRoute().getRouteName() : "N/A") + " and removed " + dependentAssignments.size() + " dependent assignment(s)",
                "0.0.0.0",
                "Schedule",
                id.toString(),
                "ACTIVE",
                "DELETED"
        );

        return ResponseEntity.ok(ApiResponse.success("Schedule and dependent assignments deleted successfully", null));
    }

    private Schedule validateAndBuildSchedule(UUID editId, ScheduleDto dto, UserPrincipal userPrincipal) {
        Bus bus = busRepository.findById(dto.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));

        Driver driver = driverRepository.findById(dto.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));

        Route route = routeRepository.findById(dto.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        // Tenant ownership and cross-college isolation validation
        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            UUID adminCollegeId = userPrincipal.getCollegeId();
            if (bus.getCollege() == null || !adminCollegeId.equals(bus.getCollege().getId())) {
                throw new BadRequestException("Bus does not belong to your college.");
            }
            if (driver.getCollege() == null || !adminCollegeId.equals(driver.getCollege().getId())) {
                throw new BadRequestException("Driver does not belong to your college.");
            }
            if (route.getCollege() == null || !adminCollegeId.equals(route.getCollege().getId())) {
                throw new BadRequestException("Route does not belong to your college.");
            }
        }

        // Cross-college combination safety check
        if (bus.getCollege() != null && driver.getCollege() != null && !bus.getCollege().getId().equals(driver.getCollege().getId())) {
            throw new BadRequestException("Cross-college scheduling is not permitted: Bus and Driver belong to different colleges.");
        }
        if (bus.getCollege() != null && route.getCollege() != null && !bus.getCollege().getId().equals(route.getCollege().getId())) {
            throw new BadRequestException("Cross-college scheduling is not permitted: Bus and Route belong to different colleges.");
        }
        if (driver.getCollege() != null && route.getCollege() != null && !driver.getCollege().getId().equals(route.getCollege().getId())) {
            throw new BadRequestException("Cross-college scheduling is not permitted: Driver and Route belong to different colleges.");
        }

        com.smartbus.domain.model.College college = bus.getCollege() != null ? bus.getCollege() : (userPrincipal != null ? userPrincipal.getUser().getCollege() : null);

        // Entity status validations
        if ("INACTIVE".equalsIgnoreCase(bus.getStatus()) || "MAINTENANCE".equalsIgnoreCase(bus.getStatus())) {
            throw new BadRequestException("Bus " + bus.getBusNumber() + " is currently " + bus.getStatus().toUpperCase() + " and cannot be scheduled.");
        }

        if ("INACTIVE".equalsIgnoreCase(route.getStatus())) {
            throw new BadRequestException("Route " + route.getRouteName() + " is currently INACTIVE.");
        }

        if (!"APPROVED".equalsIgnoreCase(driver.getApprovalStatus())) {
            throw new BadRequestException("Driver " + driver.getUser().getEmail() + " is currently unapproved (Status: " + driver.getApprovalStatus() + ").");
        }

        if ("SUSPENDED".equalsIgnoreCase(driver.getStatus())) {
            throw new BadRequestException("Driver " + driver.getUser().getEmail() + " is currently SUSPENDED.");
        }

        LocalTime newDep = LocalTime.parse(dto.getDepartureTime());
        LocalTime newArr = LocalTime.parse(dto.getArrivalTime());

        if (newDep.isAfter(newArr)) {
            throw new BadRequestException("Departure time cannot be after expected arrival time.");
        }

        Set<String> newDays = Arrays.stream(dto.getDaysOfWeek().split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        // Check Bus Overlaps
        List<Schedule> busSchedules = scheduleRepository.findByBusIdAndDeletedAtIsNull(dto.getBusId());
        for (Schedule s : busSchedules) {
            if (editId != null && s.getId().equals(editId)) {
                continue;
            }
            if (hasDayAndTimeOverlap(s, newDays, newDep, newArr)) {
                throw new BadRequestException("Bus " + bus.getBusNumber() + " is already assigned to another trip from " + s.getDepartureTime() + " to " + s.getArrivalTime() + " on matching days.");
            }
        }

        // Check Driver Overlaps
        List<Schedule> driverSchedules = scheduleRepository.findByDriverIdAndDeletedAtIsNull(dto.getDriverId());
        for (Schedule s : driverSchedules) {
            if (editId != null && s.getId().equals(editId)) {
                continue;
            }
            if (hasDayAndTimeOverlap(s, newDays, newDep, newArr)) {
                throw new BadRequestException("Driver is already assigned to another trip from " + s.getDepartureTime() + " to " + s.getArrivalTime() + ".");
            }
        }

        return Schedule.builder()
                .bus(bus)
                .driver(driver)
                .route(route)
                .college(college)
                .departureTime(newDep)
                .arrivalTime(newArr)
                .daysOfWeek(dto.getDaysOfWeek())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status(dto.getStatus() != null ? dto.getStatus().toUpperCase() : "ACTIVE")
                .build();
    }

    private boolean hasDayAndTimeOverlap(Schedule s, Set<String> newDays, LocalTime newDep, LocalTime newArr) {
        Set<String> existingDays = Arrays.stream(s.getDaysOfWeek().split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        boolean shareDay = existingDays.stream().anyMatch(newDays::contains);
        if (!shareDay) {
            return false;
        }

        // Time overlap: A start is before B end, and A end is after B start
        return newDep.isBefore(s.getArrivalTime()) && newArr.isAfter(s.getDepartureTime());
    }

    private void validateScheduleBelongsToCollege(Schedule schedule, UserPrincipal userPrincipal) {
        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            if (schedule.getCollege() == null || !userPrincipal.getCollegeId().equals(schedule.getCollege().getId())) {
                throw new ResourceNotFoundException("Schedule not found with id: " + schedule.getId());
            }
        }
    }
}
