package com.smartbus.application.service;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.dto.EtaResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RouteProgressionStateMachineTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private BusRepository busRepository;
    @Mock
    private RouteStopRepository routeStopRepository;
    @Mock
    private TripLocationRepository tripLocationRepository;
    @Mock
    private TripStopEventRepository tripStopEventRepository;
    @Mock
    private SettingRepository settingRepository;
    @Mock
    private GeofencingService mockGeofencingService;

    @InjectMocks
    private GeofencingService geofencingService;

    @InjectMocks
    private EtaCalculationService etaCalculationService;

    private Trip trip;
    private Bus bus;
    private Route route;
    private List<RouteStop> routeStops;

    private final String[] stopNames = {
            "Thirupoondi",
            "Velankanni",
            "Karaikal District",
            "Chidambaram",
            "Cuddalore",
            "Chengalpattu",
            "Tambaram",
            "Kundrathur",
            "Chennai Institute Of Technology"
    };

    private final double[][] coordinates = {
            {10.6500, 79.8000}, // 1. Thirupoondi
            {10.6800, 79.8400}, // 2. Velankanni
            {10.9200, 79.8300}, // 3. Karaikal
            {11.3900, 79.6900}, // 4. Chidambaram
            {11.7500, 79.7600}, // 5. Cuddalore
            {12.6900, 79.9800}, // 6. Chengalpattu
            {12.9200, 80.1200}, // 7. Tambaram
            {12.9700, 80.0900}, // 8. Kundrathur
            {12.9716, 80.0431}  // 9. Chennai Institute Of Technology
    };

    @BeforeEach
    void setUp() {
        bus = new Bus();
        bus.setId(UUID.randomUUID());
        bus.setBusNumber("TN-49-CIT-1001");
        bus.setCurrentLatitude(coordinates[0][0]);
        bus.setCurrentLongitude(coordinates[0][1]);
        bus.setLastUpdated(LocalDateTime.now());

        route = new Route();
        route.setId(UUID.randomUUID());
        route.setRouteName("Nagapattinam to CIT Highway Express");

        routeStops = new ArrayList<>();
        for (int i = 0; i < stopNames.length; i++) {
            Stop stop = new Stop();
            stop.setId(UUID.randomUUID());
            stop.setStopName(stopNames[i]);
            stop.setLatitude(coordinates[i][0]);
            stop.setLongitude(coordinates[i][1]);

            RouteStop rs = new RouteStop();
            rs.setRoute(route);
            rs.setStop(stop);
            rs.setSequenceNumber(i + 1);
            routeStops.add(rs);
        }

        trip = new Trip();
        trip.setId(UUID.randomUUID());
        trip.setBus(bus);
        trip.setRoute(route);
        trip.setStatus("IN_PROGRESS");
        trip.setCurrentStopSequence(1);
        trip.setStartTime(LocalDateTime.now().minusMinutes(10));

        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(routeStopRepository.findByRouteOrderBySequenceNumberAsc(route)).thenReturn(routeStops);
        when(tripStopEventRepository.findByTripIdOrderByTimestampAsc(trip.getId())).thenReturn(Collections.emptyList());
    }

    @Test
    void test1_TripStart_CurrentRequiredSequenceIs1() {
        trip.setCurrentStopSequence(1);

        EtaResponse eta = etaCalculationService.calculateLiveTripEta(trip.getId());

        assertNotNull(eta);
        assertEquals(1, eta.getNextStopSequence(), "At start, NEXT stop must be sequence 1");
        assertEquals("Thirupoondi", eta.getNextStopName(), "Next stop must be Thirupoondi");
        assertEquals(0, eta.getRouteProgress().getPassedStops(), "0 stops should be passed at start");
        assertEquals(0.0, eta.getRouteProgress().getProgressPercent(), "Progress should be 0%");

        List<EtaResponse.UpcomingStopEta> upcoming = eta.getUpcomingStops();
        assertEquals(9, upcoming.size());
        assertTrue(upcoming.get(0).isNext(), "Stop 1 must be NEXT");
        assertFalse(upcoming.get(0).isPassed(), "Stop 1 must NOT be passed");
        for (int i = 1; i < 9; i++) {
            assertFalse(upcoming.get(i).isPassed(), "Stop " + (i + 1) + " must not be passed");
            assertFalse(upcoming.get(i).isNext(), "Stop " + (i + 1) + " must not be next");
        }
    }

    @Test
    void test2_ReachStop1_AdvancesToStop2() {
        // Physical coordinates at Stop 1 (Thirupoondi)
        geofencingService.processGeofencing(trip, coordinates[0][0], coordinates[0][1]);

        verify(tripStopEventRepository, times(1)).save(argThat(event ->
                "ARRIVED_AT_STOP".equals(event.getEventType()) &&
                event.getStop().getStopName().equals("Thirupoondi")
        ));

        assertEquals(2, trip.getCurrentStopSequence(), "Reaching Stop 1 must advance cursor to 2");

        EtaResponse eta = etaCalculationService.calculateLiveTripEta(trip.getId());
        assertEquals(2, eta.getNextStopSequence(), "Next stop must now be sequence 2");
        assertEquals("Velankanni", eta.getNextStopName(), "Next stop must be Velankanni");
        assertEquals(1, eta.getRouteProgress().getPassedStops(), "1 stop should be passed");

        List<EtaResponse.UpcomingStopEta> upcoming = eta.getUpcomingStops();
        assertTrue(upcoming.get(0).isPassed(), "Stop 1 Thirupoondi must be PASSED");
        assertTrue(upcoming.get(1).isNext(), "Stop 2 Velankanni must be NEXT");
        for (int i = 2; i < 9; i++) {
            assertFalse(upcoming.get(i).isPassed(), "Stop " + (i + 1) + " must remain UPCOMING");
        }
    }

    @Test
    void test3_GPSNearStop5_WhileStop2Required_DoesNotAdvanceStop5() {
        // Driver is at Stop 2 required
        trip.setCurrentStopSequence(2);

        // GPS suddenly sends coordinates of Stop 5 (Cuddalore: 11.7500, 79.7600)
        geofencingService.processGeofencing(trip, coordinates[4][0], coordinates[4][1]);

        // Invariant: Stop 5 must NOT be marked ARRIVED_AT_STOP because current required sequence is 2!
        verify(tripStopEventRepository, never()).save(argThat(event ->
                event.getStop().getStopName().equals("Cuddalore")
        ));

        assertEquals(2, trip.getCurrentStopSequence(), "Cursor must remain at sequence 2");

        EtaResponse eta = etaCalculationService.calculateLiveTripEta(trip.getId());
        assertEquals(2, eta.getNextStopSequence(), "Next stop sequence must remain 2");
        assertEquals("Velankanni", eta.getNextStopName(), "Next stop name must remain Velankanni");

        List<EtaResponse.UpcomingStopEta> upcoming = eta.getUpcomingStops();
        assertTrue(upcoming.get(0).isPassed(), "Stop 1 must be PASSED");
        assertTrue(upcoming.get(1).isNext(), "Stop 2 must be NEXT");
        for (int i = 2; i < 9; i++) {
            assertFalse(upcoming.get(i).isPassed(), "Stop " + (i + 1) + " must remain UPCOMING");
            assertFalse(upcoming.get(i).isNext());
        }
    }

    @Test
    void test4_ReachStop2_AdvancesToStop3() {
        trip.setCurrentStopSequence(2);

        geofencingService.processGeofencing(trip, coordinates[1][0], coordinates[1][1]);

        verify(tripStopEventRepository, times(1)).save(argThat(event ->
                "ARRIVED_AT_STOP".equals(event.getEventType()) &&
                event.getStop().getStopName().equals("Velankanni")
        ));

        assertEquals(3, trip.getCurrentStopSequence(), "Cursor must advance to sequence 3");

        EtaResponse eta = etaCalculationService.calculateLiveTripEta(trip.getId());
        assertEquals(3, eta.getNextStopSequence());
        assertEquals("Karaikal District", eta.getNextStopName());
        assertEquals(2, eta.getRouteProgress().getPassedStops());
    }

    @Test
    void test5_JumpNearStop9_WhileStop3Required_DoesNotAdvanceStop9() {
        trip.setCurrentStopSequence(3);

        // GPS jumps to final stop CIT (Stop 9)
        geofencingService.processGeofencing(trip, coordinates[8][0], coordinates[8][1]);

        verify(tripStopEventRepository, never()).save(argThat(event ->
                event.getStop().getStopName().equals("Chennai Institute Of Technology")
        ));

        assertEquals(3, trip.getCurrentStopSequence(), "Cursor must NOT jump to 9 or 10");
        EtaResponse eta = etaCalculationService.calculateLiveTripEta(trip.getId());
        assertEquals("Karaikal District", eta.getNextStopName());
        assertFalse(eta.getUpcomingStops().get(8).isPassed(), "Stop 9 cannot be PASSED");
    }

    @Test
    void test6_OutOfOrderGpsPackets_NeverMoveSequenceBackwards() {
        trip.setCurrentStopSequence(4);

        // Send GPS corresponding to Stop 1 (Thirupoondi)
        geofencingService.processGeofencing(trip, coordinates[0][0], coordinates[0][1]);

        // Cursor must remain monotonic (never decrease)
        assertTrue(trip.getCurrentStopSequence() >= 4, "Cursor sequence must never move backwards");
        assertEquals(4, trip.getCurrentStopSequence());
    }

    @Test
    void test7_DuplicateGpsPacket_DoesNotAdvanceTwice() {
        trip.setCurrentStopSequence(4);

        // Arrive at Stop 4 (Chidambaram)
        geofencingService.processGeofencing(trip, coordinates[3][0], coordinates[3][1]);
        assertEquals(5, trip.getCurrentStopSequence(), "Advances to 5");

        // Immediately send same coordinates again (duplicate packet)
        geofencingService.processGeofencing(trip, coordinates[3][0], coordinates[3][1]);
        assertEquals(5, trip.getCurrentStopSequence(), "Duplicate packet must not advance to 6");
    }

    @Test
    void test8_CompleteAll9Stops_ProgressionReaches100Percent() {
        // Sequentially advance through all 9 stops
        for (int i = 0; i < 9; i++) {
            trip.setCurrentStopSequence(i + 1);
            geofencingService.processGeofencing(trip, coordinates[i][0], coordinates[i][1]);
            assertEquals(i + 2, trip.getCurrentStopSequence());
        }

        assertEquals(10, trip.getCurrentStopSequence(), "After stop 9, cursor is 10");

        EtaResponse eta = etaCalculationService.calculateLiveTripEta(trip.getId());
        assertEquals(9, eta.getRouteProgress().getPassedStops(), "All 9 stops passed");
        assertEquals(100.0, eta.getRouteProgress().getProgressPercent(), "Progress must be 100%");

        for (EtaResponse.UpcomingStopEta us : eta.getUpcomingStops()) {
            assertTrue(us.isPassed(), us.getStopName() + " must be passed");
            assertFalse(us.isNext(), us.getStopName() + " cannot be next");
        }
    }
}
