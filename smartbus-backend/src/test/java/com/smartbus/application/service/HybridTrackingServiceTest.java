package com.smartbus.application.service;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.websocket.LiveLocationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HybridTrackingServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private BusRepository busRepository;
    @Mock private TripLocationRepository tripLocationRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SettingRepository settingRepository;
    @Mock private GeofencingService geofencingService;
    @Mock private DemoGpsDeviceAdapter demoGpsDeviceAdapter;
    @Mock private LiveLocationWebSocketHandler webSocketHandler;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private HybridTrackingService hybridTrackingService;

    private Trip trip;
    private Bus bus;
    private GpsDevice gpsDevice;
    private Driver driver;

    @BeforeEach
    public void setUp() {
        gpsDevice = GpsDevice.builder()
                .id(UUID.randomUUID())
                .deviceId("GPS-DEV-101")
                .status("ACTIVE")
                .build();

        bus = Bus.builder()
                .id(UUID.randomUUID())
                .busNumber("BUS-101")
                .gpsDevice(gpsDevice)
                .build();

        driver = Driver.builder()
                .id(UUID.randomUUID())
                .user(User.builder().email("driver@smartbus.ai").firstName("Driver").lastName("Demo").build())
                .build();

        trip = Trip.builder()
                .id(UUID.randomUUID())
                .bus(bus)
                .driver(driver)
                .route(Route.builder().routeName("Route 1").build())
                .status("IN_PROGRESS")
                .build();

        lenient().when(tripRepository.findById(any())).thenReturn(Optional.of(trip));
        lenient().when(settingRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    public void testHybridTracking_GpsDeviceOnline_PrefersGpsDevice() {
        LocationData deviceLoc = LocationData.builder()
                .latitude(12.97)
                .longitude(80.22)
                .speed(20.0)
                .heading(0.0)
                .accuracy(5.0)
                .timestamp(LocalDateTime.now())
                .build();

        LocationData phoneLoc = LocationData.builder()
                .latitude(12.971)
                .longitude(80.221)
                .speed(20.0)
                .heading(0.0)
                .accuracy(10.0)
                .timestamp(LocalDateTime.now())
                .build();

        when(demoGpsDeviceAdapter.getLatestLocation(any())).thenReturn(deviceLoc);
        when(geofencingService.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(50.0);

        hybridTrackingService.processLocationUpdate(trip.getId(), phoneLoc, "event-123");

        verify(tripLocationRepository, times(1)).save(argThat(loc -> 
                "GPS_DEVICE".equals(loc.getTrackingSource()) && "event-123".equals(loc.getSourceEventId())
        ));
    }

    @Test
    public void testHybridTracking_GpsDeviceOffline_FallsBackToPhone() {
        LocationData phoneLoc = LocationData.builder()
                .latitude(12.971)
                .longitude(80.221)
                .speed(20.0)
                .heading(0.0)
                .accuracy(10.0)
                .timestamp(LocalDateTime.now())
                .build();

        when(demoGpsDeviceAdapter.getLatestLocation(any())).thenReturn(null);

        hybridTrackingService.processLocationUpdate(trip.getId(), phoneLoc, "event-456");

        verify(tripLocationRepository, times(1)).save(argThat(loc -> 
                "DRIVER_PHONE".equals(loc.getTrackingSource()) && "event-456".equals(loc.getSourceEventId())
        ));
    }

    @Test
    public void testHybridTracking_AnomalyDetected_TriggersAlert() {
        LocationData deviceLoc = LocationData.builder()
                .latitude(12.97)
                .longitude(80.22)
                .speed(20.0)
                .heading(0.0)
                .accuracy(5.0)
                .timestamp(LocalDateTime.now())
                .build();

        LocationData phoneLoc = LocationData.builder()
                .latitude(12.99) // Diverging coordinates
                .longitude(80.24)
                .speed(20.0)
                .heading(0.0)
                .accuracy(10.0)
                .timestamp(LocalDateTime.now())
                .build();

        when(demoGpsDeviceAdapter.getLatestLocation(any())).thenReturn(deviceLoc);
        // Simulate > 500m difference (e.g. 1500m)
        when(geofencingService.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(1500.0);

        hybridTrackingService.processLocationUpdate(trip.getId(), phoneLoc, "event-789");

        verify(auditLogService, times(1)).logAction(
                any(),
                eq("GPS_ANOMALY_DETECTED"),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyString()
        );
    }
}
