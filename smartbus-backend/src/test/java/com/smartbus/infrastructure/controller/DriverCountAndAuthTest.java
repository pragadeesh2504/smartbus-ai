package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuthService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.model.Driver;
import com.smartbus.domain.model.Role;
import com.smartbus.domain.model.User;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.LoginRequest;
import com.smartbus.infrastructure.dto.LoginResponse;
import com.smartbus.security.JwtTokenProvider;
import com.smartbus.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DriverCountAndAuthTest {

    @Mock
    private BusRepository busRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private StopRepository stopRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private GpsDeviceRepository gpsDeviceRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private com.smartbus.application.service.AuditLogService auditLogService;

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    @InjectMocks
    private AdminDriverController adminDriverController;

    @InjectMocks
    private AuthService authService;

    private User driverUser;
    private Driver approvedDriver;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(authService, "refreshTokenDurationMs", 604800000L);

        driverUser = User.builder()
                .id(UUID.randomUUID())
                .email("driver1@smartbus.ai")
                .firstName("Ramesh")
                .lastName("Kumar")
                .role(Role.DRIVER)
                .isActive(true)
                .build();

        approvedDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .licenseNumber("DL-1001")
                .approvalStatus("APPROVED")
                .isApproved(true)
                .status("AVAILABLE")
                .build();
    }

    @Test
    void testDashboard_ReportsAccurateActiveDriverCounts() {
        when(busRepository.findByDeletedAtIsNull()).thenReturn(Collections.emptyList());
        when(busRepository.findByStatusAndDeletedAtIsNull("ACTIVE")).thenReturn(Collections.emptyList());
        when(busRepository.findByStatusAndDeletedAtIsNull("MAINTENANCE")).thenReturn(Collections.emptyList());
        when(routeRepository.findByDeletedAtIsNull()).thenReturn(Collections.emptyList());
        when(stopRepository.count()).thenReturn(18L);
        when(scheduleRepository.findByDeletedAtIsNull()).thenReturn(Collections.emptyList());
        when(gpsDeviceRepository.findAll()).thenReturn(Collections.emptyList());
        when(auditLogRepository.findAll(any(org.springframework.data.domain.PageRequest.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(Collections.emptyList()));

        // Exactly 3 active drivers, 3 approved drivers
        when(driverRepository.countActiveDrivers()).thenReturn(3L);
        when(driverRepository.countApprovedActiveDrivers()).thenReturn(3L);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminDashboardController.getDashboardStats();
        assertNotNull(response.getBody());
        Map<String, Object> stats = response.getBody().getData();

        assertEquals(3L, stats.get("totalDrivers"));
        assertEquals(3L, stats.get("approvedDrivers"));
        verify(driverRepository).countActiveDrivers();
        verify(driverRepository).countApprovedActiveDrivers();
    }

    @Test
    void testDriverLogin_ExactAdminRegisteredEmail_Passes() {
        when(driverRepository.findByUser(driverUser)).thenReturn(Optional.of(approvedDriver));

        UserPrincipal principal = new UserPrincipal(driverUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("jwt-mock-token");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoginRequest req = LoginRequest.builder()
                .email("driver1@smartbus.ai")
                .password("Driver@123")
                .role("DRIVER")
                .build();

        LoginResponse resp = authService.login(req);
        assertNotNull(resp);
        assertEquals("driver1@smartbus.ai", resp.getEmail());
        assertEquals("DRIVER", resp.getRole());
        assertEquals("jwt-mock-token", resp.getAccessToken());
    }

    @Test
    void testDriverLogin_CaseInsensitiveAndWhitespace_Passes() {
        when(driverRepository.findByUser(driverUser)).thenReturn(Optional.of(approvedDriver));

        UserPrincipal principal = new UserPrincipal(driverUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("jwt-mock-token");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoginRequest req = new LoginRequest();
        req.setEmail("  Driver1@SmartBus.AI  ");
        req.setPassword("Driver@123");
        req.setRole("DRIVER");

        LoginResponse resp = authService.login(req);
        assertNotNull(resp);
        assertEquals("driver1@smartbus.ai", resp.getEmail());
    }

    @Test
    void testDriverLogin_UnapprovedDriver_ThrowsPendingApprovalException() {
        Driver pendingDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .licenseNumber("DL-1001")
                .approvalStatus("PENDING")
                .isApproved(false)
                .status("AVAILABLE")
                .build();

        when(driverRepository.findByUser(driverUser)).thenReturn(Optional.of(pendingDriver));

        UserPrincipal principal = new UserPrincipal(driverUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest req = LoginRequest.builder()
                .email("driver1@smartbus.ai")
                .password("Driver@123")
                .role("DRIVER")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(req));
        assertEquals("Driver account is pending administrator approval.", ex.getMessage());
    }

    @Test
    void testDriverLogin_SuspendedDriver_ThrowsSuspendedException() {
        Driver suspendedDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .licenseNumber("DL-1001")
                .approvalStatus("APPROVED")
                .isApproved(true)
                .status("SUSPENDED")
                .build();

        when(driverRepository.findByUser(driverUser)).thenReturn(Optional.of(suspendedDriver));

        UserPrincipal principal = new UserPrincipal(driverUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest req = LoginRequest.builder()
                .email("driver1@smartbus.ai")
                .password("Driver@123")
                .role("DRIVER")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(req));
        assertEquals("Driver account is suspended. Please contact administrator.", ex.getMessage());
    }

    @Test
    void testDriverLogin_WrongRoleSelected_ThrowsRoleMismatchException() {
        UserPrincipal principal = new UserPrincipal(driverUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest req = LoginRequest.builder()
                .email("driver1@smartbus.ai")
                .password("Driver@123")
                .role("STUDENT")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(req));
        assertEquals("Access denied: The selected role does not match this account.", ex.getMessage());
    }

    @Test
    void testSetDriverPassword_Valid_Success() {
        UUID driverId = approvedDriver.getId();
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(approvedDriver));
        when(passwordEncoder.encode("NewSecret@123")).thenReturn("hashed_new_secret");

        com.smartbus.infrastructure.dto.SetDriverPasswordRequest req = com.smartbus.infrastructure.dto.SetDriverPasswordRequest.builder()
                .newPassword("NewSecret@123")
                .confirmPassword("NewSecret@123")
                .build();

        ResponseEntity<ApiResponse<Void>> response = adminDriverController.setDriverPassword(driverId, req, null);

        assertNotNull(response.getBody());
        assertEquals("Password updated successfully.", response.getBody().getMessage());
        assertEquals("hashed_new_secret", driverUser.getPasswordHash());
        verify(userRepository).save(driverUser);
        verify(refreshTokenRepository).deleteByUser(driverUser);
    }

    @Test
    void testSetDriverPassword_ShortPassword_ThrowsBadRequest() {
        com.smartbus.infrastructure.dto.SetDriverPasswordRequest req = com.smartbus.infrastructure.dto.SetDriverPasswordRequest.builder()
                .newPassword("short")
                .confirmPassword("short")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> adminDriverController.setDriverPassword(approvedDriver.getId(), req, null));
        assertEquals("Password must be at least 8 characters long", ex.getMessage());
    }

    @Test
    void testSetDriverPassword_MismatchConfirm_ThrowsBadRequest() {
        com.smartbus.infrastructure.dto.SetDriverPasswordRequest req = com.smartbus.infrastructure.dto.SetDriverPasswordRequest.builder()
                .newPassword("NewSecret@123")
                .confirmPassword("Different@123")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> adminDriverController.setDriverPassword(approvedDriver.getId(), req, null));
        assertEquals("Passwords do not match", ex.getMessage());
    }
}
