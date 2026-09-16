package com.smartbus.application.service;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.DriverNotificationRepository;
import com.smartbus.infrastructure.adapter.jpa.NotificationRepository;
import com.smartbus.infrastructure.adapter.jpa.StudentRepository;
import com.smartbus.websocket.LiveLocationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SmartNotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DriverNotificationRepository driverNotificationRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LiveLocationWebSocketHandler webSocketHandler;

    @InjectMocks
    private SmartNotificationService smartNotificationService;

    private Trip trip;
    private Bus bus;
    private Driver driver;
    private User driverUser;
    private Stop stop;

    @BeforeEach
    void setUp() {
        bus = new Bus();
        bus.setId(UUID.randomUUID());
        bus.setBusNumber("KA-01-F-1234");

        driverUser = new User();
        driverUser.setFirstName("Ramesh");
        driverUser.setLastName("Kumar");

        driver = new Driver();
        driver.setId(UUID.randomUUID());
        driver.setUser(driverUser);

        stop = new Stop();
        stop.setId(UUID.randomUUID());
        stop.setStopName("Library Stop");

        trip = new Trip();
        trip.setId(UUID.randomUUID());
        trip.setBus(bus);
        trip.setDriver(driver);
    }

    @Test
    void testHandleTripDelayed_Deduplication() {
        when(studentRepository.findByPreferredStopId(stop.getId())).thenReturn(Collections.emptyList());
        when(studentRepository.findStudentsByFavoriteBusId(bus.getId())).thenReturn(Collections.emptyList());

        // First call should dispatch
        smartNotificationService.handleTripDelayed(trip, 10, stop);
        verify(webSocketHandler, times(1)).broadcast(any());

        // Second call with same trip & stop within cooldown should be deduplicated
        smartNotificationService.handleTripDelayed(trip, 12, stop);
        verify(webSocketHandler, times(1)).broadcast(any());
    }

    @Test
    void testHandleRouteDeviation_OffRouteAndReturn() {
        when(studentRepository.findStudentsByFavoriteBusId(bus.getId())).thenReturn(Collections.emptyList());

        // 1. Off-route alert
        smartNotificationService.handleRouteDeviation(trip, 650.0, true);
        verify(driverNotificationRepository, times(1)).save(any());
        verify(webSocketHandler, times(1)).broadcast(any());

        // 2. Return to route
        smartNotificationService.handleRouteDeviation(trip, 50.0, false);
        verify(webSocketHandler, times(2)).broadcast(any());
    }

    @Test
    void testHandleTripStarted_SuccessAndDeduplication() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setUser(driverUser);
        student.setNotificationPreferences("ALL");

        when(studentRepository.findStudentsByFavoriteBusId(bus.getId())).thenReturn(Collections.singletonList(student));

        // First call triggers notification and broadcast
        smartNotificationService.handleTripStarted(trip);
        verify(notificationRepository, times(1)).save(any());
        verify(webSocketHandler, atLeast(1)).broadcast(any());

        // Second call is deduplicated
        smartNotificationService.handleTripStarted(trip);
        verify(notificationRepository, times(1)).save(any());
    }
}
