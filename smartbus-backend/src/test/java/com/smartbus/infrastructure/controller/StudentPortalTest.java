package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuditLogService;
import com.smartbus.application.service.EtaService;
import com.smartbus.application.service.GeofencingService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StudentPortalTest {

    @Mock private StudentRepository studentRepository;
    @Mock private UserRepository userRepository;
    @Mock private BusRepository busRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private StopRepository stopRepository;
    @Mock private RouteStopRepository routeStopRepository;
    @Mock private TripRepository tripRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private EtaService etaService;
    @Mock private GeofencingService geofencingService;
    @Mock private com.smartbus.application.service.EtaCalculationService etaCalculationService;
    @Mock private com.smartbus.infrastructure.adapter.jpa.TripLocationRepository tripLocationRepository;

    @InjectMocks
    private StudentPortalController studentPortalController;

    private User studentUser;
    private Student student;
    private Stop preferredStop;
    private Bus bus;
    private Route route;
    private Trip trip;

    @BeforeEach
    public void setUp() {
        studentUser = User.builder()
                .id(UUID.randomUUID())
                .email("student@smartbus.ai")
                .firstName("Pragadeesh")
                .lastName("Student")
                .isActive(true)
                .role(Role.STUDENT)
                .build();

        preferredStop = Stop.builder()
                .id(UUID.randomUUID())
                .stopName("Tambaram")
                .latitude(12.920000)
                .longitude(80.221000)
                .build();

        student = Student.builder()
                .id(UUID.randomUUID())
                .user(studentUser)
                .studentId("STU-DEMO-001")
                .department("Computer Science")
                .batch("2026")
                .preferredStop(preferredStop)
                .homeLatitude(12.925000)
                .homeLongitude(80.225000)
                .homeAddress("123 Home St")
                .favoriteBuses(new HashSet<>())
                .notificationPreferences("APPROACHING,ARRIVED")
                .build();

        bus = Bus.builder()
                .id(UUID.randomUUID())
                .busNumber("BUS-101")
                .busCode("SB-BUS-7X4K92")
                .status("ACTIVE")
                .capacity(40)
                .currentLatitude(12.921000)
                .currentLongitude(80.222000)
                .build();

        route = Route.builder()
                .id(UUID.randomUUID())
                .routeName("CIT -> Tambaram")
                .status("ACTIVE")
                .distance(12.5)
                .build();

        trip = Trip.builder()
                .id(UUID.randomUUID())
                .bus(bus)
                .route(route)
                .driver(Driver.builder().user(User.builder().firstName("Kumar").lastName("Driver").build()).build())
                .status("IN_PROGRESS")
                .build();

        // Setup security context
        UserPrincipal principal = new UserPrincipal(studentUser);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    public void testGetDashboard_Success() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(tripRepository.findByStatusIn(any())).thenReturn(Collections.singletonList(trip));
        when(etaCalculationService.calculateEta(any(), any())).thenReturn(com.smartbus.infrastructure.dto.EtaResponse.builder()
                .busNumber("BUS-101")
                .status("ON_TIME")
                .minutesRemaining(12)
                .currentStopName("In Transit")
                .nextStopName("Tambaram")
                .build());

        ResponseEntity<StudentPortalController.StudentDashboardResponse> response = studentPortalController.getDashboard();

        assertNotNull(response.getBody());
        assertEquals("Tambaram", response.getBody().getHomeStop());
        assertEquals("BUS-101", response.getBody().getYourBus().getBusNumber());
        assertEquals(12, response.getBody().getHomeEta());
    }

    @Test
    public void testFavoriteBus_Success() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(busRepository.findById(any())).thenReturn(Optional.of(bus));

        ResponseEntity<Map<String, Boolean>> response = studentPortalController.favoriteBus(bus.getId());

        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("success"));
        assertTrue(student.getFavoriteBuses().contains(bus));
        verify(studentRepository, times(1)).save(student);
    }

    @Test
    public void testUnfavoriteBus_Success() {
        student.getFavoriteBuses().add(bus);
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(busRepository.findById(any())).thenReturn(Optional.of(bus));

        ResponseEntity<Map<String, Boolean>> response = studentPortalController.unfavoriteBus(bus.getId());

        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("success"));
        assertFalse(student.getFavoriteBuses().contains(bus));
        verify(studentRepository, times(1)).save(student);
    }

    @Test
    public void testUpdateHomeLocation_Success() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));

        StudentPortalController.HomeLocationRequest req = new StudentPortalController.HomeLocationRequest(13.001, 80.002, "New Address");
        ResponseEntity<StudentPortalController.StudentProfileResponse> response = studentPortalController.updateHomeLocation(req);

        assertNotNull(response.getBody());
        assertEquals("New Address", response.getBody().getHomeAddress());
        assertEquals(13.001, response.getBody().getHomeLatitude());
        verify(studentRepository, times(1)).save(student);
    }

    @Test
    public void testUpdatePreferredStop_Success() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(stopRepository.findById(any())).thenReturn(Optional.of(preferredStop));

        Map<String, String> body = new HashMap<>();
        body.put("stopId", preferredStop.getId().toString());

        ResponseEntity<StudentPortalController.StudentProfileResponse> response = studentPortalController.updatePreferredStop(body);

        assertNotNull(response.getBody());
        assertEquals("Tambaram", response.getBody().getPreferredStopName());
        verify(studentRepository, times(1)).save(student);
    }

    @Test
    public void testMarkNotificationAsRead_Success() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .user(studentUser)
                .title("BUS-101 APPROACHING")
                .message("Approaching Tambaram")
                .isRead(false)
                .build();

        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(notificationRepository.findById(any())).thenReturn(Optional.of(notification));

        ResponseEntity<Map<String, Boolean>> response = studentPortalController.markNotificationAsRead(notification.getId());

        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("success"));
        assertTrue(notification.isRead());
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    public void testMarkNotificationAsRead_Unauthorized_ThrowsException() {
        User otherUser = User.builder().id(UUID.randomUUID()).email("other@smartbus.ai").build();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .user(otherUser)
                .title("Alert")
                .isRead(false)
                .build();

        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(notificationRepository.findById(any())).thenReturn(Optional.of(notification));

        assertThrows(BadRequestException.class, () -> studentPortalController.markNotificationAsRead(notification.getId()));
    }

    @Test
    public void testGetBusStops_Success() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(tripRepository.findByBusIdAndStatusIn(any(), any())).thenReturn(Collections.emptyList().isEmpty() ? Optional.of(trip) : Optional.empty());
        
        RouteStop rs = RouteStop.builder()
                .route(route)
                .stop(preferredStop)
                .sequenceNumber(1)
                .build();
        when(routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(route.getId())).thenReturn(Collections.singletonList(rs));

        ResponseEntity<List<DriverPortalController.RouteProgressResponse.StopInfo>> res = studentPortalController.getBusStops(bus.getId());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().size());
        assertEquals("Tambaram", res.getBody().get(0).getStopName());
    }

    @Test
    public void testSetPreferredStopPost_Success() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(stopRepository.findById(preferredStop.getId())).thenReturn(Optional.of(preferredStop));

        ResponseEntity<Map<String, Object>> res = studentPortalController.setPreferredStopPost(preferredStop.getId());
        assertNotNull(res.getBody());
        assertTrue((Boolean) res.getBody().get("success"));
        assertEquals("Tambaram", res.getBody().get("preferredStopName"));
        verify(studentRepository, times(1)).save(student);
    }

    @Test
    public void testGetPreferences_Success() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));

        ResponseEntity<StudentPortalController.StudentPreferencesResponse> res = studentPortalController.getPreferences();
        assertNotNull(res.getBody());
        assertEquals("Tambaram", res.getBody().getPreferredStopName());
    }

    @Test
    public void testSavePreferences_Success() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(routeRepository.findById(route.getId())).thenReturn(Optional.of(route));
        when(busRepository.findById(bus.getId())).thenReturn(Optional.of(bus));
        when(stopRepository.findById(preferredStop.getId())).thenReturn(Optional.of(preferredStop));

        Schedule sched = Schedule.builder().id(UUID.randomUUID()).bus(bus).route(route).status("ACTIVE").build();
        when(scheduleRepository.findByRouteIdAndDeletedAtIsNull(route.getId())).thenReturn(Collections.singletonList(sched));

        RouteStop rs = RouteStop.builder().route(route).stop(preferredStop).sequenceNumber(1).build();
        when(routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(route.getId())).thenReturn(Collections.singletonList(rs));

        StudentPortalController.StudentPreferencesRequest req = StudentPortalController.StudentPreferencesRequest.builder()
                .routeId(route.getId())
                .busId(bus.getId())
                .stopId(preferredStop.getId())
                .build();

        ResponseEntity<StudentPortalController.StudentPreferencesResponse> res = studentPortalController.savePreferences(req);
        assertNotNull(res.getBody());
        assertEquals("BUS-101", res.getBody().getPreferredBusNumber());
        assertEquals("Tambaram", res.getBody().getPreferredStopName());
        verify(studentRepository, times(1)).save(student);
    }

    @Test
    public void testSavePreferences_BusNotOnRoute_ThrowsBadRequest() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));
        when(routeRepository.findById(route.getId())).thenReturn(Optional.of(route));
        when(busRepository.findById(bus.getId())).thenReturn(Optional.of(bus));

        // No schedule serving this bus on route
        when(scheduleRepository.findByRouteIdAndDeletedAtIsNull(route.getId())).thenReturn(Collections.emptyList());

        StudentPortalController.StudentPreferencesRequest req = StudentPortalController.StudentPreferencesRequest.builder()
                .routeId(route.getId())
                .busId(bus.getId())
                .build();

        assertThrows(BadRequestException.class, () -> studentPortalController.savePreferences(req));
    }

    @Test
    public void testGetSchedules_Success() {
        when(studentRepository.findByUser(any())).thenReturn(Optional.of(student));

        Schedule sched = Schedule.builder()
                .id(UUID.randomUUID())
                .bus(bus)
                .route(route)
                .departureTime(LocalTime.of(8, 0))
                .arrivalTime(LocalTime.of(8, 45))
                .status("ACTIVE")
                .build();

        when(scheduleRepository.findByDeletedAtIsNull()).thenReturn(Collections.singletonList(sched));
        when(tripRepository.findByStatusIn(any())).thenReturn(Collections.emptyList());

        ResponseEntity<List<StudentPortalController.StudentScheduleItem>> res = studentPortalController.getSchedules();
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().size());
        assertEquals("BUS-101", res.getBody().get(0).getBusNumber());
    }
}
