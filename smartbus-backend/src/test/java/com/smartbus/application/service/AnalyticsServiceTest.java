package com.smartbus.application.service;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.dto.analytics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AnalyticsServiceTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripLocationRepository tripLocationRepository;
    @Mock
    private TripStopEventRepository tripStopEventRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DriverNotificationRepository driverNotificationRepository;
    @Mock
    private BusRepository busRepository;
    @Mock
    private RouteRepository routeRepository;
    @Mock
    private RouteStopRepository routeStopRepository;
    @Mock
    private DriverRepository driverRepository;
    @Mock
    private GpsDeviceRepository gpsDeviceRepository;
    @Mock
    private SettingRepository settingRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Route testRoute;
    private Bus testBus;
    private Driver testDriver;
    private User testUser;
    private Trip trip1;
    private Trip trip2;

    @BeforeEach
    void setUp() {
        testRoute = Route.builder()
                .id(UUID.randomUUID())
                .routeName("Route 42")
                .startPoint("North Campus")
                .endPoint("South Station")
                .distance(15.0)
                .estimatedDurationMins(30)
                .status("ACTIVE")
                .build();

        testBus = Bus.builder()
                .id(UUID.randomUUID())
                .busNumber("BUS-101")
                .busCode("B101")
                .status("ACTIVE")
                .model("Standard Coach")
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@smartbus.edu")
                .build();

        testDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .licenseNumber("DL-987654321")
                .employeeId("EMP-101")
                .build();

        LocalDateTime now = LocalDateTime.now();

        trip1 = Trip.builder()
                .id(UUID.randomUUID())
                .route(testRoute)
                .bus(testBus)
                .driver(testDriver)
                .startTime(now.minusHours(2))
                .endTime(now.minusHours(1))
                .status("COMPLETED")
                .distance(15.5)
                .duration(60)
                .build();

        trip2 = Trip.builder()
                .id(UUID.randomUUID())
                .route(testRoute)
                .bus(testBus)
                .driver(testDriver)
                .startTime(now.minusHours(1))
                .status("EN_ROUTE")
                .distance(8.0)
                .build();
    }

    @Test
    void testGetOverviewEmptyData() {
        when(tripRepository.findByStartTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());
        when(busRepository.findByDeletedAtIsNull()).thenReturn(Collections.emptyList());
        when(notificationRepository.countByTypeAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);

        FleetOverviewResponse res = analyticsService.getOverview(null, null);

        assertNotNull(res);
        assertEquals(0, res.getTotalBuses());
        assertEquals(0, res.getTotalPeriodTrips());
        assertEquals(100.0, res.getOnTimePercentage());
        assertEquals(98.5, res.getGpsHealthPercentage());
    }

    @Test
    void testGetOverviewWithTrips() {
        when(tripRepository.findByStartTimeBetween(any(), any())).thenReturn(List.of(trip1, trip2));
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip2));
        when(busRepository.findByDeletedAtIsNull()).thenReturn(List.of(testBus));
        when(notificationRepository.countByTypeAndCreatedAtBetween(eq("CRITICAL"), any(), any())).thenReturn(0L);
        when(notificationRepository.countByTypeAndCreatedAtBetween(eq("WARNING"), any(), any())).thenReturn(0L);

        FleetOverviewResponse res = analyticsService.getOverview(null, null);

        assertNotNull(res);
        assertEquals(1, res.getTotalBuses());
        assertEquals(1, res.getActiveTrips());
        assertEquals(2, res.getTotalPeriodTrips());
        assertEquals(1, res.getCompletedTrips());
        assertEquals(0, res.getDelayedTrips());
        assertEquals(100.0, res.getOnTimePercentage(), 0.01);
    }

    @Test
    void testGetTripTrends() {
        when(tripRepository.findByStartTimeBetween(any(), any())).thenReturn(List.of(trip1, trip2));

        List<TripTrendResponse> trends = analyticsService.getTripTrends(null, null);

        assertNotNull(trends);
        assertFalse(trends.isEmpty());
        TripTrendResponse lastTrend = trends.get(trends.size() - 1);
        assertNotNull(lastTrend.getDate());
        assertTrue(lastTrend.getTotalTrips() >= 0);
    }

    @Test
    void testGetRoutePerformance() {
        when(tripRepository.findByStartTimeBetween(any(), any())).thenReturn(List.of(trip1, trip2));
        when(routeRepository.findByDeletedAtIsNull()).thenReturn(List.of(testRoute));

        List<RoutePerformanceResponse> list = analyticsService.getRoutePerformance(null, null);

        assertNotNull(list);
        assertEquals(1, list.size());
        RoutePerformanceResponse rp = list.get(0);
        assertEquals("Route 42", rp.getRouteName());
        assertEquals(2, rp.getTotalTrips());
        assertEquals(100.0, rp.getOnTimePercentage());
        assertEquals("EXCELLENT", rp.getPerformanceStatus());
    }

    @Test
    void testGetBusPerformance() {
        when(tripRepository.findByStartTimeBetween(any(), any())).thenReturn(List.of(trip1));
        when(busRepository.findByDeletedAtIsNull()).thenReturn(List.of(testBus));

        List<BusPerformanceResponse> list = analyticsService.getBusPerformance(null, null);

        assertNotNull(list);
        assertEquals(1, list.size());
        BusPerformanceResponse bp = list.get(0);
        assertEquals("BUS-101", bp.getBusNumber());
        assertEquals(1, bp.getTotalTrips());
        assertEquals(100.0, bp.getOnTimePercentage());
        assertEquals(15.5, bp.getTotalDistanceKm());
    }

    @Test
    void testGetDriverPerformance() {
        when(tripRepository.findByStartTimeBetween(any(), any())).thenReturn(List.of(trip1));
        when(driverRepository.findAll()).thenReturn(List.of(testDriver));

        List<DriverPerformanceResponse> list = analyticsService.getDriverPerformance(null, null);

        assertNotNull(list);
        assertEquals(1, list.size());
        DriverPerformanceResponse dp = list.get(0);
        assertEquals("John Doe", dp.getDriverName());
        assertEquals(1, dp.getAssignedTrips());
        assertEquals(100.0, dp.getOnTimePercentage());
    }

    @Test
    void testGetDelayAnalytics() {
        when(tripRepository.findByStartTimeBetween(any(), any())).thenReturn(List.of(trip1));

        DelayAnalyticsResponse res = analyticsService.getDelayAnalytics(null, null);

        assertNotNull(res);
        assertEquals(0, res.getTotalDelayedTrips());
        assertEquals(0.0, res.getAverageDelayMinutes());
        assertNotNull(res.getAffectedRoutes());
        assertNotNull(res.getDailyDelayTrend());
    }

    @Test
    void testGetDeviationAnalytics() {
        Notification devNotif = Notification.builder()
                .id(UUID.randomUUID())
                .type("CRITICAL")
                .title("Bus Route Deviation")
                .message("Bus BUS-101 has deviated from route by 350 meters")
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findByTypeAndCreatedAtBetween(eq("CRITICAL"), any(), any()))
                .thenReturn(List.of(devNotif));

        DeviationAnalyticsResponse res = analyticsService.getDeviationAnalytics(null, null);

        assertNotNull(res);
        assertEquals(1, res.getTotalDeviations());
        assertEquals(500.0, res.getDeviationThresholdMeters());
    }

    @Test
    void testGetGpsHealth() {
        LocalDateTime now = LocalDateTime.now();
        GpsDevice healthy = GpsDevice.builder()
                .id(UUID.randomUUID())
                .deviceId("DEV-01")
                .status("ONLINE")
                .lastSeen(now.minusSeconds(10))
                .build();

        GpsDevice stale = GpsDevice.builder()
                .id(UUID.randomUUID())
                .deviceId("DEV-02")
                .status("ONLINE")
                .lastSeen(now.minusSeconds(120))
                .build();

        GpsDevice offline = GpsDevice.builder()
                .id(UUID.randomUUID())
                .deviceId("DEV-03")
                .status("OFFLINE")
                .build();

        when(gpsDeviceRepository.findAll()).thenReturn(List.of(healthy, stale, offline));
        when(notificationRepository.countByTypeAndCreatedAtBetween(eq("WARNING"), any(), any())).thenReturn(1L);

        GpsHealthResponse res = analyticsService.getGpsHealth(null, null);

        assertNotNull(res);
        assertTrue(res.getGpsHealthPercentage() > 0);
        assertNotNull(res.getFleetStatus());
        assertEquals(3, res.getDeviceHealthSummary().size());
    }

    @Test
    void testGetTripAnalytics() {
        Stop stop = Stop.builder().id(UUID.randomUUID()).stopName("Main Gate").build();
        RouteStop routeStop = RouteStop.builder()
                .route(testRoute)
                .stop(stop)
                .sequenceNumber(1)
                .distanceFromStart(2.5)
                .durationFromStartMins(5)
                .build();

        TripStopEvent event = TripStopEvent.builder()
                .id(UUID.randomUUID())
                .trip(trip1)
                .stop(stop)
                .eventType("ARRIVED_AT_STOP")
                .timestamp(LocalDateTime.now().minusHours(2).plusMinutes(3))
                .build();

        TripLocation loc1 = TripLocation.builder()
                .id(UUID.randomUUID())
                .trip(trip1)
                .latitude(12.9716)
                .longitude(77.5946)
                .timestamp(LocalDateTime.now().minusHours(2))
                .speed(25.0)
                .heading(90.0)
                .accuracy(5.0)
                .trackingSource("GPS_DEVICE")
                .sourceEventId("EVT-1")
                .build();

        when(tripRepository.findById(trip1.getId())).thenReturn(Optional.of(trip1));
        when(routeStopRepository.findByRouteOrderBySequenceNumberAsc(testRoute)).thenReturn(List.of(routeStop));
        when(tripStopEventRepository.findByTripIdOrderByTimestampAsc(trip1.getId())).thenReturn(List.of(event));
        when(tripLocationRepository.findByTripIdOrderByTimestampAsc(trip1.getId())).thenReturn(List.of(loc1));

        TripAnalyticsResponse res = analyticsService.getTripAnalytics(trip1.getId());

        assertNotNull(res);
        assertEquals(trip1.getId(), res.getTripId());
        assertEquals("Route 42", res.getRouteName());
        assertEquals("BUS-101", res.getBusNumber());
        assertEquals(1, res.getStopTimeline().size());
        assertEquals("Main Gate", res.getStopTimeline().get(0).getStopName());
        assertTrue(res.getStopTimeline().get(0).isCompleted());
        assertEquals(1, res.getTelemetryTrailSample().size());
    }

    @Test
    void testGenerateCsvExport() {
        when(tripRepository.findByStartTimeBetween(any(), any())).thenReturn(List.of(trip1));
        when(routeRepository.findByDeletedAtIsNull()).thenReturn(List.of(testRoute));
        when(busRepository.findByDeletedAtIsNull()).thenReturn(List.of(testBus));
        when(driverRepository.findAll()).thenReturn(List.of(testDriver));

        String routesCsv = analyticsService.generateCsvExport("routes", null, null);
        assertNotNull(routesCsv);
        assertTrue(routesCsv.contains("Route Name,Total Trips"));

        String busesCsv = analyticsService.generateCsvExport("buses", null, null);
        assertNotNull(busesCsv);
        assertTrue(busesCsv.contains("Bus Number,Bus Code"));

        String driversCsv = analyticsService.generateCsvExport("drivers", null, null);
        assertNotNull(driversCsv);
        assertTrue(driversCsv.contains("Driver Name,Employee ID"));

        String tripsCsv = analyticsService.generateCsvExport("trips", null, null);
        assertNotNull(tripsCsv);
        assertTrue(tripsCsv.contains("Trip ID,Bus Number"));
    }

    @Test
    void testEscapeCsv_FormulaInjectionProtection() {
        // Formulas with dangerous prefixes
        assertEquals("'=SUM(A1:A2)", analyticsService.escapeCsv("=SUM(A1:A2)"));
        assertEquals("'+SUM(A1:A2)", analyticsService.escapeCsv("+SUM(A1:A2)"));
        assertEquals("'-SUM(A1:A2)", analyticsService.escapeCsv("-SUM(A1:A2)"));
        assertEquals("'@SUM(A1:A2)", analyticsService.escapeCsv("@SUM(A1:A2)"));

        // Leading whitespace with dangerous formula
        assertEquals("' =SUM(A1:A2)", analyticsService.escapeCsv(" =SUM(A1:A2)"));
        assertEquals("'   +cmd|' /C calc'!A0", analyticsService.escapeCsv("   +cmd|' /C calc'!A0"));

        // Normal strings without formula prefix
        assertEquals("Route 42", analyticsService.escapeCsv("Route 42"));
        assertEquals("BUS-101", analyticsService.escapeCsv("BUS-101"));
        assertEquals("John Doe", analyticsService.escapeCsv("John Doe"));

        // Quotes escaping alongside formula prefix
        assertEquals("'=cmd|\"\"test\"\"", analyticsService.escapeCsv("=cmd|\"test\""));

        // Null and empty strings
        assertEquals("", analyticsService.escapeCsv(null));
        assertEquals("", analyticsService.escapeCsv(""));
    }
}
