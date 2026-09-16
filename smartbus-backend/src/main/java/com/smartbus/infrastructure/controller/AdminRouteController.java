package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuditLogService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Route;
import com.smartbus.domain.model.RouteStop;
import com.smartbus.domain.model.Schedule;
import com.smartbus.infrastructure.adapter.jpa.RouteRepository;
import com.smartbus.infrastructure.adapter.jpa.RouteStopRepository;
import com.smartbus.infrastructure.adapter.jpa.ScheduleRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.RouteDto;
import com.smartbus.infrastructure.dto.RouteStopDto;
import com.smartbus.infrastructure.mapper.RouteMapper;
import com.smartbus.infrastructure.mapper.RouteStopMapper;
import com.smartbus.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.smartbus.domain.model.Stop;
import com.smartbus.infrastructure.adapter.jpa.StopRepository;
import com.smartbus.infrastructure.dto.StopDto;
import com.smartbus.websocket.LiveLocationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminRouteController {

    private final RouteRepository routeRepository;
    private final ScheduleRepository scheduleRepository;
    private final AuditLogService auditLogService;
    private final RouteMapper routeMapper;
    private final RouteStopRepository routeStopRepository;
    private final RouteStopMapper routeStopMapper;

    @Autowired(required = false)
    private StopRepository stopRepository;

    @Autowired(required = false)
    private LiveLocationWebSocketHandler webSocketHandler;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<RouteDto>>> getAllRoutes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "routeName") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        List<Route> allRoutes = routeRepository.findByDeletedAtIsNull();

        if (search != null && !search.trim().isEmpty()) {
            String lowerSearch = search.toLowerCase();
            allRoutes = allRoutes.stream()
                    .filter(r -> r.getRouteName().toLowerCase().contains(lowerSearch) ||
                            r.getStartPoint().toLowerCase().contains(lowerSearch) ||
                            r.getEndPoint().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        }

        if (status != null && !status.trim().isEmpty()) {
            allRoutes = allRoutes.stream()
                    .filter(r -> status.equalsIgnoreCase(r.getStatus()))
                    .collect(Collectors.toList());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allRoutes.size());

        List<RouteDto> content = new ArrayList<>();
        if (start <= allRoutes.size()) {
            content = allRoutes.subList(start, end).stream()
                    .map(routeMapper::toDto)
                    .collect(Collectors.toList());
        }

        Page<RouteDto> pageResult = new PageImpl<>(content, pageable, allRoutes.size());
        return ResponseEntity.ok(ApiResponse.success("Routes retrieved successfully", pageResult));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RouteDto>> createRoute(
            @RequestBody RouteDto routeDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        if (routeRepository.findByRouteNameAndDeletedAtIsNull(routeDto.getRouteName()).isPresent()) {
            throw new BadRequestException("Route name " + routeDto.getRouteName() + " already exists");
        }

        Route route = Route.builder()
                .routeName(routeDto.getRouteName())
                .startPoint(routeDto.getStartPoint())
                .endPoint(routeDto.getEndPoint())
                .distance(routeDto.getDistance() > 0 ? routeDto.getDistance() : 0.0)
                .estimatedDurationMins(routeDto.getEstimatedDurationMins() > 0 ? routeDto.getEstimatedDurationMins() : 0)
                .status("ACTIVE")
                .startLatitude(routeDto.getStartLatitude())
                .startLongitude(routeDto.getStartLongitude())
                .endLatitude(routeDto.getEndLatitude())
                .endLongitude(routeDto.getEndLongitude())
                .polyline(routeDto.getPolyline())
                .build();

        Route savedRoute = routeRepository.save(route);

        // Atomic stops synchronization if stops are passed in payload
        if (routeDto.getStops() != null && stopRepository != null) {
            int seq = 1;
            for (RouteStopDto sDto : routeDto.getStops()) {
                StopDto stopDto = sDto.getStop();
                if (stopDto != null && stopDto.getStopName() != null && !stopDto.getStopName().trim().isEmpty()) {
                    Stop stop = stopRepository.findByStopName(stopDto.getStopName().trim())
                            .orElseGet(() -> stopRepository.save(Stop.builder()
                                    .stopName(stopDto.getStopName().trim())
                                    .latitude(stopDto.getLatitude())
                                    .longitude(stopDto.getLongitude())
                                    .build()));
                    if (stopDto.getLatitude() != null && stopDto.getLongitude() != null) {
                        stop.setLatitude(stopDto.getLatitude());
                        stop.setLongitude(stopDto.getLongitude());
                        stopRepository.save(stop);
                    }

                    RouteStop rs = RouteStop.builder()
                            .route(savedRoute)
                            .stop(stop)
                            .sequenceNumber(sDto.getSequenceNumber() > 0 ? sDto.getSequenceNumber() : seq++)
                            .distanceFromStart(sDto.getDistanceFromStart() != null ? sDto.getDistanceFromStart() : 0.0)
                            .durationFromStartMins(sDto.getDurationFromStartMins())
                            .expectedArrivalTime(sDto.getExpectedArrivalTime())
                            .expectedDepartureTime(sDto.getExpectedDepartureTime())
                            .build();
                    routeStopRepository.save(rs);
                }
            }
        }

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "ROUTE_CREATED",
                "Created route: " + savedRoute.getRouteName(),
                "0.0.0.0",
                "Route",
                savedRoute.getId().toString(),
                null,
                savedRoute.getRouteName()
        );

        RouteDto resultDto = routeMapper.toDto(savedRoute);
        List<RouteStop> updatedStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(savedRoute.getId());
        resultDto.setStops(updatedStops.stream().map(routeStopMapper::toDto).collect(Collectors.toList()));
        return ResponseEntity.ok(ApiResponse.success("Route created successfully", resultDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteDto>> getRouteById(@PathVariable UUID id) {
        Route route = routeRepository.findById(id)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        RouteDto routeDto = routeMapper.toDto(route);
        List<RouteStop> routeStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(id);
        List<RouteStopDto> stopDtos = routeStops.stream()
                .map(routeStopMapper::toDto)
                .collect(Collectors.toList());
        routeDto.setStops(stopDtos);
        return ResponseEntity.ok(ApiResponse.success("Route retrieved successfully", routeDto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteDto>> updateRoute(
            @PathVariable UUID id,
            @RequestBody RouteDto routeDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Route route = routeRepository.findById(id)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        routeRepository.findByRouteNameAndDeletedAtIsNull(routeDto.getRouteName())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new BadRequestException("Route name already exists");
                    }
                });

        String oldValue = route.getRouteName() + " - " + route.getStatus();
        boolean fromChanged = !route.getStartPoint().equalsIgnoreCase(routeDto.getStartPoint());
        boolean toChanged = !route.getEndPoint().equalsIgnoreCase(routeDto.getEndPoint());
        String oldStart = route.getStartPoint();
        String oldEnd = route.getEndPoint();

        route.setRouteName(routeDto.getRouteName());
        route.setStartPoint(routeDto.getStartPoint());
        route.setEndPoint(routeDto.getEndPoint());
        route.setDistance(routeDto.getDistance());
        route.setEstimatedDurationMins(routeDto.getEstimatedDurationMins());
        if (routeDto.getStatus() != null) {
            route.setStatus(routeDto.getStatus().toUpperCase());
        }
        if (routeDto.getStartLatitude() != null) route.setStartLatitude(routeDto.getStartLatitude());
        if (routeDto.getStartLongitude() != null) route.setStartLongitude(routeDto.getStartLongitude());
        if (routeDto.getEndLatitude() != null) route.setEndLatitude(routeDto.getEndLatitude());
        if (routeDto.getEndLongitude() != null) route.setEndLongitude(routeDto.getEndLongitude());
        if (routeDto.getPolyline() != null) route.setPolyline(routeDto.getPolyline());
        route.setVersion(route.getVersion() != null ? route.getVersion() + 1 : 1);
        route.setUpdatedAt(LocalDateTime.now());

        Route savedRoute = routeRepository.save(route);

        // Atomic stops synchronization if stops are passed in payload
        if (routeDto.getStops() != null && stopRepository != null) {
            List<RouteStop> existing = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(id);
            routeStopRepository.deleteAll(existing);
            routeStopRepository.flush();

            int seq = 1;
            for (RouteStopDto sDto : routeDto.getStops()) {
                StopDto stopDto = sDto.getStop();
                if (stopDto != null && stopDto.getStopName() != null && !stopDto.getStopName().trim().isEmpty()) {
                    Stop stop = stopRepository.findByStopName(stopDto.getStopName().trim())
                            .orElseGet(() -> stopRepository.save(Stop.builder()
                                    .stopName(stopDto.getStopName().trim())
                                    .latitude(stopDto.getLatitude())
                                    .longitude(stopDto.getLongitude())
                                    .build()));
                    stop.setLatitude(stopDto.getLatitude());
                    stop.setLongitude(stopDto.getLongitude());
                    stopRepository.save(stop);

                    RouteStop rs = RouteStop.builder()
                            .route(savedRoute)
                            .stop(stop)
                            .sequenceNumber(seq++)
                            .distanceFromStart(sDto.getDistanceFromStart() != null ? sDto.getDistanceFromStart() : 0.0)
                            .durationFromStartMins(sDto.getDurationFromStartMins())
                            .expectedArrivalTime(sDto.getExpectedArrivalTime())
                            .expectedDepartureTime(sDto.getExpectedDepartureTime())
                            .build();
                    routeStopRepository.save(rs);
                }
            }
        }

        // Broadcast ROUTE_UPDATED event to notify all connected clients
        if (webSocketHandler != null) {
            Map<String, Object> routeUpdate = new HashMap<>();
            routeUpdate.put("type", "ROUTE_UPDATED");
            routeUpdate.put("routeId", savedRoute.getId().toString());
            routeUpdate.put("routeName", savedRoute.getRouteName());
            routeUpdate.put("version", savedRoute.getVersion());
            routeUpdate.put("updatedAt", savedRoute.getUpdatedAt() != null ? savedRoute.getUpdatedAt().toString() : LocalDateTime.now().toString());
            webSocketHandler.broadcast(routeUpdate);
        }

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "ROUTE_UPDATED",
                "Updated route: " + savedRoute.getRouteName(),
                "0.0.0.0",
                "Route",
                savedRoute.getId().toString(),
                oldValue,
                savedRoute.getRouteName() + " - " + savedRoute.getStatus()
        );

        if (fromChanged) {
            auditLogService.logAction(
                    userPrincipal != null ? userPrincipal.getUser() : null,
                    "ROUTE_FROM_LOCATION_CHANGED",
                    "Changed starting location for route: " + savedRoute.getRouteName() + " (from " + oldStart + " to " + savedRoute.getStartPoint() + ")",
                    "0.0.0.0",
                    "Route",
                    savedRoute.getId().toString(),
                    oldStart,
                    savedRoute.getStartPoint()
            );
        }

        if (toChanged) {
            auditLogService.logAction(
                    userPrincipal != null ? userPrincipal.getUser() : null,
                    "ROUTE_TO_LOCATION_CHANGED",
                    "Changed destination for route: " + savedRoute.getRouteName() + " (from " + oldEnd + " to " + savedRoute.getEndPoint() + ")",
                    "0.0.0.0",
                    "Route",
                    savedRoute.getId().toString(),
                    oldEnd,
                    savedRoute.getEndPoint()
            );
        }

        RouteDto resultDto = routeMapper.toDto(savedRoute);
        List<RouteStop> updatedStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(id);
        resultDto.setStops(updatedStops.stream().map(routeStopMapper::toDto).collect(Collectors.toList()));
        return ResponseEntity.ok(ApiResponse.success("Route updated successfully", resultDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoute(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Route route = routeRepository.findById(id)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        // Overlap / reference validation
        List<Schedule> activeSchedules = scheduleRepository.findByRouteIdAndDeletedAtIsNull(id);
        if (!activeSchedules.isEmpty()) {
            throw new BadRequestException("Cannot delete route because it is referenced by active schedules.");
        }

        route.setDeletedAt(java.time.LocalDateTime.now());
        routeRepository.save(route);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "ROUTE_DELETED",
                "Soft-deleted route: " + route.getRouteName(),
                "0.0.0.0",
                "Route",
                route.getId().toString(),
                route.getRouteName(),
                "DELETED"
        );

        return ResponseEntity.ok(ApiResponse.success("Route deleted successfully"));
    }
}
