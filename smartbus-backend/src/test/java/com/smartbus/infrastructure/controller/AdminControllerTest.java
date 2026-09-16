package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuditLogService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Bus;
import com.smartbus.domain.model.BusQrToken;
import com.smartbus.domain.model.Driver;
import com.smartbus.domain.model.Schedule;
import com.smartbus.infrastructure.adapter.jpa.BusAssignmentRepository;
import com.smartbus.infrastructure.adapter.jpa.BusQrTokenRepository;
import com.smartbus.infrastructure.adapter.jpa.BusRepository;
import com.smartbus.infrastructure.adapter.jpa.DriverRepository;
import com.smartbus.infrastructure.adapter.jpa.GpsDeviceRepository;
import com.smartbus.infrastructure.adapter.jpa.RouteRepository;
import com.smartbus.infrastructure.adapter.jpa.ScheduleRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.BusDto;
import com.smartbus.infrastructure.dto.DriverDto;
import com.smartbus.infrastructure.mapper.BusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminControllerTest {

    @Mock
    private BusRepository busRepository;

    @Mock
    private BusQrTokenRepository busQrTokenRepository;

    @Mock
    private GpsDeviceRepository gpsDeviceRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private com.smartbus.infrastructure.adapter.jpa.UserRepository userRepository;

    @Mock
    private com.smartbus.infrastructure.adapter.jpa.RefreshTokenRepository refreshTokenRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private BusAssignmentRepository busAssignmentRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private BusMapper busMapper;

    @InjectMocks
    private AdminBusController adminBusController;

    @InjectMocks
    private AdminDriverController adminDriverController;

    @InjectMocks
    private AdminScheduleController adminScheduleController;

    private UUID busId;
    private Bus bus;
    private Driver driver;

    @BeforeEach
    void setUp() {
        busId = UUID.randomUUID();
        bus = Bus.builder()
                .id(busId)
                .busNumber("BUS-101")
                .registrationNumber("TN-01-AB-1234")
                .busCode("SB-BUS-7X4K92")
                .status("ACTIVE")
                .build();

        com.smartbus.domain.model.User user = com.smartbus.domain.model.User.builder()
                .email("driver@smartbus.ai")
                .build();

        driver = Driver.builder()
                .id(UUID.randomUUID())
                .user(user)
                .status("AVAILABLE")
                .approvalStatus("APPROVED")
                .isApproved(true)
                .build();
    }

    @Test
    void createBus_DuplicateRegistration_ThrowsException() {
        BusDto newBusDto = BusDto.builder()
                .busNumber("BUS-101")
                .registrationNumber("TN-01-AB-1234")
                .build();

        when(busRepository.findByRegistrationNumberAndDeletedAtIsNull("TN-01-AB-1234")).thenReturn(Optional.of(bus));

        assertThrows(BadRequestException.class, () -> {
            adminBusController.createBus(newBusDto, null);
        });
    }

    @Test
    void regenerateQrToken_Success() {
        when(busRepository.findById(busId)).thenReturn(Optional.of(bus));
        
        BusQrToken oldToken = BusQrToken.builder().bus(bus).token("old-token").isActive(true).build();
        when(busQrTokenRepository.findByBusIdAndIsActiveTrue(busId)).thenReturn(Optional.of(oldToken));
        
        when(busQrTokenRepository.save(any(BusQrToken.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = 
                adminBusController.regenerateQr(busId, null);

        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData().get("token"));
        verify(busQrTokenRepository, times(2)).save(any(BusQrToken.class)); // Deactivate old + save new
    }

    @Test
    void toggleDriverSuspension_Success() {
        UUID driverId = driver.getId();
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverRepository.save(any(Driver.class))).thenReturn(driver);

        ResponseEntity<ApiResponse<DriverDto>> response = adminDriverController.updateStatus(driverId, "SUSPENDED", null);

        assertNotNull(response);
        assertEquals("SUSPENDED", driver.getStatus());
        verify(driverRepository, times(1)).save(driver);
    }

    @Test
    void checkScheduleOverlap_Conflicts_ThrowsException() {
        Schedule s1 = Schedule.builder()
                .id(UUID.randomUUID())
                .bus(bus)
                .driver(driver)
                .departureTime(LocalTime.of(8, 0))
                .arrivalTime(LocalTime.of(9, 0))
                .daysOfWeek("MONDAY,TUESDAY")
                .status("ACTIVE")
                .build();

        ArrayList<Schedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(s1);

        when(busRepository.findById(any())).thenReturn(Optional.of(bus));
        when(driverRepository.findById(any())).thenReturn(Optional.of(driver));
        when(routeRepository.findById(any())).thenReturn(Optional.of(com.smartbus.domain.model.Route.builder().id(UUID.randomUUID()).status("ACTIVE").build()));
        when(scheduleRepository.findByBusIdAndDeletedAtIsNull(any())).thenReturn(existingSchedules);

        com.smartbus.infrastructure.dto.ScheduleDto newScheduleDto = com.smartbus.infrastructure.dto.ScheduleDto.builder()
                .busId(busId)
                .driverId(driver.getId())
                .routeId(UUID.randomUUID())
                .departureTime("08:30")
                .arrivalTime("09:30")
                .daysOfWeek("MONDAY")
                .build();

        assertThrows(BadRequestException.class, () -> {
            adminScheduleController.createSchedule(newScheduleDto, null);
        });
    }

    @Test
    void deleteBus_ActiveTripConflict_ThrowsException() {
        when(busRepository.findById(busId)).thenReturn(Optional.of(bus));
        com.smartbus.domain.model.Trip activeTrip = com.smartbus.domain.model.Trip.builder()
                .id(UUID.randomUUID())
                .status("EN_ROUTE")
                .build();
        when(tripRepository.findByBusIdAndStatusIn(eq(busId), anyList())).thenReturn(Optional.of(activeTrip));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            adminBusController.deleteBus(busId, null);
        });
        assertTrue(ex.getMessage().contains("active trip"));
    }

    @Test
    void deleteBus_ActiveScheduleConflict_ThrowsException() {
        when(busRepository.findById(busId)).thenReturn(Optional.of(bus));
        when(tripRepository.findByBusIdAndStatusIn(eq(busId), anyList())).thenReturn(Optional.empty());
        Schedule s1 = Schedule.builder().id(UUID.randomUUID()).bus(bus).build();
        when(scheduleRepository.findByBusIdAndDeletedAtIsNull(busId)).thenReturn(java.util.Collections.singletonList(s1));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            adminBusController.deleteBus(busId, null);
        });
        assertTrue(ex.getMessage().contains("active schedule"));
    }

    @Test
    void deleteBus_Success_SoftDeleted() {
        when(busRepository.findById(busId)).thenReturn(Optional.of(bus));
        when(tripRepository.findByBusIdAndStatusIn(eq(busId), anyList())).thenReturn(Optional.empty());
        when(scheduleRepository.findByBusIdAndDeletedAtIsNull(busId)).thenReturn(java.util.Collections.emptyList());
        when(busRepository.save(any(Bus.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<ApiResponse<Void>> response = adminBusController.deleteBus(busId, null);
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertNotNull(bus.getDeletedAt());
        assertEquals("INACTIVE", bus.getStatus());
        verify(busRepository, times(1)).save(bus);
    }

    @Test
    void deleteSchedule_Success_ReleasesBusAssignment() {
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = Schedule.builder()
                .id(scheduleId)
                .route(com.smartbus.domain.model.Route.builder().id(UUID.randomUUID()).routeName("Route 1").build())
                .status("ACTIVE")
                .build();

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        com.smartbus.domain.model.BusAssignment assignment = com.smartbus.domain.model.BusAssignment.builder()
                .id(UUID.randomUUID())
                .status("ACTIVE")
                .build();
        when(busAssignmentRepository.findByScheduleId(scheduleId)).thenReturn(List.of(assignment));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<ApiResponse<Void>> response = adminScheduleController.deleteSchedule(scheduleId, null);
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertNotNull(schedule.getDeletedAt());
        assertEquals("INACTIVE", schedule.getStatus());
        verify(busAssignmentRepository, times(1)).delete(assignment);
    }
}
