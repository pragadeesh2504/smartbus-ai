package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuditLogService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Route;
import com.smartbus.domain.model.RouteStop;
import com.smartbus.domain.model.RouteStopId;
import com.smartbus.domain.model.Stop;
import com.smartbus.infrastructure.adapter.jpa.RouteRepository;
import com.smartbus.infrastructure.adapter.jpa.RouteStopRepository;
import com.smartbus.infrastructure.adapter.jpa.StopRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.RouteStopDto;
import com.smartbus.infrastructure.dto.StopDto;
import com.smartbus.infrastructure.mapper.RouteStopMapper;
import com.smartbus.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import com.smartbus.websocket.LiveLocationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/admin/routes/{routeId}/stops")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStopController {

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;
    private final AuditLogService auditLogService;
    private final RouteStopMapper routeStopMapper;

    @Autowired(required = false)
    private LiveLocationWebSocketHandler webSocketHandler;

    private void touchRouteAndBroadcast(Route route) {
        route.setVersion(route.getVersion() != null ? route.getVersion() + 1 : 1);
        route.setUpdatedAt(java.time.LocalDateTime.now());
        routeRepository.save(route);

        if (webSocketHandler != null) {
            Map<String, Object> routeUpdate = new HashMap<>();
            routeUpdate.put("type", "ROUTE_UPDATED");
            routeUpdate.put("routeId", route.getId().toString());
            routeUpdate.put("routeName", route.getRouteName());
            routeUpdate.put("version", route.getVersion());
            routeUpdate.put("updatedAt", route.getUpdatedAt().toString());
            webSocketHandler.broadcast(routeUpdate);
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteStopDto>>> getStopsByRoute(
            @PathVariable UUID routeId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Route route = routeRepository.findById(routeId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));
        validateRouteBelongsToCollege(route, userPrincipal);

        List<RouteStop> routeStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(routeId);
        List<RouteStopDto> dtos = routeStops.stream()
                .map(routeStopMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Route stops loaded", dtos));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RouteStopDto>> addStopToRoute(
            @PathVariable UUID routeId,
            @RequestBody RouteStopDto routeStopDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Route route = routeRepository.findById(routeId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));
        validateRouteBelongsToCollege(route, userPrincipal);

        StopDto stopDto = routeStopDto.getStop();
        if (stopDto.getLatitude() < -90.0 || stopDto.getLatitude() > 90.0) {
            throw new BadRequestException("Latitude must be between -90 and 90 degrees");
        }
        if (stopDto.getLongitude() < -180.0 || stopDto.getLongitude() > 180.0) {
            throw new BadRequestException("Longitude must be between -180 and 180 degrees");
        }

        // Check if Stop already exists by name in college or create a new one
        Stop stop;
        if (route.getCollege() != null) {
            stop = stopRepository.findByCollegeIdAndStopName(route.getCollege().getId(), stopDto.getStopName().trim())
                    .orElseGet(() -> {
                        Stop newStop = Stop.builder()
                                .stopName(stopDto.getStopName().trim())
                                .latitude(stopDto.getLatitude())
                                .longitude(stopDto.getLongitude())
                                .college(route.getCollege())
                                .build();
                        return stopRepository.save(newStop);
                    });
        } else {
            stop = stopRepository.findByStopName(stopDto.getStopName().trim())
                    .orElseGet(() -> {
                        Stop newStop = Stop.builder()
                                .stopName(stopDto.getStopName().trim())
                                .latitude(stopDto.getLatitude())
                                .longitude(stopDto.getLongitude())
                                .build();
                        return stopRepository.save(newStop);
                    });
        }

        List<RouteStop> existing = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(routeId);
        int nextSeq = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getSequenceNumber() + 1;

        RouteStop routeStop = RouteStop.builder()
                .route(route)
                .stop(stop)
                .sequenceNumber(nextSeq)
                .distanceFromStart(routeStopDto.getDistanceFromStart() != null ? routeStopDto.getDistanceFromStart() : 0.0)
                .durationFromStartMins(routeStopDto.getDurationFromStartMins())
                .expectedArrivalTime(routeStopDto.getExpectedArrivalTime())
                .expectedDepartureTime(routeStopDto.getExpectedDepartureTime())
                .build();

        RouteStop savedRouteStop = routeStopRepository.save(routeStop);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "STOP_CREATED",
                "Added stop " + stop.getStopName() + " to route " + route.getRouteName() + " (Seq: " + nextSeq + ")",
                "0.0.0.0",
                "RouteStop",
                route.getId().toString() + "_" + nextSeq,
                null,
                stop.getStopName()
        );

        touchRouteAndBroadcast(route);

        return ResponseEntity.ok(ApiResponse.success("Stop added to route successfully", routeStopMapper.toDto(savedRouteStop)));
    }

    @PutMapping("/{stopId}")
    public ResponseEntity<ApiResponse<RouteStopDto>> updateRouteStop(
            @PathVariable UUID routeId,
            @PathVariable UUID stopId,
            @RequestBody RouteStopDto routeStopDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Route route = routeRepository.findById(routeId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));
        validateRouteBelongsToCollege(route, userPrincipal);

        List<RouteStop> routeStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(routeId);
        RouteStop target = routeStops.stream()
                .filter(rs -> rs.getStop().getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Stop not linked to this route"));

        // Validate coordinates if updated
        StopDto stopDto = routeStopDto.getStop();
        if (stopDto != null) {
            Stop stop = target.getStop();
            stop.setStopName(stopDto.getStopName());
            if (stopDto.getLatitude() < -90.0 || stopDto.getLatitude() > 90.0 ||
                    stopDto.getLongitude() < -180.0 || stopDto.getLongitude() > 180.0) {
                throw new BadRequestException("Latitude/Longitude out of range");
            }
            stop.setLatitude(stopDto.getLatitude());
            stop.setLongitude(stopDto.getLongitude());
            stopRepository.save(stop);
        }

        target.setDistanceFromStart(routeStopDto.getDistanceFromStart());
        target.setDurationFromStartMins(routeStopDto.getDurationFromStartMins());
        target.setExpectedArrivalTime(routeStopDto.getExpectedArrivalTime());
        target.setExpectedDepartureTime(routeStopDto.getExpectedDepartureTime());

        RouteStop saved = routeStopRepository.save(target);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "STOP_UPDATED",
                "Updated stop configurations for " + target.getStop().getStopName() + " in route " + route.getRouteName(),
                "0.0.0.0",
                "RouteStop",
                route.getId().toString() + "_" + target.getSequenceNumber(),
                null,
                null
        );

        touchRouteAndBroadcast(route);

        return ResponseEntity.ok(ApiResponse.success("Stop updated successfully", routeStopMapper.toDto(saved)));
    }

    @PutMapping("/sync")
    @Transactional
    public ResponseEntity<ApiResponse<List<RouteStopDto>>> syncStops(
            @PathVariable UUID routeId,
            @RequestBody List<RouteStopDto> stopDtos,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Route route = routeRepository.findById(routeId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));
        validateRouteBelongsToCollege(route, userPrincipal);

        // Delete old relations and flush to clear sequence numbers
        List<RouteStop> existing = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(routeId);
        routeStopRepository.deleteAll(existing);
        routeStopRepository.flush();

        List<RouteStop> savedList = new ArrayList<>();
        int seq = 1;
        for (RouteStopDto dto : stopDtos) {
            StopDto stopDto = dto.getStop();
            if (stopDto == null || stopDto.getStopName() == null || stopDto.getStopName().trim().isEmpty()) {
                throw new BadRequestException("Stop information with name is required for all stops");
            }
            if (stopDto.getLatitude() < -90.0 || stopDto.getLatitude() > 90.0) {
                throw new BadRequestException("Latitude must be between -90 and 90 degrees");
            }
            if (stopDto.getLongitude() < -180.0 || stopDto.getLongitude() > 180.0) {
                throw new BadRequestException("Longitude must be between -180 and 180 degrees");
            }

            Stop stop;
            if (route.getCollege() != null) {
                stop = stopRepository.findByCollegeIdAndStopName(route.getCollege().getId(), stopDto.getStopName().trim())
                        .orElseGet(() -> {
                            Stop newStop = Stop.builder()
                                    .stopName(stopDto.getStopName().trim())
                                    .latitude(stopDto.getLatitude())
                                    .longitude(stopDto.getLongitude())
                                    .college(route.getCollege())
                                    .build();
                            return stopRepository.save(newStop);
                        });
            } else {
                stop = stopRepository.findByStopName(stopDto.getStopName().trim())
                        .orElseGet(() -> {
                            Stop newStop = Stop.builder()
                                    .stopName(stopDto.getStopName().trim())
                                    .latitude(stopDto.getLatitude())
                                    .longitude(stopDto.getLongitude())
                                    .build();
                            return stopRepository.save(newStop);
                        });
            }

            // Update coordinates if provided
            stop.setLatitude(stopDto.getLatitude());
            stop.setLongitude(stopDto.getLongitude());
            stopRepository.save(stop);

            RouteStop routeStop = RouteStop.builder()
                    .route(route)
                    .stop(stop)
                    .sequenceNumber(seq)
                    .distanceFromStart(dto.getDistanceFromStart() != null ? dto.getDistanceFromStart() : 0.0)
                    .durationFromStartMins(dto.getDurationFromStartMins())
                    .expectedArrivalTime(dto.getExpectedArrivalTime())
                    .expectedDepartureTime(dto.getExpectedDepartureTime())
                    .build();

            savedList.add(routeStopRepository.save(routeStop));
            seq++;
        }

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "STOPS_SYNCED",
                "Synchronized " + savedList.size() + " stops for route " + route.getRouteName(),
                "0.0.0.0",
                "Route",
                route.getId().toString(),
                null,
                savedList.stream().map(rs -> rs.getStop().getStopName()).collect(Collectors.joining(" -> "))
        );

        touchRouteAndBroadcast(route);

        List<RouteStopDto> dtos = savedList.stream()
                .map(routeStopMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Route stops synchronized successfully", dtos));
    }

    @PutMapping("/reorder")
    @Transactional
    public ResponseEntity<ApiResponse<List<RouteStopDto>>> reorderStops(
            @PathVariable UUID routeId,
            @RequestBody List<UUID> orderedStopIds,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Route route = routeRepository.findById(routeId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));
        validateRouteBelongsToCollege(route, userPrincipal);

        List<RouteStop> existing = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(routeId);

        // Delete old relations
        routeStopRepository.deleteAll(existing);
        routeStopRepository.flush();

        List<RouteStop> reordered = new ArrayList<>();
        int seq = 1;
        for (UUID stopId : orderedStopIds) {
            Stop stop = stopRepository.findById(stopId)
                    .orElseThrow(() -> new ResourceNotFoundException("Stop not found with ID: " + stopId));

            // Find matching values from previous settings or defaults
            final int finalSeq = seq;
            RouteStop match = existing.stream()
                    .filter(rs -> rs.getStop().getId().equals(stopId))
                    .findFirst()
                    .orElse(null);

            double dist = match != null ? match.getDistanceFromStart() : 0.0;
            int duration = match != null ? match.getDurationFromStartMins() : 0;
            LocalTime arr = match != null ? match.getExpectedArrivalTime() : null;
            LocalTime dep = match != null ? match.getExpectedDepartureTime() : null;

            RouteStop newRs = RouteStop.builder()
                    .route(route)
                    .stop(stop)
                    .sequenceNumber(finalSeq)
                    .distanceFromStart(dist)
                    .durationFromStartMins(duration)
                    .expectedArrivalTime(arr)
                    .expectedDepartureTime(dep)
                    .build();

            reordered.add(routeStopRepository.save(newRs));
            seq++;
        }

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "STOP_REORDERED",
                "Reordered stops for route " + route.getRouteName(),
                "0.0.0.0",
                "Route",
                route.getId().toString(),
                null,
                orderedStopIds.toString()
        );

        touchRouteAndBroadcast(route);

        List<RouteStopDto> dtos = reordered.stream()
                .map(routeStopMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Stops reordered successfully", dtos));
    }

    @DeleteMapping("/{stopId}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeStopFromRoute(
            @PathVariable UUID routeId,
            @PathVariable UUID stopId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Route route = routeRepository.findById(routeId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));
        validateRouteBelongsToCollege(route, userPrincipal);

        List<RouteStop> existing = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(routeId);
        RouteStop toRemove = existing.stream()
                .filter(rs -> rs.getStop().getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Stop not linked to route"));

        routeStopRepository.delete(toRemove);
        routeStopRepository.flush();

        // Recalculate remaining sequences to close any gaps
        List<RouteStop> remaining = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(routeId);
        routeStopRepository.deleteAll(remaining);
        routeStopRepository.flush();

        int seq = 1;
        for (RouteStop rs : remaining) {
            rs.setSequenceNumber(seq);
            routeStopRepository.save(rs);
            seq++;
        }

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "STOP_DELETED",
                "Removed stop " + toRemove.getStop().getStopName() + " from route " + route.getRouteName(),
                "0.0.0.0",
                "Route",
                route.getId().toString(),
                toRemove.getStop().getStopName(),
                "REMOVED"
        );

        touchRouteAndBroadcast(route);

        return ResponseEntity.ok(ApiResponse.success("Stop removed from route and sequences recalculated"));
    }

    private void validateRouteBelongsToCollege(Route route, UserPrincipal userPrincipal) {
        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            if (route.getCollege() == null || !userPrincipal.getCollegeId().equals(route.getCollege().getId())) {
                throw new ResourceNotFoundException("Route not found with id: " + route.getId());
            }
        }
    }
}
