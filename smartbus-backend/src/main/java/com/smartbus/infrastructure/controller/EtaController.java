package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.EtaCalculationService;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.EtaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/eta")
@RequiredArgsConstructor
public class EtaController {

    private final EtaCalculationService etaCalculationService;

    @GetMapping("/trips/{tripId}/eta")
    public ResponseEntity<ApiResponse<EtaResponse>> getTripEta(
            @PathVariable UUID tripId,
            @RequestParam(required = false) UUID targetStopId) {
        EtaResponse eta = etaCalculationService.calculateEta(tripId, targetStopId);
        return ResponseEntity.ok(ApiResponse.success("Trip ETA calculated successfully", eta));
    }

    @GetMapping("/buses/{busId}/eta")
    public ResponseEntity<ApiResponse<EtaResponse>> getBusEta(
            @PathVariable UUID busId,
            @RequestParam(required = false) UUID targetStopId) {
        EtaResponse eta = etaCalculationService.calculateBusEta(busId, targetStopId);
        return ResponseEntity.ok(ApiResponse.success("Bus ETA calculated successfully", eta));
    }
}
