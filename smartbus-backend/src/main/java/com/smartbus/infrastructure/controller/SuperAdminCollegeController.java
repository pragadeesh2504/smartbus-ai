package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.CollegeService;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.CollegeDto;
import com.smartbus.infrastructure.dto.PlatformMetricsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/super-admin", "/api/super-admin"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Slf4j
public class SuperAdminCollegeController {

    private final CollegeService collegeService;

    @GetMapping({"/colleges", "/colleges/"})
    public ResponseEntity<ApiResponse<List<CollegeDto>>> getAllColleges() {
        List<CollegeDto> colleges = collegeService.getAllColleges();
        return ResponseEntity.ok(ApiResponse.success("Colleges retrieved successfully", colleges));
    }

    @GetMapping({"/colleges/{id}"})
    public ResponseEntity<ApiResponse<CollegeDto>> getCollegeById(@PathVariable UUID id) {
        CollegeDto college = collegeService.getCollegeById(id);
        return ResponseEntity.ok(ApiResponse.success("College retrieved successfully", college));
    }

    @GetMapping({"/metrics", "/colleges/metrics"})
    public ResponseEntity<ApiResponse<PlatformMetricsDto>> getPlatformMetrics() {
        PlatformMetricsDto metrics = collegeService.getPlatformMetrics();
        return ResponseEntity.ok(ApiResponse.success("Platform metrics retrieved successfully", metrics));
    }
}
