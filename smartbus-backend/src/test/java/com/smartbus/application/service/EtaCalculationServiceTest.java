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

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EtaCalculationServiceTest {

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
    private GeofencingService geofencingService;

    @InjectMocks
    private EtaCalculationService etaCalculationService;

    private Trip trip;
    private Bus bus;
    private Route route;
    private Stop stop1;
    private Stop stop2;
    private RouteStop routeStop1;
    private RouteStop routeStop2;

    @BeforeEach
    void setUp() {
        bus = new Bus();
        bus.setId(UUID.randomUUID());
        bus.setBusNumber("KA-01-F-1234");
        bus.setCurrentLatitude(12.971598);
        bus.setCurrentLongitude(77.594562);
        bus.setLastUpdated(LocalDateTime.now());

        route = new Route();
        route.setId(UUID.randomUUID());
        route.setRouteName("Campus Express");

        stop1 = new Stop();
        stop1.setId(UUID.randomUUID());
        stop1.setStopName("Main Gate");
        stop1.setLatitude(12.971598);
        stop1.setLongitude(77.594562);

        stop2 = new Stop();
        stop2.setId(UUID.randomUUID());
        stop2.setStopName("Hostel Block A");
        stop2.setLatitude(12.980000);
        stop2.setLongitude(77.600000);

        routeStop1 = new RouteStop();
        routeStop1.setRoute(route);
        routeStop1.setStop(stop1);
        routeStop1.setSequenceNumber(1);

        routeStop2 = new RouteStop();
        routeStop2.setRoute(route);
        routeStop2.setStop(stop2);
        routeStop2.setSequenceNumber(2);

        trip = new Trip();
        trip.setId(UUID.randomUUID());
        trip.setBus(bus);
        trip.setRoute(route);
        trip.setStatus("IN_PROGRESS");
        trip.setStartTime(LocalDateTime.now().minusMinutes(5));
    }

    @Test
    void testCalculateEta_NormalMovement() {
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(routeStopRepository.findByRouteOrderBySequenceNumberAsc(route))
                .thenReturn(Arrays.asList(routeStop1, routeStop2));
        when(tripStopEventRepository.findByTripIdOrderByTimestampAsc(trip.getId()))
                .thenReturn(Collections.emptyList());
        when(geofencingService.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(1500.0);

        EtaResponse response = etaCalculationService.calculateEta(trip.getId(), stop2.getId());

        assertNotNull(response);
        assertEquals("ON_TIME", response.getStatus());
        assertFalse(response.isGpsStale());
        assertFalse(response.isOffRoute());
        assertTrue(response.getMinutesRemaining() >= 0);
        assertNotNull(response.getDistanceMeters());
    }

    @Test
    void testCalculateEta_GpsStale() {
        bus.setLastUpdated(LocalDateTime.now().minusSeconds(120));

        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(routeStopRepository.findByRouteOrderBySequenceNumberAsc(route))
                .thenReturn(Arrays.asList(routeStop1, routeStop2));
        when(tripStopEventRepository.findByTripIdOrderByTimestampAsc(trip.getId()))
                .thenReturn(Collections.emptyList());
        when(geofencingService.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(1500.0);

        EtaResponse response = etaCalculationService.calculateEta(trip.getId(), stop2.getId());

        assertNotNull(response);
        assertTrue(response.isGpsStale());
        assertEquals("GPS_STALE", response.getStatus());
    }

    @Test
    void testCalculateEta_SpeedFallback() {
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        when(routeStopRepository.findByRouteOrderBySequenceNumberAsc(route))
                .thenReturn(Arrays.asList(routeStop1, routeStop2));
        when(tripStopEventRepository.findByTripIdOrderByTimestampAsc(trip.getId()))
                .thenReturn(Collections.emptyList());
        when(tripLocationRepository.findTop5ByTripIdOrderByTimestampDesc(trip.getId()))
                .thenReturn(Collections.emptyList());
        when(geofencingService.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(1500.0);

        EtaResponse response = etaCalculationService.calculateEta(trip.getId(), stop2.getId());

        assertNotNull(response);
        assertNotNull(response.getMinutesRemaining());
        assertTrue(response.getMinutesRemaining() >= 0);
    }
}
