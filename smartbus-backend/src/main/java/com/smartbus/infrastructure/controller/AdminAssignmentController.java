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
import com.smartbus.infrastructure.adapter.jpa.BusAssignmentRepository;
import com.smartbus.infrastructure.adapter.jpa.BusRepository;
import com.smartbus.infrastructure.adapter.jpa.DriverRepository;
import com.smartbus.infrastructure.adapter.jpa.RouteRepository;
import com.smartbus.infrastructure.adapter.jpa.ScheduleRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.AssignmentDto;
import com.smartbus.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/assignments")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminAssignmentController {

    private final BusAssignmentRepository busAssignmentRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final ScheduleRepository scheduleRepository;
    private final com.smartbus.infrastructure.adapter.jpa.TripRepository tripRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    @Transactional
    public ResponseEntity<ApiResponse<List<AssignmentDto>>> getAllAssignments() {
        List<BusAssignment> list = busAssignmentRepository.findAll();
        List<BusAssignment> validList = new ArrayList<>();
        List<BusAssignment> orphans = new ArrayList<>();

        for (BusAssignment a : list) {
            if (a.getSchedule() == null || a.getSchedule().getDeletedAt() != null || "RELEASED".equalsIgnoreCase(a.getStatus())) {
                orphans.add(a);
            } else {
                validList.add(a);
            }
        }

        if (!orphans.isEmpty()) {
            busAssignmentRepository.deleteAll(orphans);
            busAssignmentRepository.flush();
        }

        List<AssignmentDto> dtos = validList.stream().map(this::mapToDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Assignments retrieved", dtos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssignmentDto>> getAssignmentById(@PathVariable UUID id) {
        BusAssignment assignment = busAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found with id: " + id));
        return ResponseEntity.ok(ApiResponse.success("Assignment retrieved", mapToDto(assignment)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        BusAssignment assignment = busAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found with id: " + id));

        // Active trip safety check: throw 409 Conflict
        if (assignment.getSchedule() != null) {
            List<com.smartbus.domain.model.Trip> scheduleTrips = tripRepository.findByScheduleId(assignment.getSchedule().getId());
            boolean hasActiveTrip = scheduleTrips.stream().anyMatch(t ->
                    "IN_PROGRESS".equalsIgnoreCase(t.getStatus()) ||
                    "PAUSED".equalsIgnoreCase(t.getStatus()) ||
                    "EN_ROUTE".equalsIgnoreCase(t.getStatus())
            );
            if (hasActiveTrip) {
                throw new ConflictException("Cannot delete this assignment while its trip is active. Please end the trip first.");
            }
        }

        if (assignment.getBus() != null) {
            java.util.Optional<com.smartbus.domain.model.Trip> activeBusTrip = tripRepository.findByBusIdAndStatusIn(
                    assignment.getBus().getId(),
                    java.util.Arrays.asList("IN_PROGRESS", "PAUSED", "EN_ROUTE")
            );
            if (activeBusTrip.isPresent() && activeBusTrip.get().getSchedule() != null &&
                    assignment.getSchedule() != null &&
                    activeBusTrip.get().getSchedule().getId().equals(assignment.getSchedule().getId())) {
                throw new ConflictException("Cannot delete this assignment while its trip is active. Please end the trip first.");
            }
        }

        String busNumber = assignment.getBus() != null ? assignment.getBus().getBusNumber() : "N/A";
        String driverEmail = (assignment.getDriver() != null && assignment.getDriver().getUser() != null)
                ? assignment.getDriver().getUser().getEmail() : "N/A";
        String routeName = assignment.getRoute() != null ? assignment.getRoute().getRouteName() : "N/A";
        String scheduleId = assignment.getSchedule() != null ? assignment.getSchedule().getId().toString() : "N/A";

        // Audit Log
        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "ASSIGNMENT_DELETED",
                "Deleted Assignment " + assignment.getId() + " (Bus: " + busNumber + ", Driver: " + driverEmail + ", Route: " + routeName + ", Schedule: " + scheduleId + ")",
                "0.0.0.0",
                "BusAssignment",
                assignment.getId().toString(),
                busNumber + " -> " + routeName,
                "DELETED"
        );

        busAssignmentRepository.delete(assignment);
        busAssignmentRepository.flush();

        return ResponseEntity.ok(ApiResponse.success("Assignment deleted successfully", null));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssignmentDto>> updateAssignment(
            @PathVariable UUID id,
            @RequestBody AssignmentDto dto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        BusAssignment assignment = busAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found with id: " + id));

        if (dto.getBusId() != null) {
            Bus bus = busRepository.findById(dto.getBusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
            assignment.setBus(bus);
        }
        if (dto.getDriverId() != null) {
            Driver driver = driverRepository.findById(dto.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
            assignment.setDriver(driver);
        }
        if (dto.getRouteId() != null) {
            Route route = routeRepository.findById(dto.getRouteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
            assignment.setRoute(route);
        }
        if (dto.getStatus() != null) {
            assignment.setStatus(dto.getStatus().toUpperCase());
        }

        BusAssignment saved = busAssignmentRepository.save(assignment);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "ASSIGNMENT_UPDATED",
                "Updated assignment ID: " + id,
                "0.0.0.0",
                "BusAssignment",
                saved.getId().toString(),
                null,
                saved.getStatus()
        );

        return ResponseEntity.ok(ApiResponse.success("Assignment updated successfully", mapToDto(saved)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AssignmentDto>> createAssignment(
            @RequestBody AssignmentDto dto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Bus bus = busRepository.findById(dto.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));

        Driver driver = driverRepository.findById(dto.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));

        Route route = routeRepository.findById(dto.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        Schedule schedule = scheduleRepository.findById(dto.getScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));

        // Validation: Verify if schedule matches
        if (!schedule.getBus().getId().equals(bus.getId())) {
            throw new BadRequestException("Schedule bus does not match assigned bus.");
        }
        if (!schedule.getDriver().getId().equals(driver.getId())) {
            throw new BadRequestException("Schedule driver does not match assigned driver.");
        }
        if (!schedule.getRoute().getId().equals(route.getId())) {
            throw new BadRequestException("Schedule route does not match assigned route.");
        }

        // Validate entity states
        if ("INACTIVE".equalsIgnoreCase(bus.getStatus()) || "MAINTENANCE".equalsIgnoreCase(bus.getStatus())) {
            throw new BadRequestException("Selected bus status is " + bus.getStatus());
        }
        if (!"APPROVED".equalsIgnoreCase(driver.getApprovalStatus())) {
            throw new BadRequestException("Selected driver is not approved.");
        }
        if ("INACTIVE".equalsIgnoreCase(route.getStatus())) {
            throw new BadRequestException("Selected route is inactive.");
        }

        BusAssignment assignment = BusAssignment.builder()
                .bus(bus)
                .driver(driver)
                .route(route)
                .schedule(schedule)
                .status("ACTIVE")
                .build();

        BusAssignment saved = busAssignmentRepository.save(assignment);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "ASSIGNMENT_CREATED",
                "Assigned Bus " + bus.getBusNumber() + " and Driver " + driver.getUser().getEmail() + " to Schedule " + schedule.getId(),
                "0.0.0.0",
                "BusAssignment",
                saved.getId().toString(),
                null,
                bus.getBusNumber()
        );

        return ResponseEntity.ok(ApiResponse.success("Assignment confirmed successfully", mapToDto(saved)));
    }

    private AssignmentDto mapToDto(BusAssignment a) {
        return AssignmentDto.builder()
                .id(a.getId())
                .busId(a.getBus().getId())
                .busNumber(a.getBus().getBusNumber())
                .driverId(a.getDriver().getId())
                .driverName(a.getDriver().getUser().getFirstName() + " " + a.getDriver().getUser().getLastName())
                .routeId(a.getRoute().getId())
                .routeName(a.getRoute().getRouteName())
                .scheduleId(a.getSchedule().getId())
                .departureTime(a.getSchedule().getDepartureTime().toString())
                .assignedAt(a.getAssignedAt())
                .status(a.getStatus())
                .build();
    }
}
