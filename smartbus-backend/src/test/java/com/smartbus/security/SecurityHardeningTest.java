package com.smartbus.security;

import com.smartbus.application.port.in.GpsUseCase;
import com.smartbus.application.service.GpsValidationUtil;
import com.smartbus.domain.model.Driver;
import com.smartbus.domain.model.Role;
import com.smartbus.domain.model.Trip;
import com.smartbus.domain.model.User;
import com.smartbus.infrastructure.adapter.jpa.DriverRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import com.smartbus.infrastructure.controller.GpsController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityHardeningTest {

    @Mock
    private GpsUseCase gpsUseCase;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private com.smartbus.security.ratelimit.RateLimitService rateLimitService;

    @InjectMocks
    private GpsController gpsController;

    private UUID tripId;
    private Trip trip;
    private Driver driver1;
    private Driver driver2;
    private User driverUser1;
    private User driverUser2;
    private User adminUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();

        driverUser1 = User.builder()
                .id(UUID.randomUUID())
                .email("driver1@smartbus.ai")
                .role(Role.DRIVER)
                .isActive(true)
                .build();

        driver1 = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser1)
                .approvalStatus("APPROVED")
                .status("AVAILABLE")
                .build();

        driverUser2 = User.builder()
                .id(UUID.randomUUID())
                .email("driver2@smartbus.ai")
                .role(Role.DRIVER)
                .isActive(true)
                .build();

        driver2 = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser2)
                .approvalStatus("APPROVED")
                .status("AVAILABLE")
                .build();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@smartbus.ai")
                .role(Role.ADMIN)
                .isActive(true)
                .build();

        studentUser = User.builder()
                .id(UUID.randomUUID())
                .email("student@smartbus.ai")
                .role(Role.STUDENT)
                .isActive(true)
                .build();

        trip = Trip.builder()
                .id(tripId)
                .driver(driver1)
                .status("EN_ROUTE")
                .build();

        lenient().when(rateLimitService.checkGpsLimit(anyString()))
                .thenReturn(com.smartbus.security.ratelimit.RateLimitResult.allow());
    }

    @Test
    void sec02_assignedDriverCanSubmitGpsLocation() {
        UserPrincipal principal = new UserPrincipal(driverUser1);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(driverRepository.findByUser(driverUser1)).thenReturn(Optional.of(driver1));

        ResponseEntity<Void> response = gpsController.updateLocation(
                tripId, 12.92, 80.22, 35.0, 90.0, principal);

        assertEquals(200, response.getStatusCode().value());
        verify(gpsUseCase, times(1)).processLocationUpdate(tripId, 12.92, 80.22, 35.0, 90.0);
    }

    @Test
    void sec02_unassignedDriverIsDeniedGpsLocationSubmission() {
        UserPrincipal principal = new UserPrincipal(driverUser2);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(driverRepository.findByUser(driverUser2)).thenReturn(Optional.of(driver2));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> {
            gpsController.updateLocation(tripId, 12.92, 80.22, 35.0, 90.0, principal);
        });

        assertTrue(ex.getMessage().contains("Driver is not assigned"));
        verify(gpsUseCase, never()).processLocationUpdate(any(), any(), any(), any(), any());
    }

    @Test
    void sec02_studentIsDeniedGpsLocationSubmission() {
        UserPrincipal principal = new UserPrincipal(studentUser);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> {
            gpsController.updateLocation(tripId, 12.92, 80.22, 35.0, 90.0, principal);
        });

        assertTrue(ex.getMessage().contains("not authorized"));
        verify(gpsUseCase, never()).processLocationUpdate(any(), any(), any(), any(), any());
    }

    @Test
    void sec02_adminCanSubmitGpsLocation() {
        UserPrincipal principal = new UserPrincipal(adminUser);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        ResponseEntity<Void> response = gpsController.updateLocation(
                tripId, 12.92, 80.22, 35.0, 90.0, principal);

        assertEquals(200, response.getStatusCode().value());
        verify(gpsUseCase, times(1)).processLocationUpdate(tripId, 12.92, 80.22, 35.0, 90.0);
    }

    @Test
    void sec03_jwtTokenProviderEnforcesSecretRequirements() {
        // Empty secret fails
        assertThrows(IllegalStateException.class, () -> {
            new JwtTokenProvider("", 3600000);
        });

        // Secret shorter than 32 bytes (256 bits) fails
        assertThrows(IllegalStateException.class, () -> {
            new JwtTokenProvider("short-secret-less-than-32-b", 3600000);
        });

        // 32-byte secret succeeds
        JwtTokenProvider provider = new JwtTokenProvider("12345678901234567890123456789012", 3600000);
        assertNotNull(provider);
    }

    @Test
    void sec08_latitudeValidation() {
        // Valid boundaries
        assertDoesNotThrow(() -> GpsValidationUtil.validateLatitude(-90.0));
        assertDoesNotThrow(() -> GpsValidationUtil.validateLatitude(90.0));
        assertDoesNotThrow(() -> GpsValidationUtil.validateLatitude(0.0));

        // Invalid boundaries
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLatitude(-90.001));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLatitude(90.001));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLatitude(null));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLatitude(Double.NaN));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLatitude(Double.POSITIVE_INFINITY));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLatitude(Double.NEGATIVE_INFINITY));
    }

    @Test
    void sec08_longitudeValidation() {
        // Valid boundaries
        assertDoesNotThrow(() -> GpsValidationUtil.validateLongitude(-180.0));
        assertDoesNotThrow(() -> GpsValidationUtil.validateLongitude(180.0));
        assertDoesNotThrow(() -> GpsValidationUtil.validateLongitude(0.0));

        // Invalid boundaries
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLongitude(-180.001));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLongitude(180.001));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLongitude(null));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLongitude(Double.NaN));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLongitude(Double.POSITIVE_INFINITY));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateLongitude(Double.NEGATIVE_INFINITY));
    }

    @Test
    void sec08_speedValidation() {
        // Valid values
        assertDoesNotThrow(() -> GpsValidationUtil.validateSpeed(0.0));
        assertDoesNotThrow(() -> GpsValidationUtil.validateSpeed(30.0));
        assertDoesNotThrow(() -> GpsValidationUtil.validateSpeed(160.0));
        assertDoesNotThrow(() -> GpsValidationUtil.validateSpeed(null));

        // Invalid values
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateSpeed(-1.0));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateSpeed(160.1));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateSpeed(Double.NaN));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateSpeed(Double.POSITIVE_INFINITY));
    }

    @Test
    void sec08_headingValidation() {
        // Valid values
        assertDoesNotThrow(() -> GpsValidationUtil.validateHeading(0.0));
        assertDoesNotThrow(() -> GpsValidationUtil.validateHeading(180.0));
        assertDoesNotThrow(() -> GpsValidationUtil.validateHeading(359.99));
        assertDoesNotThrow(() -> GpsValidationUtil.validateHeading(null));

        // Invalid values
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateHeading(-0.01));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateHeading(360.0));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateHeading(Double.NaN));
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> GpsValidationUtil.validateHeading(Double.POSITIVE_INFINITY));
    }

    @Test
    void sec08_controllerRejectsInvalidGpsTelemetry() {
        UserPrincipal principal = new UserPrincipal(adminUser);

        // Invalid Latitude
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> {
            gpsController.updateLocation(tripId, 95.0, 80.0, 30.0, 90.0, principal);
        });

        // Invalid Longitude
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> {
            gpsController.updateLocation(tripId, 12.0, 195.0, 30.0, 90.0, principal);
        });

        // Invalid Speed
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> {
            gpsController.updateLocation(tripId, 12.0, 80.0, 200.0, 90.0, principal);
        });

        // Invalid Heading
        assertThrows(com.smartbus.domain.exception.BadRequestException.class, () -> {
            gpsController.updateLocation(tripId, 12.0, 80.0, 30.0, 365.0, principal);
        });
    }

    @Test
    void sec11_rateLimitExceededRejectsGpsTelemetryWithoutProcessing() {
        UserPrincipal principal = new UserPrincipal(driverUser1);
        when(rateLimitService.checkGpsLimit(anyString())).thenReturn(
                com.smartbus.security.ratelimit.RateLimitResult.block(30)
        );

        com.smartbus.domain.exception.RateLimitExceededException ex = assertThrows(
                com.smartbus.domain.exception.RateLimitExceededException.class,
                () -> gpsController.updateLocation(tripId, 12.92, 80.22, 35.0, 90.0, principal)
        );

        assertEquals(30, ex.getRetryAfterSeconds());
        assertTrue(ex.getMessage().contains("rate limit exceeded"));
        verifyNoInteractions(gpsUseCase);
        verifyNoInteractions(tripRepository);
    }
}
