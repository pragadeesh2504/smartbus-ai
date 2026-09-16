package com.smartbus.application.service;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.RouteStopRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EtaPredictorServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private RouteStopRepository routeStopRepository;

    @Mock
    private GeofencingService geofencingService;

    @InjectMocks
    private EtaPredictorService etaPredictorService;

    @Test
    void predictEtaMins_Success() {
        UUID tripId = UUID.randomUUID();
        UUID targetStopId = UUID.randomUUID();
        UUID currentStopId = UUID.randomUUID();

        Bus bus = new Bus();
        bus.setCurrentLatitude(12.971);
        bus.setCurrentLongitude(77.594);

        Route route = new Route();
        
        Trip trip = new Trip();
        trip.setBus(bus);
        trip.setRoute(route);

        Stop stopCurrent = new Stop();
        stopCurrent.setId(currentStopId);
        stopCurrent.setLatitude(12.971);
        stopCurrent.setLongitude(77.594);

        Stop stopTarget = new Stop();
        stopTarget.setId(targetStopId);
        stopTarget.setLatitude(12.980);
        stopTarget.setLongitude(77.600);

        RouteStop rsCurrent = new RouteStop();
        rsCurrent.setStop(stopCurrent);
        rsCurrent.setSequenceNumber(1);
        rsCurrent.setDistanceFromStart(0.0);
        rsCurrent.setDurationFromStartMins(0);

        RouteStop rsTarget = new RouteStop();
        rsTarget.setStop(stopTarget);
        rsTarget.setSequenceNumber(2);
        rsTarget.setDistanceFromStart(2.5); // 2.5 km away
        rsTarget.setDurationFromStartMins(5);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(routeStopRepository.findByRouteOrderBySequenceNumberAsc(route))
                .thenReturn(Arrays.asList(rsCurrent, rsTarget));

        // Stub distance calls for geofencing closest stop lookup
        when(geofencingService.calculateDistance(12.971, 77.594, 12.971, 77.594)).thenReturn(0.0);
        when(geofencingService.calculateDistance(12.971, 77.594, 12.980, 77.600)).thenReturn(2500.0);

        int predictedMins = etaPredictorService.predictEtaMins(tripId, targetStopId);

        // 2.5 km at 0.5 km/min (30 km/h) = 5 mins.
        // Let's assert it computes correctly within a margin.
        assertTrue(predictedMins >= 4 && predictedMins <= 8);
    }
}
