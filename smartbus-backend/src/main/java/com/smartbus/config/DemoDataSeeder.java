package com.smartbus.config;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;
    private final ScheduleRepository scheduleRepository;
    private final TripRepository tripRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final NotificationRepository notificationRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmergencyLogRepository emergencyLogRepository;
    private final GpsDeviceRepository gpsDeviceRepository;
    private final BusQrTokenRepository busQrTokenRepository;
    private final BusAssignmentRepository busAssignmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Checking if demo data needs to be seeded...");

        if (!seedDemoData) {
            log.info("Demo data seeding disabled (app.seed-demo-data=false).");
            // Ensure essential administrative account exists so real users can be created
            boolean hasAdmin = userRepository.findAll().stream().anyMatch(u -> u.getRole() == Role.ADMIN && u.getDeletedAt() == null);
            if (!hasAdmin) {
                log.info("No active admin account detected. Provisioning essential system administrator...");
                User adminUser = User.builder()
                        .email("admin@smartbus.com")
                        .passwordHash(passwordEncoder.encode("Admin@123"))
                        .firstName("System")
                        .lastName("Admin")
                        .phoneNumber("+919876543210")
                        .role(Role.ADMIN)
                        .isActive(true)
                        .build();
                userRepository.save(adminUser);
            }
            return;
        }

        if (userRepository.existsByEmail("student@smartbus.ai")) {
            log.info("Demo data already seeded. Skipping...");
            return;
        }

        log.info("Starting demo database seeding for development...");

        // 1. Seed Transport Admin
        User adminUser = User.builder()
                .email("admin@smartbus.ai")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .firstName("Demo")
                .lastName("Admin")
                .phoneNumber("9876598765")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
        userRepository.save(adminUser);

        // 2. Seed Student
        User studentUser = User.builder()
                .email("student@smartbus.ai")
                .passwordHash(passwordEncoder.encode("Student@123"))
                .firstName("Demo")
                .lastName("Student")
                .phoneNumber("9876501234")
                .role(Role.STUDENT)
                .isActive(true)
                .build();
        userRepository.save(studentUser);

        Student studentProfile = Student.builder()
                .user(studentUser)
                .studentId("STU-DEMO-001")
                .department("Computer Science")
                .batch("2026")
                .notificationPreferences("APPROACHING,ARRIVED,DEPARTED,DELAYED")
                .build();
        studentRepository.save(studentProfile);

        // 3. Seed College Staff
        User staffUser = User.builder()
                .email("staff@smartbus.ai")
                .passwordHash(passwordEncoder.encode("Staff@123"))
                .firstName("Demo")
                .lastName("Staff")
                .phoneNumber("9876512345")
                .role(Role.STUDENT)
                .isActive(true)
                .build();
        userRepository.save(staffUser);

        Student staffProfile = Student.builder()
                .user(staffUser)
                .studentId("STAFF-DEMO-001")
                .department("Administration")
                .batch("N/A")
                .build();
        studentRepository.save(staffProfile);

        // 4. Seed Drivers
        User driverUser1 = User.builder()
                .email("driver@smartbus.ai")
                .passwordHash(passwordEncoder.encode("Driver@123"))
                .firstName("Demo")
                .lastName("Driver")
                .phoneNumber("9876543210")
                .role(Role.DRIVER)
                .isActive(true)
                .build();
        userRepository.save(driverUser1);

        Driver driverProfile1 = Driver.builder()
                .user(driverUser1)
                .licenseNumber("TN0120231234567")
                .employeeId("DRV-DEMO-001")
                .licenseExpiry(LocalDate.now().plusYears(5))
                .emergencyContact("9876598765")
                .approvalStatus("APPROVED")
                .isApproved(true)
                .averageRating(new BigDecimal("4.80"))
                .status("AVAILABLE")
                .build();
        driverRepository.save(driverProfile1);

        User driverUser2 = User.builder()
                .email("driver1@smartbus.ai")
                .passwordHash(passwordEncoder.encode("Driver@123"))
                .firstName("Driver")
                .lastName("One")
                .phoneNumber("9876543211")
                .role(Role.DRIVER)
                .isActive(true)
                .build();
        userRepository.save(driverUser2);

        Driver driverProfile2 = Driver.builder()
                .user(driverUser2)
                .licenseNumber("TN0120231234568")
                .employeeId("DRV-DEMO-002")
                .licenseExpiry(LocalDate.now().plusYears(3))
                .emergencyContact("9876598765")
                .approvalStatus("APPROVED")
                .isApproved(true)
                .averageRating(new BigDecimal("4.50"))
                .status("AVAILABLE")
                .build();
        driverRepository.save(driverProfile2);

        User driverUser3 = User.builder()
                .email("driver2@smartbus.ai")
                .passwordHash(passwordEncoder.encode("Driver@123"))
                .firstName("Driver")
                .lastName("Two")
                .phoneNumber("9876543212")
                .role(Role.DRIVER)
                .isActive(true)
                .build();
        userRepository.save(driverUser3);

        Driver driverProfile3 = Driver.builder()
                .user(driverUser3)
                .licenseNumber("TN0120231234569")
                .employeeId("DRV-DEMO-003")
                .licenseExpiry(LocalDate.now().plusYears(4))
                .emergencyContact("9876598765")
                .approvalStatus("PENDING")
                .isApproved(false)
                .averageRating(new BigDecimal("5.00"))
                .status("AVAILABLE")
                .build();
        driverRepository.save(driverProfile3);

        // 5. Seed GPS Devices
        GpsDevice dev1 = GpsDevice.builder().deviceId("GPS-DEV-001").imei("359283120194811").simIdentifier("89014103211185").provider("Vodafone").status("ONLINE").build();
        GpsDevice dev2 = GpsDevice.builder().deviceId("GPS-DEV-002").imei("359283120194812").simIdentifier("89014103211186").provider("Airtel").status("OFFLINE").build();
        GpsDevice dev3 = GpsDevice.builder().deviceId("GPS-DEV-003").imei("359283120194813").simIdentifier("89014103211187").provider("Jio").status("NOT_CONFIGURED").build();
        gpsDeviceRepository.save(dev1);
        gpsDeviceRepository.save(dev2);
        gpsDeviceRepository.save(dev3);

        // 6. Seed Buses
        Bus bus1 = Bus.builder().busNumber("BUS-101").registrationNumber("TN-01-AB-1234").manufacturer("Volvo").manufacturingYear(2024).busType("AC").busCode("SB-BUS-7X4K92").gpsDevice(dev1).model("Volvo Hybrid 2024").capacity(40).status("ACTIVE").currentLatitude(12.971598).currentLongitude(77.594562).lastUpdated(LocalDateTime.now()).build();
        Bus bus2 = Bus.builder().busNumber("BUS-102").registrationNumber("TN-01-CD-5678").manufacturer("Tata").manufacturingYear(2023).busType("NON_AC").busCode("SB-BUS-9Y2L51").gpsDevice(dev2).model("Tata Starbus 2023").capacity(50).status("ACTIVE").currentLatitude(12.960000).currentLongitude(77.580000).lastUpdated(LocalDateTime.now()).build();
        Bus bus3 = Bus.builder().busNumber("BUS-103").registrationNumber("TN-01-EF-9012").manufacturer("Leyland").manufacturingYear(2022).busType("AC").busCode("SB-BUS-3W1M82").gpsDevice(dev3).model("Leyland Viking").capacity(40).status("MAINTENANCE").currentLatitude(12.950000).currentLongitude(77.570000).lastUpdated(LocalDateTime.now()).build();
        busRepository.save(bus1);
        busRepository.save(bus2);
        busRepository.save(bus3);

        // 7. Seed QR Tokens
        BusQrToken qr1 = BusQrToken.builder().bus(bus1).token("8f3d8f3d8f3d8f3d8f3d8f3d8f3d8f3d").isActive(true).build();
        BusQrToken qr2 = BusQrToken.builder().bus(bus2).token("9a4e9a4e9a4e9a4e9a4e9a4e9a4e9a4e").isActive(true).build();
        busQrTokenRepository.save(qr1);
        busQrTokenRepository.save(qr2);

        // 8. Seed Route
        Route route = Route.builder()
                .routeName("CIT Campus - Central Bus Stand")
                .startPoint("CIT Campus Gate")
                .endPoint("Central Bus Stand")
                .distance(8.5)
                .estimatedDurationMins(30)
                .status("ACTIVE")
                .build();
        routeRepository.save(route);

        // 9. Seed Stops
        Stop stop1 = Stop.builder().stopName("CIT Campus Gate").latitude(12.971598).longitude(77.594562).build();
        Stop stop2 = Stop.builder().stopName("Industrial Park").latitude(12.960000).longitude(77.580000).build();
        Stop stop3 = Stop.builder().stopName("Velachery").latitude(12.955000).longitude(77.575000).build();
        Stop stop4 = Stop.builder().stopName("Central Bus Stand").latitude(12.950000).longitude(77.570000).build();
        stopRepository.save(stop1);
        stopRepository.save(stop2);
        stopRepository.save(stop3);
        stopRepository.save(stop4);

        // 10. Seed Route Stops
        RouteStop rs1 = RouteStop.builder().route(route).sequenceNumber(1).stop(stop1).distanceFromStart(0.0).durationFromStartMins(0).expectedArrivalTime(LocalTime.of(8, 30)).expectedDepartureTime(LocalTime.of(8, 32)).build();
        RouteStop rs2 = RouteStop.builder().route(route).sequenceNumber(2).stop(stop2).distanceFromStart(4.0).durationFromStartMins(15).expectedArrivalTime(LocalTime.of(8, 45)).expectedDepartureTime(LocalTime.of(8, 47)).build();
        RouteStop rs3 = RouteStop.builder().route(route).sequenceNumber(3).stop(stop3).distanceFromStart(6.5).durationFromStartMins(22).expectedArrivalTime(LocalTime.of(8, 52)).expectedDepartureTime(LocalTime.of(8, 54)).build();
        RouteStop rs4 = RouteStop.builder().route(route).sequenceNumber(4).stop(stop4).distanceFromStart(8.5).durationFromStartMins(30).expectedArrivalTime(LocalTime.of(9, 0)).expectedDepartureTime(LocalTime.of(9, 2)).build();
        routeStopRepository.save(rs1);
        routeStopRepository.save(rs2);
        routeStopRepository.save(rs3);
        routeStopRepository.save(rs4);

        // 11. Seed Schedules
        Schedule schedule1 = Schedule.builder()
                .route(route)
                .bus(bus1)
                .driver(driverProfile1)
                .departureTime(LocalTime.of(8, 30))
                .arrivalTime(LocalTime.of(9, 0))
                .daysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .status("ACTIVE")
                .build();
        scheduleRepository.save(schedule1);

        Schedule schedule2 = Schedule.builder()
                .route(route)
                .bus(bus2)
                .driver(driverProfile2)
                .departureTime(LocalTime.of(13, 0))
                .arrivalTime(LocalTime.of(13, 30))
                .daysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .status("ACTIVE")
                .build();
        scheduleRepository.save(schedule2);

        // 12. Seed Bus Assignments
        BusAssignment assign1 = BusAssignment.builder().bus(bus1).driver(driverProfile1).route(route).schedule(schedule1).status("ACTIVE").build();
        BusAssignment assign2 = BusAssignment.builder().bus(bus2).driver(driverProfile2).route(route).schedule(schedule2).status("ACTIVE").build();
        busAssignmentRepository.save(assign1);
        busAssignmentRepository.save(assign2);

        // 13. Seed active live Trip & historical Trip
        Trip activeTrip = Trip.builder()
                .schedule(schedule1)
                .bus(bus1)
                .driver(driverProfile1)
                .route(route)
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now().minusMinutes(10))
                .actualDeparture(LocalDateTime.now().minusMinutes(10))
                .primaryTrackingSource("GPS_DEVICE")
                .build();
        tripRepository.save(activeTrip);

        Trip historicalTrip = Trip.builder()
                .schedule(schedule1)
                .bus(bus1)
                .driver(driverProfile1)
                .route(route)
                .status("COMPLETED")
                .startTime(LocalDateTime.now().minusHours(3))
                .endTime(LocalDateTime.now().minusHours(2).minusMinutes(30))
                .actualDeparture(LocalDateTime.now().minusHours(3))
                .actualArrival(LocalDateTime.now().minusHours(2).minusMinutes(30))
                .build();
        tripRepository.save(historicalTrip);

        GpsLocation loc1 = GpsLocation.builder().trip(historicalTrip).latitude(12.971598).longitude(77.594562).speed(0.0).heading(0.0).build();
        GpsLocation loc2 = GpsLocation.builder().trip(historicalTrip).latitude(12.960000).longitude(77.580000).speed(40.0).heading(180.0).build();
        GpsLocation loc3 = GpsLocation.builder().trip(historicalTrip).latitude(12.950000).longitude(77.570000).speed(0.0).heading(180.0).build();
        gpsLocationRepository.save(loc1);
        gpsLocationRepository.save(loc2);
        gpsLocationRepository.save(loc3);

        // 14. Seed Audit Logs
        AuditLog audit1 = AuditLog.builder().user(adminUser).action("BUS_CREATED").details("Created bus BUS-101 (Code: SB-BUS-7X4K92)").ipAddress("127.0.0.1").entityName("Bus").entityId(bus1.getId().toString()).newValue("BUS-101").build();
        AuditLog audit2 = AuditLog.builder().user(adminUser).action("ASSIGNMENT_CREATED").details("Assigned Bus BUS-101 and Driver driver@smartbus.ai to Schedule").ipAddress("127.0.0.1").entityName("BusAssignment").entityId(assign1.getId().toString()).newValue("BUS-101").build();
        auditLogRepository.save(audit1);
        auditLogRepository.save(audit2);

        // 15. Seed Notifications
        Notification notifBroadcast = Notification.builder()
                .user(null)
                .title("Welcome to SmartBus AI!")
                .message("The transit tracking platform is fully configured. Use the quick demo buttons to browse Student, Driver, and Admin Dashboards.")
                .type("INFO")
                .build();
        notificationRepository.save(notifBroadcast);

        Notification notifStudent = Notification.builder()
                .user(studentUser)
                .title("Route Assignment Complete")
                .message("Your profile is assigned to track the 'CIT Campus - Central Bus Stand' route.")
                .type("INFO")
                .build();
        notificationRepository.save(notifStudent);

        Notification notifStaff = Notification.builder()
                .user(staffUser)
                .title("Staff Transit Ready")
                .message("Quickly monitor passenger capacities and real-time ETAs from your console.")
                .type("INFO")
                .build();
        notificationRepository.save(notifStaff);

        // 16. Seed Attendance records
        Attendance attendanceStudent = Attendance.builder()
                .student(studentProfile)
                .trip(historicalTrip)
                .boardedAt(LocalDateTime.now().minusHours(3))
                .boardingStop(stop1)
                .status("BOARDED")
                .build();
        attendanceRepository.save(attendanceStudent);

        Attendance attendanceStaff = Attendance.builder()
                .student(staffProfile)
                .trip(historicalTrip)
                .boardedAt(LocalDateTime.now().minusHours(3))
                .boardingStop(stop1)
                .status("BOARDED")
                .build();
        attendanceRepository.save(attendanceStaff);

        // 17. Seed Emergency log
        EmergencyLog emergency = EmergencyLog.builder()
                .trip(historicalTrip)
                .user(driverUser1)
                .type("BREAKDOWN")
                .description("Flat tire resolved within 10 minutes near Industrial Park stop.")
                .latitude(12.960000)
                .longitude(77.580000)
                .resolved(true)
                .status("RESOLVED")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1).plusMinutes(15))
                .build();
        emergencyLogRepository.save(emergency);

        // Link student's preferred stop, favorites and coordinates
        studentProfile.setPreferredStop(stop3); // Velachery
        studentProfile.getFavoriteBuses().add(bus1); // BUS-101
        studentProfile.setHomeLatitude(12.956000);
        studentProfile.setHomeLongitude(77.576000);
        studentProfile.setHomeAddress("123 Velachery High Road, Chennai");
        studentProfile.setRegisterNumber("STU-DEMO-001");
        studentRepository.save(studentProfile);

        log.info("Demo database seeding successfully completed.");
    }
}
