package com.smartbus.infrastructure.controller;

import com.smartbus.application.port.in.BusUseCase;
import com.smartbus.infrastructure.dto.BusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/buses")
@RequiredArgsConstructor
public class BusController {

    private final BusUseCase busUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<BusDto> getBusById(@PathVariable UUID id) {
        return ResponseEntity.ok(busUseCase.getBusById(id));
    }

    @GetMapping
    public ResponseEntity<List<BusDto>> getAllBuses() {
        return ResponseEntity.ok(busUseCase.getAllBuses());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BusDto> createBus(@RequestBody BusDto busDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(busUseCase.createBus(busDto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BusDto> updateBus(@PathVariable UUID id, @RequestBody BusDto busDto) {
        return ResponseEntity.ok(busUseCase.updateBus(id, busDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBus(@PathVariable UUID id) {
        busUseCase.deleteBus(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/location")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateLocation(
            @PathVariable UUID id,
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        busUseCase.updateBusLocation(id, latitude, longitude);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        busUseCase.updateBusStatus(id, status);
        return ResponseEntity.ok().build();
    }
}
