package com.smartbus.infrastructure.controller;

import com.smartbus.application.port.in.GpsUseCase;
import com.smartbus.domain.exception.RateLimitExceededException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Driver;
import com.smartbus.domain.model.Role;
import com.smartbus.domain.model.Trip;
import com.smartbus.infrastructure.adapter.jpa.DriverRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import com.smartbus.security.UserPrincipal;
import com.smartbus.security.ratelimit.RateLimitResult;
import com.smartbus.security.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class GpsController {

    private final GpsUseCase gpsUseCase;
    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final RateLimitService rateLimitService;

    @PostMapping("/{tripId}/location")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<Void> updateLocation(
            @PathVariable UUID tripId,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam Double speed,
            @RequestParam Double heading,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }

        // SEC-11: Rate limit GPS telemetry submissions
        if (rateLimitService != null) {
            String rateLimitKey = principal.getUser().getId() + ":" + tripId;
            RateLimitResult rateLimitResult = rateLimitService.checkGpsLimit(rateLimitKey);
            if (!rateLimitResult.isAllowed()) {
                throw new RateLimitExceededException("GPS telemetry rate limit exceeded. Please try again later.", rateLimitResult.getRetryAfterSeconds());
            }
        }

        // SEC-08: Validate telemetry ranges at controller layer
        com.smartbus.application.service.GpsValidationUtil.validateTelemetry(latitude, longitude, speed, heading);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));

        // ADMIN has full authority to submit telemetry
        if (principal.getUser().getRole() == Role.ADMIN) {
            gpsUseCase.processLocationUpdate(tripId, latitude, longitude, speed, heading);
            return ResponseEntity.ok().build();
        }

        // DRIVER must be explicitly assigned to this trip
        if (principal.getUser().getRole() == Role.DRIVER) {
            Driver driver = driverRepository.findByUser(principal.getUser())
                    .orElseThrow(() -> new AccessDeniedException("Driver profile not found"));

            if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
                throw new AccessDeniedException("Driver is not assigned to trip " + tripId);
            }

            gpsUseCase.processLocationUpdate(tripId, latitude, longitude, speed, heading);
            return ResponseEntity.ok().build();
        }

        throw new AccessDeniedException("User role not authorized to submit telemetry");
    }
}
