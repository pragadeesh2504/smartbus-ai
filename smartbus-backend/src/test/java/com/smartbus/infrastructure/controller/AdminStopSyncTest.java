package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuditLogService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Route;
import com.smartbus.domain.model.RouteStop;
import com.smartbus.domain.model.Stop;
import com.smartbus.infrastructure.adapter.jpa.RouteRepository;
import com.smartbus.infrastructure.adapter.jpa.RouteStopRepository;
import com.smartbus.infrastructure.adapter.jpa.StopRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.RouteStopDto;
import com.smartbus.infrastructure.dto.StopDto;
import com.smartbus.infrastructure.mapper.RouteStopMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminStopSyncTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private StopRepository stopRepository;

    @Mock
    private RouteStopRepository routeStopRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RouteStopMapper routeStopMapper;

    @InjectMocks
    private AdminStopController adminStopController;

    private UUID routeId;
    private Route route;

    @BeforeEach
    void setUp() {
        routeId = UUID.randomUUID();
        route = Route.builder()
                .id(routeId)
                .routeName("Campus Express")
                .startPoint("Hostel Gate")
                .endPoint("Tech Park")
                .status("ACTIVE")
                .build();
    }

    @Test
    void syncStops_AtomicReplacement_Success() {
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(route));

        RouteStop oldStop = RouteStop.builder()
                .route(route)
                .stop(Stop.builder().id(UUID.randomUUID()).stopName("Old Stop").build())
                .sequenceNumber(1)
                .build();
        when(routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(routeId)).thenReturn(Collections.singletonList(oldStop));

        RouteStopDto s1 = RouteStopDto.builder()
                .stop(StopDto.builder().stopName("Milestone A").latitude(13.0).longitude(80.0).build())
                .distanceFromStart(0.0)
                .durationFromStartMins(0)
                .expectedArrivalTime(LocalTime.of(8, 0))
                .expectedDepartureTime(LocalTime.of(8, 5))
                .build();

        RouteStopDto s2 = RouteStopDto.builder()
                .stop(StopDto.builder().stopName("Milestone B").latitude(13.1).longitude(80.1).build())
                .distanceFromStart(5.5)
                .durationFromStartMins(15)
                .expectedArrivalTime(LocalTime.of(8, 20))
                .expectedDepartureTime(LocalTime.of(8, 25))
                .build();

        List<RouteStopDto> dtos = Arrays.asList(s1, s2);

        when(stopRepository.findByStopName(anyString())).thenReturn(Optional.empty());
        when(stopRepository.save(any(Stop.class))).thenAnswer(i -> {
            Stop s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        when(routeStopRepository.save(any(RouteStop.class))).thenAnswer(i -> i.getArgument(0));
        when(routeStopMapper.toDto(any(RouteStop.class))).thenAnswer(i -> {
            RouteStop rs = i.getArgument(0);
            return RouteStopDto.builder()
                    .sequenceNumber(rs.getSequenceNumber())
                    .stop(StopDto.builder().stopName(rs.getStop().getStopName()).build())
                    .build();
        });

        ResponseEntity<ApiResponse<List<RouteStopDto>>> response = adminStopController.syncStops(routeId, dtos, null);

        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals(2, response.getBody().getData().size());
        assertEquals(1, response.getBody().getData().get(0).getSequenceNumber());
        assertEquals(2, response.getBody().getData().get(1).getSequenceNumber());

        verify(routeStopRepository, times(1)).deleteAll(anyList());
        verify(routeStopRepository, times(1)).flush();
        verify(routeStopRepository, times(2)).save(any(RouteStop.class));
    }

    @Test
    void syncStops_InvalidCoordinates_ThrowsException() {
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(route));

        RouteStopDto invalidStop = RouteStopDto.builder()
                .stop(StopDto.builder().stopName("Invalid Point").latitude(100.0).longitude(80.0).build())
                .build();

        assertThrows(BadRequestException.class, () -> {
            adminStopController.syncStops(routeId, Collections.singletonList(invalidStop), null);
        });
    }

    @Test
    void syncStops_MissingRoute_ThrowsException() {
        when(routeRepository.findById(routeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            adminStopController.syncStops(routeId, Collections.emptyList(), null);
        });
    }
}
