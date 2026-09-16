package com.smartbus.infrastructure.controller;

import com.smartbus.application.port.in.RouteUseCase;
import com.smartbus.infrastructure.dto.RouteDto;
import com.smartbus.infrastructure.dto.StopDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteUseCase routeUseCase;

    // --- Route Endpoints ---
    @GetMapping("/{id}")
    public ResponseEntity<RouteDto> getRouteById(@PathVariable UUID id) {
        return ResponseEntity.ok(routeUseCase.getRouteById(id));
    }

    @GetMapping
    public ResponseEntity<List<RouteDto>> getAllRoutes() {
        return ResponseEntity.ok(routeUseCase.getAllRoutes());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RouteDto> createRoute(@RequestBody RouteDto routeDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeUseCase.createRoute(routeDto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RouteDto> updateRoute(@PathVariable UUID id, @RequestBody RouteDto routeDto) {
        return ResponseEntity.ok(routeUseCase.updateRoute(id, routeDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoute(@PathVariable UUID id) {
        routeUseCase.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }

    // --- Stop Endpoints ---
    @GetMapping("/stops/{id}")
    public ResponseEntity<StopDto> getStopById(@PathVariable UUID id) {
        return ResponseEntity.ok(routeUseCase.getStopById(id));
    }

    @GetMapping("/stops")
    public ResponseEntity<List<StopDto>> getAllStops() {
        return ResponseEntity.ok(routeUseCase.getAllStops());
    }

    @PostMapping("/stops")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StopDto> createStop(@RequestBody StopDto stopDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeUseCase.createStop(stopDto));
    }

    @DeleteMapping("/stops/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStop(@PathVariable UUID id) {
        routeUseCase.deleteStop(id);
        return ResponseEntity.noContent().build();
    }

    // --- Add Stop to Route Sequence ---
    @PostMapping("/{routeId}/stops")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addStopToRoute(
            @PathVariable UUID routeId,
            @RequestParam UUID stopId,
            @RequestParam int sequenceNumber,
            @RequestParam Double distanceFromStart,
            @RequestParam int durationFromStartMins) {
        routeUseCase.addStopToRoute(routeId, stopId, sequenceNumber, distanceFromStart, durationFromStartMins);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
