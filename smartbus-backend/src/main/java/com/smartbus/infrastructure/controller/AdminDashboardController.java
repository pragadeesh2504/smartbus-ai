package com.smartbus.infrastructure.controller;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.AuditLogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final ScheduleRepository scheduleRepository;
    private final GpsDeviceRepository gpsDeviceRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // Buses
        long totalBuses = busRepository.findByDeletedAtIsNull().size();
        long activeBuses = busRepository.findByStatusAndDeletedAtIsNull("ACTIVE").size();
        long maintenanceBuses = busRepository.findByStatusAndDeletedAtIsNull("MAINTENANCE").size();

        // Drivers
        long totalDrivers = driverRepository.countActiveDrivers();
        long approvedDrivers = driverRepository.countApprovedActiveDrivers();

        // Routes and Stops
        long totalRoutes = routeRepository.findByDeletedAtIsNull().size();
        long totalStops = stopRepository.count();

        // Schedules
        long todaySchedules = scheduleRepository.findByDeletedAtIsNull().size();
        long activeSchedules = scheduleRepository.findByDeletedAtIsNull().stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus()))
                .count();

        // GPS
        long offlineGps = gpsDeviceRepository.findAll().stream()
                .filter(d -> "OFFLINE".equalsIgnoreCase(d.getStatus()))
                .count();

        // Recent Audit Logs
        List<AuditLogDto> recentLogs = auditLogRepository.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent().stream().map(log -> AuditLogDto.builder()
                .id(log.getId())
                .userEmail(log.getUser() != null ? log.getUser().getEmail() : "System")
                .action(log.getAction())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .entityName(log.getEntityName())
                .entityId(log.getEntityId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .build()).collect(Collectors.toList());

        stats.put("totalBuses", totalBuses);
        stats.put("activeBuses", activeBuses);
        stats.put("maintenanceBuses", maintenanceBuses);
        stats.put("totalDrivers", totalDrivers);
        stats.put("approvedDrivers", approvedDrivers);
        stats.put("totalRoutes", totalRoutes);
        stats.put("totalStops", totalStops);
        stats.put("todaySchedules", todaySchedules);
        stats.put("activeSchedules", activeSchedules);
        stats.put("offlineGps", offlineGps);
        stats.put("recentLogs", recentLogs);

        return ResponseEntity.ok(ApiResponse.success("Dashboard metrics loaded successfully", stats));
    }
}
