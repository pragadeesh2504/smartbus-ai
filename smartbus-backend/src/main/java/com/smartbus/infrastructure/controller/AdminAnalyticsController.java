package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AnalyticsService;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.analytics.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import com.smartbus.security.UserPrincipal;
import com.smartbus.security.ratelimit.RateLimitResult;
import com.smartbus.security.ratelimit.RateLimitService;
import com.smartbus.domain.exception.RateLimitExceededException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;
    private final RateLimitService rateLimitService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<FleetOverviewResponse>> getOverview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        FleetOverviewResponse overview = analyticsService.getOverview(from, to);
        return ResponseEntity.ok(ApiResponse.success("Fleet overview retrieved successfully", overview));
    }

    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<List<TripTrendResponse>>> getTrends(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        List<TripTrendResponse> trends = analyticsService.getTripTrends(from, to);
        return ResponseEntity.ok(ApiResponse.success("Trip trends retrieved successfully", trends));
    }

    @GetMapping("/routes")
    public ResponseEntity<ApiResponse<List<RoutePerformanceResponse>>> getRoutes(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        List<RoutePerformanceResponse> routes = analyticsService.getRoutePerformance(from, to);
        return ResponseEntity.ok(ApiResponse.success("Route performance retrieved successfully", routes));
    }

    @GetMapping("/buses")
    public ResponseEntity<ApiResponse<List<BusPerformanceResponse>>> getBuses(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        List<BusPerformanceResponse> buses = analyticsService.getBusPerformance(from, to);
        return ResponseEntity.ok(ApiResponse.success("Bus performance retrieved successfully", buses));
    }

    @GetMapping("/drivers")
    public ResponseEntity<ApiResponse<List<DriverPerformanceResponse>>> getDrivers(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        List<DriverPerformanceResponse> drivers = analyticsService.getDriverPerformance(from, to);
        return ResponseEntity.ok(ApiResponse.success("Driver performance retrieved successfully", drivers));
    }

    @GetMapping("/delays")
    public ResponseEntity<ApiResponse<DelayAnalyticsResponse>> getDelays(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        DelayAnalyticsResponse delays = analyticsService.getDelayAnalytics(from, to);
        return ResponseEntity.ok(ApiResponse.success("Delay analytics retrieved successfully", delays));
    }

    @GetMapping("/deviations")
    public ResponseEntity<ApiResponse<DeviationAnalyticsResponse>> getDeviations(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        DeviationAnalyticsResponse deviations = analyticsService.getDeviationAnalytics(from, to);
        return ResponseEntity.ok(ApiResponse.success("Deviation analytics retrieved successfully", deviations));
    }

    @GetMapping("/gps")
    public ResponseEntity<ApiResponse<GpsHealthResponse>> getGpsHealth(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        GpsHealthResponse gps = analyticsService.getGpsHealth(from, to);
        return ResponseEntity.ok(ApiResponse.success("GPS health metrics retrieved successfully", gps));
    }

    @GetMapping("/trips/{tripId}")
    public ResponseEntity<ApiResponse<TripAnalyticsResponse>> getTripDetails(
            @PathVariable UUID tripId) {
        TripAnalyticsResponse trip = analyticsService.getTripAnalytics(tripId);
        return ResponseEntity.ok(ApiResponse.success("Trip analytics retrieved successfully", trip));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(defaultValue = "routes") String type,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (rateLimitService != null) {
            String adminKey = (principal != null && principal.getUser() != null)
                    ? principal.getUser().getId().toString()
                    : "admin";
            RateLimitResult limit = rateLimitService.checkExportLimit(adminKey);
            if (!limit.isAllowed()) {
                throw new RateLimitExceededException(
                        "Too many export requests. Please try again later.",
                        limit.getRetryAfterSeconds());
            }
        }

        String csvContent = analyticsService.generateCsvExport(type, from, to);
        byte[] bytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fleet_analytics_" + type + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
