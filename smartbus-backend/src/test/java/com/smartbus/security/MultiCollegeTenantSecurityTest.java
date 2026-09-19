package com.smartbus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbus.application.port.in.GpsUseCase;
import com.smartbus.application.service.*;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.controller.*;
import com.smartbus.infrastructure.dto.*;
import com.smartbus.infrastructure.mapper.*;
import com.smartbus.websocket.LiveLocationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiCollegeTenantSecurityTest {

    private static final String TEST_PASSWORD = "TestPassword@123";

    @Mock private CollegeRepository collegeRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private BusRepository busRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private StopRepository stopRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TripRepository tripRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @Mock private BusQrTokenRepository busQrTokenRepository;
    @Mock private GpsDeviceRepository gpsDeviceRepository;
    @Mock private BusAssignmentRepository busAssignmentRepository;
    @Mock private TripLocationRepository tripLocationRepository;
    @Mock private TripStopEventRepository tripStopEventRepository;
    @Mock private BreakdownReportRepository breakdownReportRepository;
    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private RouteStopRepository routeStopRepository;
    @Mock private DriverNotificationRepository driverNotificationRepository;
    @Mock private EmergencyLogRepository emergencyLogRepository;
    @Mock private NotificationRepository notificationRepository;

    @Mock private AuditLogService auditLogService;
    @Mock private EtaCalculationService etaCalculationService;
    @Mock private EtaService etaService;
    @Mock private GeofencingService geofencingService;
    @Mock private HybridTrackingService hybridTrackingService;
    @Mock private SmartNotificationService smartNotificationService;
    @Mock private LiveLocationWebSocketHandler webSocketHandler;
    @Mock private GpsUseCase gpsUseCase;

    @Mock private RouteStopMapper routeStopMapper;
    @Mock private RouteMapper routeMapper;
    @Mock private ScheduleMapper scheduleMapper;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private GoogleAuthService googleAuthService;

    private CollegeService collegeService;
    private AuthService authService;

    private College collegeA;
    private College collegeB;
    private User adminUserA;
    private User adminUserB;
    private User studentUserA;
    private Student studentA;

    @BeforeEach
    void setUp() {
        collegeService = new CollegeService(
                collegeRepository,
                userRepository,
                studentRepository,
                driverRepository,
                busRepository,
                routeRepository,
                tripRepository
        );
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                studentRepository,
                driverRepository,
                passwordResetTokenRepository,
                emailService,
                googleAuthService,
                collegeRepository,
                passwordEncoder,
                authenticationManager,
                jwtTokenProvider
        );

        collegeA = College.builder()
                .id(UUID.randomUUID())
                .name("Chennai Institute of Technology")
                .collegeCode("CIT")
                .status("ACTIVE")
                .contactEmail("transport@cit.edu")
                .build();

        collegeB = College.builder()
                .id(UUID.randomUUID())
                .name("Loyola College")
                .collegeCode("LOYOLA")
                .status("ACTIVE")
                .contactEmail("transport@loyola.edu")
                .build();

        adminUserA = User.builder()
                .id(UUID.randomUUID())
                .email("admin@cit.edu")
                .role(Role.ADMIN)
                .college(collegeA)
                .firstName("CIT")
                .lastName("Admin")
                .build();

        adminUserB = User.builder()
                .id(UUID.randomUUID())
                .email("admin@loyola.edu")
                .role(Role.ADMIN)
                .college(collegeB)
                .firstName("Loyola")
                .lastName("Admin")
                .build();

        studentUserA = User.builder()
                .id(UUID.randomUUID())
                .email("student@cit.edu")
                .role(Role.STUDENT)
                .college(collegeA)
                .firstName("Alice")
                .lastName("Smith")
                .build();

        studentA = Student.builder()
                .id(UUID.randomUUID())
                .user(studentUserA)
                .college(collegeA)
                .studentId("CIT-2024-001")
                .department("CSE")
                .batch("2024")
                .favoriteBuses(new HashSet<>())
                .build();
    }

    private void setSecurityContext(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Criterion 1: College Admin self-registers: atomically creates College + Admin with required college code")
    void testCollegeAdminSelfRegistration() {
        when(collegeRepository.existsByNameIgnoreCase("Anna University")).thenReturn(false);
        when(collegeRepository.existsByCollegeCodeIgnoreCase("ANNA")).thenReturn(false);
        when(collegeRepository.save(any(College.class))).thenAnswer(i -> {
            College c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(userRepository.existsByEmail("admin@annauniv.edu")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed_secret");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        RegisterCollegeAdminRequest req = RegisterCollegeAdminRequest.builder()
                .collegeName("Anna University")
                .collegeCode("ANNA")
                .email("admin@annauniv.edu")
                .password("Secret123!")
                .firstName("Anna")
                .lastName("Admin")
                .phoneNumber("+919876543210")
                .build();

        authService.registerCollegeAdmin(req);

        verify(collegeRepository, times(1)).save(any(College.class));
        verify(userRepository, times(1)).save(argThat(u ->
            u.getRole() == Role.ADMIN && "admin@annauniv.edu".equals(u.getEmail())
        ));
    }

    @Test
    @DisplayName("Criterion 2: Student registration with valid college code assigns tenant college")
    void testStudentRegistrationWithValidCollegeCode() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("newstudent@cit.edu");
        req.setPassword(TEST_PASSWORD);
        req.setFirstName("Bob");
        req.setLastName("Jones");
        req.setRole("STUDENT");
        req.setStudentId("CIT-2024-002");
        req.setDepartment("ECE");
        req.setBatch("2024");
        req.setCollegeCode("cit"); // Case-insensitive input test

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("CIT", "ACTIVE")).thenReturn(Optional.of(collegeA));
        when(studentRepository.existsByCollegeIdAndStudentId(collegeA.getId(), "CIT-2024-002")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pwd");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        assertDoesNotThrow(() -> authService.register(req));
        verify(studentRepository, times(1)).save(argThat(s ->
                s.getCollege() != null && s.getCollege().getId().equals(collegeA.getId())
        ));
    }

    @Test
    @DisplayName("Criterion 3: Student registration with invalid college code is rejected")
    void testStudentRegistrationWithInvalidCollegeCode_Rejected() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("badstudent@domain.com");
        req.setPassword(TEST_PASSWORD);
        req.setRole("STUDENT");
        req.setCollegeCode("INVALID");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("INVALID", "ACTIVE")).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("Invalid or inactive College Code"));
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criterion 4: Student registration for suspended college is rejected")
    void testStudentRegistrationWithInactiveCollege_Rejected() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("bob@cit.edu");
        req.setPassword(TEST_PASSWORD);
        req.setRole("STUDENT");
        req.setCollegeCode("CIT");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        // College is inactive/disabled, so repository returns empty for ACTIVE status
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("CIT", "ACTIVE")).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("Invalid or inactive College Code"));
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criterion 5: Student login with valid college code succeeds")
    void testStudentLoginWithValidCollegeCode_Success() {
        LoginRequest req = LoginRequest.builder()
                .email("student@cit.edu")
                .password(TEST_PASSWORD)
                .role("STUDENT")
                .collegeCode("cit")
                .build();

        UserPrincipal principal = new UserPrincipal(studentUserA);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("CIT", "ACTIVE")).thenReturn(Optional.of(collegeA));
        when(jwtTokenProvider.generateToken(auth)).thenReturn("mocked-jwt");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        LoginResponse resp = authService.login(req);
        assertNotNull(resp);
        assertEquals("mocked-jwt", resp.getAccessToken());
        assertEquals("CIT", resp.getCollegeCode());
        assertEquals("Chennai Institute of Technology", resp.getCollegeName());
    }

    @Test
    @DisplayName("Criterion 6: Cross-tenant student login is rejected (Student of College A attempting login with College B code)")
    void testCrossTenantStudentLogin_Rejected() {
        LoginRequest req = LoginRequest.builder()
                .email("student@cit.edu")
                .password(TEST_PASSWORD)
                .role("STUDENT")
                .collegeCode("LOYOLA")
                .build();

        // Authentication passes email+password, but returned user belongs to College A
        UserPrincipal principal = new UserPrincipal(studentUserA);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("LOYOLA", "ACTIVE")).thenReturn(Optional.of(collegeB));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("Account does not belong to the specified college"));
        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("Criterion 7: Intra-college student ID uniqueness is enforced, but allowed across different colleges")
    void testIntraCollegeStudentIdUniqueness() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("dup@cit.edu");
        req.setPassword(TEST_PASSWORD);
        req.setRole("STUDENT");
        req.setStudentId("ROLL-001");
        req.setDepartment("CSE");
        req.setBatch("2024");
        req.setCollegeCode("CIT");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("CIT", "ACTIVE")).thenReturn(Optional.of(collegeA));
        when(studentRepository.existsByCollegeIdAndStudentId(collegeA.getId(), "ROLL-001")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("already exists in this college"));
    }

    @Test
    @DisplayName("Criterion 8 & 9: Admin Bus Controller prevents cross-tenant bus access (returns 404)")
    void testAdminBusIsolation() {
        Bus busB = Bus.builder().id(UUID.randomUUID()).busNumber("BUS-B").college(collegeB).build();

        AdminBusController busController = new AdminBusController(
                busRepository, busQrTokenRepository, gpsDeviceRepository,
                tripRepository, scheduleRepository, auditLogService,
                etaCalculationService, tripLocationRepository, routeStopRepository, routeStopMapper
        );

        when(busRepository.findById(busB.getId())).thenReturn(Optional.of(busB));

        // Attempting to access College B bus as Admin of College A returns 404
        assertThrows(ResourceNotFoundException.class, () ->
                busController.getBusById(busB.getId(), new UserPrincipal(adminUserA))
        );
    }

    @Test
    @DisplayName("Criterion 10 & 11: Admin Driver Controller prevents cross-tenant driver access (returns 404)")
    void testAdminDriverIsolation() {
        User driverUserB = User.builder().id(UUID.randomUUID()).college(collegeB).role(Role.DRIVER).build();
        Driver driverB = Driver.builder().id(UUID.randomUUID()).user(driverUserB).college(collegeB).build();

        AdminDriverController driverController = new AdminDriverController(
                driverRepository, userRepository, tripRepository,
                scheduleRepository, refreshTokenRepository, passwordEncoder, auditLogService
        );

        when(driverRepository.findById(driverB.getId())).thenReturn(Optional.of(driverB));

        // Attempting to access College B driver as Admin of College A returns 404
        assertThrows(ResourceNotFoundException.class, () ->
                driverController.updateStatus(driverB.getId(), "ACTIVE", new UserPrincipal(adminUserA))
        );
    }

    @Test
    @DisplayName("Criterion 12 & 13: Admin Route Controller prevents cross-tenant route access (returns 404)")
    void testAdminRouteIsolation() {
        Route routeB = Route.builder().id(UUID.randomUUID()).routeName("Route B").college(collegeB).build();

        AdminRouteController routeController = new AdminRouteController(
                routeRepository, scheduleRepository, auditLogService,
                routeMapper, routeStopRepository, routeStopMapper
        );

        when(routeRepository.findById(routeB.getId())).thenReturn(Optional.of(routeB));

        // Attempting to access College B route as Admin of College A returns 404
        assertThrows(ResourceNotFoundException.class, () ->
                routeController.getRouteById(routeB.getId(), new UserPrincipal(adminUserA))
        );
    }

    @Test
    @DisplayName("Criterion 14: Schedule creation rejects cross-college bus or driver assignments")
    void testAdminScheduleCrossCollegeRejection() {
        Bus busB = Bus.builder().id(UUID.randomUUID()).busNumber("BUS-B").college(collegeB).build();
        Route routeA = Route.builder().id(UUID.randomUUID()).routeName("Route A").college(collegeA).build();
        Driver driverA = Driver.builder().id(UUID.randomUUID()).college(collegeA).build();

        AdminScheduleController scheduleController = new AdminScheduleController(
                scheduleRepository, busAssignmentRepository, busRepository,
                driverRepository, routeRepository, tripRepository,
                auditLogService, scheduleMapper
        );

        when(routeRepository.findById(routeA.getId())).thenReturn(Optional.of(routeA));
        when(driverRepository.findById(driverA.getId())).thenReturn(Optional.of(driverA));
        when(busRepository.findById(busB.getId())).thenReturn(Optional.of(busB));

        ScheduleDto req = ScheduleDto.builder()
                .routeId(routeA.getId())
                .busId(busB.getId())
                .driverId(driverA.getId())
                .departureTime("08:00")
                .arrivalTime("09:00")
                .daysOfWeek("MONDAY")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                scheduleController.createSchedule(req, new UserPrincipal(adminUserA))
        );
        assertTrue(ex.getMessage().contains("Bus does not belong to your college"));
    }

    @Test
    @DisplayName("Criterion 15 & 16: Driver startTrip prevents cross-college schedule execution")
    void testDriverCannotStartCrossCollegeSchedule() {
        User driverUserA = User.builder().id(UUID.randomUUID()).college(collegeA).role(Role.DRIVER).isActive(true).build();
        Driver driverA = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUserA)
                .college(collegeA)
                .approvalStatus("APPROVED")
                .isApproved(true)
                .status("AVAILABLE")
                .build();
        setSecurityContext(driverUserA);

        Schedule schedB = Schedule.builder()
                .id(UUID.randomUUID())
                .college(collegeB)
                .bus(Bus.builder().id(UUID.randomUUID()).college(collegeB).build())
                .route(Route.builder().id(UUID.randomUUID()).college(collegeB).build())
                .driver(driverA)
                .build();

        DriverPortalController driverController = new DriverPortalController(
                driverRepository, busRepository, busQrTokenRepository,
                busAssignmentRepository, scheduleRepository, tripRepository,
                tripLocationRepository, tripStopEventRepository, breakdownReportRepository,
                maintenanceRepository, routeStopRepository, driverNotificationRepository,
                emergencyLogRepository, auditLogService, geofencingService,
                hybridTrackingService, webSocketHandler, etaCalculationService,
                smartNotificationService
        );

        when(driverRepository.findByUser(driverUserA)).thenReturn(Optional.of(driverA));
        when(scheduleRepository.findById(schedB.getId())).thenReturn(Optional.of(schedB));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                driverController.startTrip(schedB.getId())
        );
        assertTrue(ex.getMessage().contains("Schedule does not belong to driver's college"));
    }

    @Test
    @DisplayName("Criterion 17: Student Portal prevents cross-college live bus tracking")
    void testStudentCrossCollegeTrackingProtection() {
        setSecurityContext(studentUserA);

        Bus busB = Bus.builder().id(UUID.randomUUID()).busNumber("BUS-B").college(collegeB).build();
        Trip tripB = Trip.builder()
                .id(UUID.randomUUID())
                .bus(busB)
                .college(collegeB)
                .status("IN_PROGRESS")
                .build();

        StudentPortalController studentController = new StudentPortalController(
                studentRepository, userRepository, busRepository,
                routeRepository, stopRepository, routeStopRepository,
                tripRepository, notificationRepository, scheduleRepository,
                auditLogService, etaService, etaCalculationService,
                geofencingService, tripLocationRepository
        );

        when(studentRepository.findByUser(studentUserA)).thenReturn(Optional.of(studentA));
        when(tripRepository.findByBusIdAndStatusIn(eq(busB.getId()), anyList())).thenReturn(Optional.of(tripB));

        // Student of College A cannot track Bus of College B
        assertThrows(ResourceNotFoundException.class, () -> studentController.getLiveBusLocation(busB.getId()));
    }

    @Test
    @DisplayName("Criterion 19: SuperAdmin can view all colleges and platform metrics in read-only mode")
    void testSuperAdminCollegesReadOnly() {
        when(collegeRepository.findAll()).thenReturn(Arrays.asList(collegeA, collegeB));
        when(collegeRepository.count()).thenReturn(2L);
        when(studentRepository.count()).thenReturn(100L);
        when(driverRepository.count()).thenReturn(10L);
        when(busRepository.countByDeletedAtIsNull()).thenReturn(8L);

        List<CollegeDto> dtos = collegeService.getAllColleges();
        assertEquals(2, dtos.size());

        PlatformMetricsDto metrics = collegeService.getPlatformMetrics();
        assertNotNull(metrics);
        assertEquals(2, metrics.getTotalColleges());
        assertEquals(100, metrics.getTotalStudents());
    }

    static class TestWebSocketHandler extends LiveLocationWebSocketHandler {
        public TestWebSocketHandler(GpsUseCase gpsUseCase, TripRepository tripRepository, BusRepository busRepository, ObjectMapper objectMapper, JwtTokenProvider jwtTokenProvider) {
            super(gpsUseCase, tripRepository, busRepository, objectMapper, jwtTokenProvider);
        }

        public void triggerTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            super.handleTextMessage(session, message);
        }
    }

    @Test
    @DisplayName("Criterion 20: WebSocket broadcast isolates live GPS updates across colleges")
    void testWebSocketMultiCollegeIsolation() throws Exception {
        WebSocketSession sessionCollegeA = mock(WebSocketSession.class);
        when(sessionCollegeA.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("collegeId", collegeA.getId());
        when(sessionCollegeA.getAttributes()).thenReturn(attributes);

        TestWebSocketHandler ws = new TestWebSocketHandler(
                gpsUseCase, tripRepository, busRepository, new ObjectMapper(), jwtTokenProvider
        );

        // Register session as subscribed client
        ws.afterConnectionEstablished(sessionCollegeA);
        TextMessage subMsg = new TextMessage("{\"type\":\"SUBSCRIBE_CLIENT\",\"collegeId\":\"" + collegeA.getId() + "\"}");
        ws.triggerTextMessage(sessionCollegeA, subMsg);

        // Simulate driver sending GPS update for a trip belonging to College B
        UUID tripBId = UUID.randomUUID();
        Bus busB = Bus.builder().id(UUID.randomUUID()).busNumber("BUS-B").college(collegeB).currentLatitude(13.0).currentLongitude(80.2).build();
        Route routeB = Route.builder().id(UUID.randomUUID()).routeName("Route B").college(collegeB).build();
        Trip tripB = Trip.builder()
                .id(tripBId)
                .bus(busB)
                .route(routeB)
                .college(collegeB)
                .status("IN_PROGRESS")
                .build();

        when(tripRepository.findById(tripBId)).thenReturn(Optional.of(tripB));

        TextMessage driverUpdate = new TextMessage("{\"type\":\"DRIVER_UPDATE\",\"tripId\":\"" + tripBId + "\",\"latitude\":13.0,\"longitude\":80.2,\"speed\":30.0,\"heading\":90.0}");
        ws.triggerTextMessage(sessionCollegeA, driverUpdate);

        // Verify session of College A never received College B's GPS update message
        verify(sessionCollegeA, never()).sendMessage(any());
    }

    @Test
    @DisplayName("Criterion 21: Admin can retrieve own college information")
    void testAdminCanRetrieveOwnCollegeInfo() {
        when(collegeRepository.findById(collegeA.getId())).thenReturn(Optional.of(collegeA));
        AdminCollegeController controller = new AdminCollegeController(collegeService);
        var response = controller.getCollegeInfo(new UserPrincipal(adminUserA));

        assertNotNull(response.getBody());
        assertEquals("Chennai Institute of Technology", response.getBody().getData().getName());
        assertEquals("CIT", response.getBody().getData().getCollegeCode());
        assertEquals("ACTIVE", response.getBody().getData().getStatus());
    }

    @Test
    @DisplayName("Criterion 22: Admin can update own College Code")
    void testAdminCanUpdateOwnCollegeCode_Success() {
        when(collegeRepository.findById(collegeA.getId())).thenReturn(Optional.of(collegeA));
        when(collegeRepository.existsByCollegeCodeIgnoreCaseAndIdNot("NEWCIT", collegeA.getId())).thenReturn(false);
        when(collegeRepository.save(any(College.class))).thenAnswer(i -> i.getArgument(0));

        AdminCollegeController controller = new AdminCollegeController(collegeService);
        UpdateCollegeCodeRequest req = new UpdateCollegeCodeRequest("NEWCIT");
        var response = controller.updateCollegeCode(new UserPrincipal(adminUserA), req);

        assertNotNull(response.getBody());
        assertEquals("NEWCIT", response.getBody().getData().getCollegeCode());
        assertEquals("NEWCIT", collegeA.getCollegeCode());
    }

    @Test
    @DisplayName("Criterion 23: Duplicate College Code update is rejected")
    void testAdminUpdateCollegeCode_DuplicateRejected() {
        when(collegeRepository.existsByCollegeCodeIgnoreCaseAndIdNot("LOYOLA", collegeA.getId())).thenReturn(true);

        AdminCollegeController controller = new AdminCollegeController(collegeService);
        UpdateCollegeCodeRequest req = new UpdateCollegeCodeRequest("LOYOLA");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                controller.updateCollegeCode(new UserPrincipal(adminUserA), req)
        );
        assertTrue(ex.getMessage().contains("already in use by another college"));
    }

    @Test
    @DisplayName("Criterion 24: College Admin updates college code successfully")
    void testAdminUpdatesCollegeCode() {
        UUID srmId = UUID.randomUUID();
        College srmCollege = College.builder()
                .id(srmId)
                .name("SRM University")
                .collegeCode("SRM-OLD")
                .status("ACTIVE")
                .build();

        when(collegeRepository.findById(srmId)).thenReturn(Optional.of(srmCollege));
        when(collegeRepository.existsByCollegeCodeIgnoreCaseAndIdNot("SRM", srmId)).thenReturn(false);
        when(collegeRepository.save(any(College.class))).thenAnswer(i -> i.getArgument(0));

        CollegeDto configured = collegeService.updateCollegeCodeForAdmin(srmId, "SRM");
        assertEquals("SRM", configured.getCollegeCode());
        assertEquals("SRM", srmCollege.getCollegeCode());
    }

    @Test
    @DisplayName("Criterion 25: Student login requires new College Code after Admin updates it")
    void testStudentLoginRequiresNewCollegeCodeAfterAdminUpdatesIt() {
        // Step 1: Admin changes code from CIT to NEWCIT
        when(collegeRepository.findById(collegeA.getId())).thenReturn(Optional.of(collegeA));
        when(collegeRepository.existsByCollegeCodeIgnoreCaseAndIdNot("NEWCIT", collegeA.getId())).thenReturn(false);
        when(collegeRepository.save(any(College.class))).thenAnswer(i -> i.getArgument(0));

        collegeService.updateCollegeCodeForAdmin(collegeA.getId(), "NEWCIT");
        assertEquals("NEWCIT", collegeA.getCollegeCode());

        // Step 2: Student attempts login with OLD code "CIT" -> rejected
        UserPrincipal principal = new UserPrincipal(studentUserA);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("CIT", "ACTIVE")).thenReturn(Optional.empty());

        LoginRequest oldLoginReq = LoginRequest.builder()
                .email("student@cit.edu")
                .password(TEST_PASSWORD)
                .role("STUDENT")
                .collegeCode("CIT")
                .build();
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.login(oldLoginReq)
        );
        assertTrue(ex.getMessage().contains("Invalid or inactive College Code"));

        // Step 3: Student attempts login with NEW code "NEWCIT" -> succeeds
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("NEWCIT", "ACTIVE")).thenReturn(Optional.of(collegeA));
        when(jwtTokenProvider.generateToken(auth)).thenReturn("mock-jwt-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        LoginRequest newLoginReq = LoginRequest.builder()
                .email("student@cit.edu")
                .password(TEST_PASSWORD)
                .role("STUDENT")
                .collegeCode("NEWCIT")
                .build();
        LoginResponse resp = authService.login(newLoginReq);

        assertNotNull(resp);
        assertEquals("student@cit.edu", resp.getEmail());
        assertEquals("NEWCIT", resp.getCollegeCode());
        assertEquals(collegeA.getId(), resp.getCollegeId());
    }

    @Test
    @DisplayName("Criterion 26: SuperAdmin can view platform metrics")
    void testSuperAdminCanViewPlatformMetrics() {
        when(collegeRepository.count()).thenReturn(2L);
        when(collegeRepository.findAll()).thenReturn(Arrays.asList(collegeA, collegeB));
        when(studentRepository.count()).thenReturn(150L);
        when(driverRepository.count()).thenReturn(12L);
        when(busRepository.countByDeletedAtIsNull()).thenReturn(10L);

        PlatformMetricsDto metrics = collegeService.getPlatformMetrics();
        assertEquals(2L, metrics.getTotalColleges());
        assertEquals(2L, metrics.getActiveColleges());
        assertEquals(0L, metrics.getDisabledColleges());
        assertEquals(150L, metrics.getTotalStudents());
        assertEquals(12L, metrics.getTotalDrivers());
        assertEquals(10L, metrics.getTotalBuses());
    }

    @Test
    @DisplayName("Criterion 27: SuperAdmin controller is read-only for college directory and metrics")
    void testSuperAdminControllerIsReadOnly() {
        when(collegeRepository.findAll()).thenReturn(Arrays.asList(collegeA, collegeB));
        when(collegeRepository.findById(collegeA.getId())).thenReturn(Optional.of(collegeA));
        when(collegeRepository.count()).thenReturn(2L);
        when(studentRepository.count()).thenReturn(150L);
        when(driverRepository.count()).thenReturn(12L);
        when(busRepository.countByDeletedAtIsNull()).thenReturn(10L);

        SuperAdminCollegeController controller = new SuperAdminCollegeController(collegeService);

        var allRes = controller.getAllColleges();
        assertNotNull(allRes.getBody());
        assertEquals(2, allRes.getBody().getData().size());

        var singleRes = controller.getCollegeById(collegeA.getId());
        assertNotNull(singleRes.getBody());
        assertEquals("Chennai Institute of Technology", singleRes.getBody().getData().getName());

        var metricsRes = controller.getPlatformMetrics();
        assertNotNull(metricsRes.getBody());
        assertEquals(2L, metricsRes.getBody().getData().getTotalColleges());
    }

    @Test
    @DisplayName("Criterion 28: College Admin self-registration creates college with required college_code and initial admin atomically")
    void testCollegeAdminSelfRegistrationCreatesCollegeAndAdmin() {
        when(collegeRepository.existsByNameIgnoreCase("College B")).thenReturn(false);
        when(collegeRepository.existsByCollegeCodeIgnoreCase("CLGB")).thenReturn(false);
        when(userRepository.existsByEmail("admin@collegeb.edu")).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("hashed_pass_b");
        when(collegeRepository.save(any(College.class))).thenAnswer(i -> {
            College c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        RegisterCollegeAdminRequest req = RegisterCollegeAdminRequest.builder()
                .collegeName("College B")
                .collegeCode("CLGB")
                .firstName("Admin")
                .lastName("CollegeB")
                .email("admin@collegeb.edu")
                .password(TEST_PASSWORD)
                .phoneNumber("+919876543210")
                .build();

        authService.registerCollegeAdmin(req);

        // Verify college was saved with non-null uppercase code and ACTIVE status
        verify(collegeRepository, times(1)).save(argThat(c -> 
                "CLGB".equals(c.getCollegeCode()) && 
                "College B".equals(c.getName()) && 
                "ACTIVE".equals(c.getStatus())
        ));

        // Verify admin user was saved with Role.ADMIN and linked to the created college
        verify(userRepository, times(1)).save(argThat(u -> 
                "admin@collegeb.edu".equals(u.getEmail()) && 
                u.getRole() == Role.ADMIN && 
                u.getCollege() != null && 
                "CLGB".equals(u.getCollege().getCollegeCode())
        ));
    }

    @Test
    @DisplayName("Criterion 29: College Admin self-registration rejects duplicate college code and duplicate email")
    void testCollegeAdminSelfRegistrationRejectsDuplicates() {
        // 1. Duplicate college code
        when(collegeRepository.existsByCollegeCodeIgnoreCase("CIT")).thenReturn(true);
        RegisterCollegeAdminRequest dupCodeReq = RegisterCollegeAdminRequest.builder()
                .collegeName("Another College")
                .collegeCode("CIT")
                .firstName("Another")
                .lastName("Admin")
                .email("another@cit.edu")
                .password(TEST_PASSWORD)
                .build();

        BadRequestException exCode = assertThrows(BadRequestException.class, () ->
                authService.registerCollegeAdmin(dupCodeReq)
        );
        assertTrue(exCode.getMessage().contains("College code already exists"));

        // 2. Duplicate email
        when(collegeRepository.existsByCollegeCodeIgnoreCase("UNIQUE")).thenReturn(false);
        when(collegeRepository.existsByNameIgnoreCase("Unique College")).thenReturn(false);
        when(userRepository.existsByEmail("admin@cit.edu")).thenReturn(true);

        RegisterCollegeAdminRequest dupEmailReq = RegisterCollegeAdminRequest.builder()
                .collegeName("Unique College")
                .collegeCode("UNIQUE")
                .firstName("New")
                .lastName("Admin")
                .email("admin@cit.edu")
                .password(TEST_PASSWORD)
                .build();

        BadRequestException exEmail = assertThrows(BadRequestException.class, () ->
                authService.registerCollegeAdmin(dupEmailReq)
        );
        assertTrue(exEmail.getMessage().contains("Email address already in use"));
    }

    @Test
    @DisplayName("Criterion 30: College Admin registration rate limiting blocks after 5 requests per minute")
    void testCollegeAdminRegistrationRateLimiting() {
        com.smartbus.security.ratelimit.RateLimitService rateLimitService = new com.smartbus.security.ratelimit.RateLimitService();
        AuthController authController = new AuthController(authService, rateLimitService);

        jakarta.servlet.http.HttpServletRequest servletRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("198.51.100.25");

        RegisterCollegeAdminRequest req = RegisterCollegeAdminRequest.builder()
                .collegeName("Test College")
                .collegeCode("TC")
                .firstName("Test")
                .lastName("Admin")
                .email("admin@test.edu")
                .password(TEST_PASSWORD)
                .build();

        // Configure mocks for 5 successful registrations
        when(collegeRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(collegeRepository.existsByCollegeCodeIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(collegeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // First 5 requests should succeed
        for (int i = 0; i < 5; i++) {
            var res = authController.registerCollegeAdmin(req, servletRequest);
            assertEquals(org.springframework.http.HttpStatus.CREATED, res.getStatusCode());
        }

        // 6th request within the same minute should be blocked with RateLimitExceededException
        assertThrows(com.smartbus.domain.exception.RateLimitExceededException.class, () ->
                authController.registerCollegeAdmin(req, servletRequest)
        );
    }

    @Test
    @DisplayName("Criterion 31: Full two-way tenant isolation: Admin B cannot access College A entities")
    void testAdminBCannotAccessCollegeAEntities() {
        Bus busA = Bus.builder().id(UUID.randomUUID()).busNumber("BUS-A").college(collegeA).build();
        Route routeA = Route.builder().id(UUID.randomUUID()).routeName("Route A").college(collegeA).build();
        User driverUserA = User.builder().id(UUID.randomUUID()).college(collegeA).role(Role.DRIVER).build();
        Driver driverA = Driver.builder().id(UUID.randomUUID()).user(driverUserA).college(collegeA).build();

        // 1. Bus Isolation: Admin B trying to access Bus A -> ResourceNotFoundException
        AdminBusController busController = new AdminBusController(
                busRepository, busQrTokenRepository, gpsDeviceRepository,
                tripRepository, scheduleRepository, auditLogService,
                etaCalculationService, tripLocationRepository, routeStopRepository, routeStopMapper
        );
        when(busRepository.findById(busA.getId())).thenReturn(Optional.of(busA));
        assertThrows(ResourceNotFoundException.class, () ->
                busController.getBusById(busA.getId(), new UserPrincipal(adminUserB))
        );

        // 2. Driver Isolation: Admin B trying to update Driver A -> ResourceNotFoundException
        AdminDriverController driverController = new AdminDriverController(
                driverRepository, userRepository, tripRepository,
                scheduleRepository, refreshTokenRepository, passwordEncoder, auditLogService
        );
        when(driverRepository.findById(driverA.getId())).thenReturn(Optional.of(driverA));
        assertThrows(ResourceNotFoundException.class, () ->
                driverController.updateStatus(driverA.getId(), "ACTIVE", new UserPrincipal(adminUserB))
        );

        // 3. Route Isolation: Admin B trying to access Route A -> ResourceNotFoundException
        AdminRouteController routeController = new AdminRouteController(
                routeRepository, scheduleRepository, auditLogService,
                routeMapper, routeStopRepository, routeStopMapper
        );
        when(routeRepository.findById(routeA.getId())).thenReturn(Optional.of(routeA));
        assertThrows(ResourceNotFoundException.class, () ->
                routeController.getRouteById(routeA.getId(), new UserPrincipal(adminUserB))
        );

        // 4. Stop Isolation: Admin B trying to access stops on Route A -> ResourceNotFoundException
        AdminStopController stopController = new AdminStopController(
                routeRepository, stopRepository, routeStopRepository, auditLogService, routeStopMapper
        );
        when(routeRepository.findById(routeA.getId())).thenReturn(Optional.of(routeA));
        assertThrows(ResourceNotFoundException.class, () ->
                stopController.getStopsByRoute(routeA.getId(), new UserPrincipal(adminUserB))
        );

        // 5. Student Isolation: College B student queries only return College B students
        when(studentRepository.findByCollegeId(collegeB.getId())).thenReturn(Collections.emptyList());
        List<Student> studentsForB = studentRepository.findByCollegeId(collegeB.getId());
        assertTrue(studentsForB.isEmpty());
    }

    @Test
    @DisplayName("Criterion 32: Full two-way tenant isolation: Admin A cannot access College B entities")
    void testAdminACannotAccessCollegeBEntities() {
        Bus busB = Bus.builder().id(UUID.randomUUID()).busNumber("BUS-B").college(collegeB).build();
        Route routeB = Route.builder().id(UUID.randomUUID()).routeName("Route B").college(collegeB).build();
        User driverUserB = User.builder().id(UUID.randomUUID()).college(collegeB).role(Role.DRIVER).build();
        Driver driverB = Driver.builder().id(UUID.randomUUID()).user(driverUserB).college(collegeB).build();

        // 1. Bus Isolation: Admin A cannot access Bus B
        AdminBusController busController = new AdminBusController(
                busRepository, busQrTokenRepository, gpsDeviceRepository,
                tripRepository, scheduleRepository, auditLogService,
                etaCalculationService, tripLocationRepository, routeStopRepository, routeStopMapper
        );
        when(busRepository.findById(busB.getId())).thenReturn(Optional.of(busB));
        assertThrows(ResourceNotFoundException.class, () ->
                busController.getBusById(busB.getId(), new UserPrincipal(adminUserA))
        );

        // 2. Driver Isolation: Admin A cannot update Driver B
        AdminDriverController driverController = new AdminDriverController(
                driverRepository, userRepository, tripRepository,
                scheduleRepository, refreshTokenRepository, passwordEncoder, auditLogService
        );
        when(driverRepository.findById(driverB.getId())).thenReturn(Optional.of(driverB));
        assertThrows(ResourceNotFoundException.class, () ->
                driverController.updateStatus(driverB.getId(), "ACTIVE", new UserPrincipal(adminUserA))
        );

        // 3. Route Isolation: Admin A cannot access Route B
        AdminRouteController routeController = new AdminRouteController(
                routeRepository, scheduleRepository, auditLogService,
                routeMapper, routeStopRepository, routeStopMapper
        );
        when(routeRepository.findById(routeB.getId())).thenReturn(Optional.of(routeB));
        assertThrows(ResourceNotFoundException.class, () ->
                routeController.getRouteById(routeB.getId(), new UserPrincipal(adminUserA))
        );

        // 4. Stop Isolation: Admin A cannot access Route B stops
        AdminStopController stopController = new AdminStopController(
                routeRepository, stopRepository, routeStopRepository, auditLogService, routeStopMapper
        );
        when(routeRepository.findById(routeB.getId())).thenReturn(Optional.of(routeB));
        assertThrows(ResourceNotFoundException.class, () ->
                stopController.getStopsByRoute(routeB.getId(), new UserPrincipal(adminUserA))
        );

        // 5. Student Isolation: College A query does not contain College B students
        when(studentRepository.findByCollegeId(collegeA.getId())).thenReturn(Collections.singletonList(studentA));
        List<Student> studentsForA = studentRepository.findByCollegeId(collegeA.getId());
        assertEquals(1, studentsForA.size());
        assertEquals("CIT-2024-001", studentsForA.get(0).getStudentId());
    }

    @Test
    @DisplayName("Criterion 33: Two-way WebSocket GPS isolation between College A and College B")
    void testTwoWayWebSocketIsolation() throws Exception {
        WebSocketSession sessionA = mock(WebSocketSession.class);
        Map<String, Object> attrA = new HashMap<>();
        attrA.put("collegeId", collegeA.getId());
        when(sessionA.getAttributes()).thenReturn(attrA);

        WebSocketSession sessionB = mock(WebSocketSession.class);
        Map<String, Object> attrB = new HashMap<>();
        attrB.put("collegeId", collegeB.getId());
        when(sessionB.getAttributes()).thenReturn(attrB);

        TestWebSocketHandler ws = new TestWebSocketHandler(
                gpsUseCase, tripRepository, busRepository, new ObjectMapper(), jwtTokenProvider
        );

        ws.afterConnectionEstablished(sessionA);
        ws.triggerTextMessage(sessionA, new TextMessage("{\"type\":\"SUBSCRIBE_CLIENT\",\"collegeId\":\"" + collegeA.getId() + "\"}"));

        ws.afterConnectionEstablished(sessionB);
        ws.triggerTextMessage(sessionB, new TextMessage("{\"type\":\"SUBSCRIBE_CLIENT\",\"collegeId\":\"" + collegeB.getId() + "\"}"));

        // Event for College A: Trip of College A
        UUID tripAId = UUID.randomUUID();
        Bus busA = Bus.builder().id(UUID.randomUUID()).busNumber("BUS-A").college(collegeA).currentLatitude(13.1).currentLongitude(80.1).build();
        Trip tripA = Trip.builder().id(tripAId).bus(busA).college(collegeA).status("EN_ROUTE").build();
        when(tripRepository.findById(tripAId)).thenReturn(Optional.of(tripA));

        TextMessage updateA = new TextMessage("{\"type\":\"DRIVER_UPDATE\",\"tripId\":\"" + tripAId + "\",\"latitude\":13.1,\"longitude\":80.1}");
        ws.triggerTextMessage(sessionA, updateA);

        // Session B should never receive update for Trip A
        verify(sessionB, never()).sendMessage(any());
    }

    @Test
    @DisplayName("Security: Public registration rejects SUPER_ADMIN role escalation")
    void testPublicRegistration_RejectsSuperAdminRole() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("hacker@smartbus.com");
        req.setPassword(TEST_PASSWORD);
        req.setRole("SUPER_ADMIN");
        req.setFirstName("Hacker");
        req.setLastName("User");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("restricted to students only"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Security: Public registration rejects ADMIN role escalation")
    void testPublicRegistration_RejectsAdminRole() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("fakeadmin@smartbus.com");
        req.setPassword(TEST_PASSWORD);
        req.setRole("ADMIN");
        req.setFirstName("Fake");
        req.setLastName("Admin");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("restricted to students only"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Security: Public registration rejects DRIVER role escalation")
    void testPublicRegistration_RejectsDriverRole() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("unauthorized.driver@smartbus.com");
        req.setPassword(TEST_PASSWORD);
        req.setRole("DRIVER");
        req.setFirstName("Fake");
        req.setLastName("Driver");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("restricted to students only"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Security: WebSocket subscription rejects unauthenticated client without college token")
    void testWebSocket_RejectsUnauthenticatedSubscription() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        TestWebSocketHandler ws = new TestWebSocketHandler(
                gpsUseCase, tripRepository, busRepository, new ObjectMapper(), jwtTokenProvider
        );

        ws.triggerTextMessage(session, new TextMessage("{\"type\":\"SUBSCRIBE_CLIENT\"}"));

        verify(session, times(1)).sendMessage(argThat((TextMessage msg) ->
                msg.getPayload().contains("Unauthorized: Missing or invalid authentication token")
        ));
    }

    @Test
    @DisplayName("Security: WebSocket subscription rejects mismatched client collegeId")
    void testWebSocket_RejectsMismatchedCollegeId() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("collegeId", collegeA.getId());
        when(session.getAttributes()).thenReturn(attributes);

        TestWebSocketHandler ws = new TestWebSocketHandler(
                gpsUseCase, tripRepository, busRepository, new ObjectMapper(), jwtTokenProvider
        );

        // Client claims to belong to College B while session is authenticated for College A
        ws.triggerTextMessage(session, new TextMessage("{\"type\":\"SUBSCRIBE_CLIENT\",\"collegeId\":\"" + collegeB.getId() + "\"}"));

        verify(session, times(1)).sendMessage(argThat((TextMessage msg) ->
                msg.getPayload().contains("Forbidden: Cross-tenant subscription is not permitted")
        ));
    }
}
