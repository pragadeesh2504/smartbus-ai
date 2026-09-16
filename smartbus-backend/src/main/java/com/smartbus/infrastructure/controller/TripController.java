package com.smartbus.infrastructure.controller;

import com.smartbus.application.port.in.TripUseCase;
import com.smartbus.infrastructure.dto.TripDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripUseCase tripUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<TripDto> getTripById(@PathVariable UUID id) {
        return ResponseEntity.ok(tripUseCase.getTripById(id));
    }

    @GetMapping("/active")
    public ResponseEntity<List<TripDto>> getActiveTrips() {
        return ResponseEntity.ok(tripUseCase.getActiveTrips());
    }

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<TripDto> startTrip(@RequestParam UUID scheduleId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripUseCase.startTrip(scheduleId));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<TripDto> pauseTrip(@PathVariable UUID id) {
        return ResponseEntity.ok(tripUseCase.pauseTrip(id));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<TripDto> resumeTrip(@PathVariable UUID id) {
        return ResponseEntity.ok(tripUseCase.resumeTrip(id));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<TripDto> endTrip(@PathVariable UUID id) {
        return ResponseEntity.ok(tripUseCase.endTrip(id));
    }

    @GetMapping("/driver/{driverId}/active")
    public ResponseEntity<TripDto> getActiveTripByDriver(@PathVariable UUID driverId) {
        return ResponseEntity.ok(tripUseCase.getActiveTripByDriver(driverId));
    }
}
