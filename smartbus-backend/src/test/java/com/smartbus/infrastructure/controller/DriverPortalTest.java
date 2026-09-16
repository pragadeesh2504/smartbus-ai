package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.*;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.security.UserPrincipal;
import com.smartbus.websocket.LiveLocationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DriverPortalTest {

    @Mock private DriverRepository driverRepository;
    @Mock private BusRepository busRepository;
    @Mock private BusQrTokenRepository busQrTokenRepository;
    @Mock private BusAssignmentRepository busAssignmentRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TripRepository tripRepository;
    @Mock private TripLocationRepository tripLocationRepository;
    @Mock private TripStopEventRepository tripStopEventRepository;
    @Mock private BreakdownReportRepository breakdownReportRepository;
    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private DriverNotificationRepository driverNotificationRepository;
    @Mock private EmergencyLogRepository emergencyLogRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private GeofencingService geofencingService;
    @Mock private HybridTrackingService hybridTrackingService;
    @Mock private LiveLocationWebSocketHandler webSocketHandler;
    @Mock private SmartNotificationService smartNotificationService;
    @Mock private RouteStopRepository routeStopRepository;
    @Mock private EtaCalculationService etaCalculationService;

    @InjectMocks
    private DriverPortalController driverPortalController;

    private User driverUser;
    private Driver driver;
    private Bus bus;
    private Route route;
    private Schedule schedule;
    private BusAssignment assignment;
    private BusQrToken qrToken;

    @BeforeEach
    public void setUp() {
        driverUser = User.builder()
                .id(UUID.randomUUID())
                .email("driver@smartbus.ai")
                .firstName("Demo")
                .lastName("Driver")
                .isActive(true)
                .role(Role.DRIVER)
                .build();

        driver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .approvalStatus("APPROVED")
                .status("AVAILABLE")
                .build();

        bus = Bus.builder()
                .id(UUID.randomUUID())
                .busNumber("BUS-101")
                .busCode("SB-BUS-7X4K92")
                .status("ACTIVE")
                .capacity(40)
                .build();

        route = Route.builder()
                .id(UUID.randomUUID())
                .routeName("CIT Campus -> Central")
                .status("ACTIVE")
                .build();

        schedule = Schedule.builder()
                .id(UUID.randomUUID())
                .departureTime(LocalTime.of(8, 0))
                .status("ACTIVE")
                .build();

        assignment = BusAssignment.builder()
                .id(UUID.randomUUID())
                .bus(bus)
                .driver(driver)
                .route(route)
                .schedule(schedule)
                .status("ACTIVE")
                .build();

        qrToken = BusQrToken.builder()
                .id(UUID.randomUUID())
                .bus(bus)
                .token("secure-token-123")
                .isActive(true)
                .build();

        // Setup security context
        UserPrincipal principal = new UserPrincipal(driverUser);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    public void testVerifyQr_Success() {
        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));
        when(busRepository.findByBusCodeAndDeletedAtIsNull(any())).thenReturn(Optional.of(bus));
        when(busQrTokenRepository.findByTokenAndIsActiveTrue(any())).thenReturn(Optional.of(qrToken));
        when(busAssignmentRepository.findByDriverIdAndStatus(any(), any())).thenReturn(Collections.singletonList(assignment));
        when(tripRepository.findByBusIdAndStatusIn(any(), any())).thenReturn(Optional.empty());

        DriverPortalController.QrVerifyRequest req = new DriverPortalController.QrVerifyRequest("smartbus://bus/SB-BUS-7X4K92/secure-token-123");
        ResponseEntity<DriverPortalController.QrVerifyResponse> response = driverPortalController.verifyQr(req);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("BUS-101", response.getBody().getBus().getBusNumber());
    }

    @Test
    public void testVerifyQr_InvalidQrFormat_ThrowsException() {
        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));

        DriverPortalController.QrVerifyRequest req = new DriverPortalController.QrVerifyRequest("invalid-qr");
        assertThrows(BadRequestException.class, () -> driverPortalController.verifyQr(req));
    }

    @Test
    public void testVerifyQr_InactiveBus_ThrowsException() {
        bus.setStatus("INACTIVE");
        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));
        when(busRepository.findByBusCodeAndDeletedAtIsNull(any())).thenReturn(Optional.of(bus));

        DriverPortalController.QrVerifyRequest req = new DriverPortalController.QrVerifyRequest("smartbus://bus/SB-BUS-7X4K92/secure-token-123");
        assertThrows(BadRequestException.class, () -> driverPortalController.verifyQr(req));
    }

    @Test
    public void testVerifyQr_DriverNotApproved_ThrowsException() {
        driver.setApprovalStatus("PENDING");
        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));

        DriverPortalController.QrVerifyRequest req = new DriverPortalController.QrVerifyRequest("smartbus://bus/SB-BUS-7X4K92/secure-token-123");
        assertThrows(BadRequestException.class, () -> driverPortalController.verifyQr(req));
    }

    @Test
    public void testVerifyQr_BusAlreadyInTrip_ThrowsException() {
        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));
        when(busRepository.findByBusCodeAndDeletedAtIsNull(any())).thenReturn(Optional.of(bus));
        when(busQrTokenRepository.findByTokenAndIsActiveTrue(any())).thenReturn(Optional.of(qrToken));
        when(busAssignmentRepository.findByDriverIdAndStatus(any(), any())).thenReturn(Collections.singletonList(assignment));
        
        Trip activeTrip = Trip.builder().id(UUID.randomUUID()).status("IN_PROGRESS").build();
        when(tripRepository.findByBusIdAndStatusIn(any(), any())).thenReturn(Optional.of(activeTrip));

        DriverPortalController.QrVerifyRequest req = new DriverPortalController.QrVerifyRequest("smartbus://bus/SB-BUS-7X4K92/secure-token-123");
        assertThrows(BadRequestException.class, () -> driverPortalController.verifyQr(req));
    }

    @Test
    public void testStartTrip_Success() {
        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));
        when(scheduleRepository.findById(any())).thenReturn(Optional.of(schedule));
        when(busAssignmentRepository.findByDriverIdAndStatus(any(), any())).thenReturn(Collections.singletonList(assignment));
        when(tripRepository.findByBusIdAndStatusIn(any(), any())).thenReturn(Optional.empty());
        when(tripRepository.findByDriverIdAndStatusIn(any(), any())).thenReturn(Optional.empty());
        when(tripRepository.save(any())).thenAnswer(invocation -> {
            Trip t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        ResponseEntity<DriverPortalController.TripResponse> response = driverPortalController.startTrip(schedule.getId());
        assertNotNull(response.getBody());
        assertEquals("IN_PROGRESS", response.getBody().getStatus());
        verify(smartNotificationService, times(1)).handleTripStarted(any());
    }

    @Test
    public void testPauseAndResumeTrip_Success() {
        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .driver(driver)
                .bus(bus)
                .route(route)
                .status("IN_PROGRESS")
                .build();

        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        when(tripRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Pause
        ResponseEntity<DriverPortalController.TripResponse> responsePause = driverPortalController.pauseTrip(trip.getId(), new DriverPortalController.PauseRequest("Traffic", 12.0, 80.0));
        assertEquals("PAUSED", responsePause.getBody().getStatus());

        // Resume
        ResponseEntity<DriverPortalController.TripResponse> responseResume = driverPortalController.resumeTrip(trip.getId());
        assertEquals("IN_PROGRESS", responseResume.getBody().getStatus());
    }

    @Test
    public void testEndTrip_Success() {
        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .driver(driver)
                .bus(bus)
                .route(route)
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now().minusMinutes(30))
                .build();

        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));
        when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        when(tripRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tripLocationRepository.findByTripIdOrderByTimestampAsc(any())).thenReturn(Collections.emptyList());

        ResponseEntity<DriverPortalController.TripResponse> response = driverPortalController.endTrip(trip.getId());
        assertNotNull(response.getBody());
        assertEquals("COMPLETED", response.getBody().getStatus());
    }

    @Test
    public void testTriggerSOS_Success() {
        Trip trip = Trip.builder().id(UUID.randomUUID()).driver(driver).bus(bus).build();
        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));
        when(tripRepository.findByDriverIdAndStatusIn(any(), any())).thenReturn(Optional.of(trip));

        ResponseEntity<Map<String, Boolean>> response = driverPortalController.triggerEmergency(new DriverPortalController.EmergencyRequest(12.9, 80.2));
        assertTrue(response.getBody().get("success"));
        verify(emergencyLogRepository, times(1)).save(any());
    }

    @Test
    public void testReportBreakdown_Success() {
        Trip trip = Trip.builder().id(UUID.randomUUID()).driver(driver).bus(bus).build();
        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));
        when(tripRepository.findByDriverIdAndStatusIn(any(), any())).thenReturn(Optional.of(trip));

        ResponseEntity<Map<String, Boolean>> response = driverPortalController.reportBreakdown(new DriverPortalController.BreakdownRequest("ENGINE", "Overheating engine", null));
        assertTrue(response.getBody().get("success"));
        verify(breakdownReportRepository, times(1)).save(any());
        verify(maintenanceRepository, times(1)).save(any());
    }

    @Test
    public void testGetDriverSchedules_OnlyOwnSchedules() {
        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));

        Schedule sched = Schedule.builder()
                .id(UUID.randomUUID())
                .driver(driver)
                .bus(bus)
                .route(route)
                .departureTime(LocalTime.of(8, 0))
                .arrivalTime(LocalTime.of(8, 45))
                .status("ACTIVE")
                .build();

        when(scheduleRepository.findByDriverIdAndDeletedAtIsNull(driver.getId())).thenReturn(Collections.singletonList(sched));
        when(busAssignmentRepository.findByDriverIdAndStatus(driver.getId(), "ACTIVE")).thenReturn(Collections.emptyList());
        when(tripRepository.findByDriverIdAndStatusIn(any(), any())).thenReturn(Optional.empty());

        ResponseEntity<List<DriverPortalController.DriverScheduleItem>> res = driverPortalController.getDriverSchedules();
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().size());
        assertEquals(sched.getId(), res.getBody().get(0).getScheduleId());
        assertEquals("BUS-101", res.getBody().get(0).getBusNumber());
    }

    @Test
    public void testStartTrip_OtherDriverSchedule_ThrowsAccessDenied() {
        Driver otherDriver = Driver.builder().id(UUID.randomUUID()).build();
        Schedule otherSched = Schedule.builder()
                .id(UUID.randomUUID())
                .driver(otherDriver)
                .bus(bus)
                .route(route)
                .status("ACTIVE")
                .build();

        when(driverRepository.findByUser(any())).thenReturn(Optional.of(driver));
        when(scheduleRepository.findById(otherSched.getId())).thenReturn(Optional.of(otherSched));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> driverPortalController.startTrip(otherSched.getId()));
    }
}
