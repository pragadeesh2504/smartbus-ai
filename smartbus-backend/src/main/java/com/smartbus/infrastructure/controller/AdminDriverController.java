package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuditLogService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Driver;
import com.smartbus.domain.model.Role;
import com.smartbus.domain.model.User;
import com.smartbus.infrastructure.adapter.jpa.DriverRepository;
import com.smartbus.infrastructure.adapter.jpa.UserRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.DriverDto;
import com.smartbus.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/drivers")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminDriverController {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final com.smartbus.infrastructure.adapter.jpa.TripRepository tripRepository;
    private final com.smartbus.infrastructure.adapter.jpa.ScheduleRepository scheduleRepository;
    private final com.smartbus.infrastructure.adapter.jpa.RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DriverDto>>> getAllDrivers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "licenseNumber") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String approvalStatus,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        List<Driver> allDrivers = driverRepository.findAll().stream()
                .filter(d -> d.getUser() != null && d.getUser().getDeletedAt() == null)
                .collect(Collectors.toList());

        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            UUID collegeId = userPrincipal.getCollegeId();
            allDrivers = allDrivers.stream()
                    .filter(d -> d.getCollege() != null && collegeId.equals(d.getCollege().getId()))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.trim().isEmpty()) {
            String lowerSearch = search.toLowerCase();
            allDrivers = allDrivers.stream()
                    .filter(d -> d.getUser().getEmail().toLowerCase().contains(lowerSearch) ||
                            d.getUser().getFirstName().toLowerCase().contains(lowerSearch) ||
                            d.getUser().getLastName().toLowerCase().contains(lowerSearch) ||
                            (d.getEmployeeId() != null && d.getEmployeeId().toLowerCase().contains(lowerSearch)) ||
                            d.getLicenseNumber().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        }

        if (approvalStatus != null && !approvalStatus.trim().isEmpty()) {
            allDrivers = allDrivers.stream()
                    .filter(d -> approvalStatus.equalsIgnoreCase(d.getApprovalStatus()))
                    .collect(Collectors.toList());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allDrivers.size());

        List<DriverDto> content = new ArrayList<>();
        if (start <= allDrivers.size()) {
            content = allDrivers.subList(start, end).stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        }

        Page<DriverDto> pageResult = new PageImpl<>(content, pageable, allDrivers.size());
        return ResponseEntity.ok(ApiResponse.success("Drivers retrieved successfully", pageResult));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverDto>> createDriver(
            @RequestBody DriverDto driverDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        String rawEmail = driverDto.getEmail();
        if (rawEmail == null || rawEmail.trim().isBlank()) {
            throw new BadRequestException("Driver email is required.");
        }
        String normalizedEmail = rawEmail.trim().toLowerCase();

        if (driverDto.getPhone() != null && !driverDto.getPhone().isBlank()) {
            validatePhoneNumber(driverDto.getPhone());
        }

        // Split name into first and last
        String firstName = "Demo";
        String lastName = "Driver";
        if (driverDto.getName() != null) {
            String[] parts = driverDto.getName().split(" ", 2);
            firstName = parts[0];
            if (parts.length > 1) {
                lastName = parts[1];
            }
        }

        String initialPassword = (driverDto.getPassword() != null && !driverDto.getPassword().isBlank())
                ? driverDto.getPassword()
                : "Driver@123";
        if (initialPassword.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long");
        }

        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        User user;
        Driver driver;
        if (existingUserOpt.isPresent()) {
            User existing = existingUserOpt.get();
            if (existing.getDeletedAt() == null) {
                throw new BadRequestException("User email " + driverDto.getEmail() + " already exists");
            }
            existing.setDeletedAt(null);
            existing.setActive(true);
            existing.setFirstName(firstName);
            existing.setLastName(lastName);
            existing.setPhoneNumber(driverDto.getPhone() != null ? driverDto.getPhone() : "9876543210");
            existing.setPasswordHash(passwordEncoder.encode(initialPassword));
            existing.setRole(Role.DRIVER);
            existing.setCollege(userPrincipal != null ? userPrincipal.getUser().getCollege() : null);
            user = userRepository.save(existing);
            driver = driverRepository.findByUser(user).orElse(Driver.builder().user(user).college(userPrincipal != null ? userPrincipal.getUser().getCollege() : null).build());
        } else {
            if (driverRepository.findByLicenseNumber(driverDto.getLicenseNumber()).isPresent()) {
                throw new BadRequestException("License number " + driverDto.getLicenseNumber() + " already exists");
            }

            if (driverDto.getEmployeeId() != null &&
                    driverRepository.findByEmployeeId(driverDto.getEmployeeId()).isPresent()) {
                throw new BadRequestException("Employee ID " + driverDto.getEmployeeId() + " already exists");
            }

            user = User.builder()
                    .email(normalizedEmail)
                    .passwordHash(passwordEncoder.encode(initialPassword))
                    .firstName(firstName)
                    .lastName(lastName)
                    .phoneNumber(driverDto.getPhone() != null ? driverDto.getPhone() : "9876543210")
                    .role(Role.DRIVER)
                    .college(userPrincipal != null ? userPrincipal.getUser().getCollege() : null)
                    .isActive(true)
                    .build();
            user = userRepository.save(user);
            driver = Driver.builder()
                    .user(user)
                    .college(userPrincipal != null ? userPrincipal.getUser().getCollege() : null)
                    .build();
        }

        driver.setCollege(userPrincipal != null ? userPrincipal.getUser().getCollege() : null);
        driver.setLicenseNumber(driverDto.getLicenseNumber());
        driver.setEmployeeId(driverDto.getEmployeeId());
        driver.setLicenseExpiry(driverDto.getLicenseExpiry());
        driver.setEmergencyContact(driverDto.getEmergencyContact());
        driver.setApprovalStatus(driverDto.getApprovalStatus() != null ? driverDto.getApprovalStatus().toUpperCase() : "PENDING");
        driver.setApproved("APPROVED".equalsIgnoreCase(driverDto.getApprovalStatus()));
        driver.setStatus("AVAILABLE");
        driver.setAverageRating(new BigDecimal("5.00"));

        Driver savedDriver = driverRepository.save(driver);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "DRIVER_CREATED",
                "Created driver: " + user.getFirstName() + " " + user.getLastName() + " (Employee ID: " + driverDto.getEmployeeId() + ")",
                "0.0.0.0",
                "Driver",
                savedDriver.getId().toString(),
                null,
                user.getEmail()
        );

        return ResponseEntity.ok(ApiResponse.success("Driver registered successfully", mapToDto(savedDriver)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverDto>> updateDriver(
            @PathVariable UUID id,
            @RequestBody DriverDto driverDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
        validateDriverBelongsToCollege(driver, userPrincipal);

        User user = driver.getUser();

        if (driverDto.getEmail() != null && !driverDto.getEmail().trim().equalsIgnoreCase(user.getEmail())) {
            String newEmail = driverDto.getEmail().trim().toLowerCase();
            if (userRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new BadRequestException("User email " + driverDto.getEmail() + " already exists");
            }
            user.setEmail(newEmail);
        }

        if (driverDto.getPassword() != null && !driverDto.getPassword().isBlank()) {
            if (driverDto.getPassword().length() < 8) {
                throw new BadRequestException("Password must be at least 8 characters long");
            }
            user.setPasswordHash(passwordEncoder.encode(driverDto.getPassword()));
            refreshTokenRepository.deleteByUser(user);
        }

        if (driverDto.getPhone() != null && !driverDto.getPhone().isBlank()) {
            validatePhoneNumber(driverDto.getPhone());
            user.setPhoneNumber(driverDto.getPhone());
        }

        if (driverDto.getName() != null && !driverDto.getName().isBlank()) {
            String[] parts = driverDto.getName().split(" ", 2);
            user.setFirstName(parts[0]);
            if (parts.length > 1) {
                user.setLastName(parts[1]);
            }
        }
        userRepository.save(user);

        if (driverDto.getLicenseNumber() != null) {
            driver.setLicenseNumber(driverDto.getLicenseNumber());
        }
        if (driverDto.getEmployeeId() != null) {
            driver.setEmployeeId(driverDto.getEmployeeId());
        }
        if (driverDto.getLicenseExpiry() != null) {
            driver.setLicenseExpiry(driverDto.getLicenseExpiry());
        }
        if (driverDto.getEmergencyContact() != null) {
            driver.setEmergencyContact(driverDto.getEmergencyContact());
        }
        Driver savedDriver = driverRepository.save(driver);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "DRIVER_UPDATED",
                "Updated driver: " + user.getEmail(),
                "0.0.0.0",
                "Driver",
                savedDriver.getId().toString(),
                null,
                user.getEmail()
        );

        return ResponseEntity.ok(ApiResponse.success("Driver updated successfully", mapToDto(savedDriver)));
    }

    @RequestMapping(value = {"/{id}/password", "/{id}/set-password"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<Void>> setDriverPassword(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody com.smartbus.infrastructure.dto.SetDriverPasswordRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long");
        }

        if (request.getConfirmPassword() == null || !request.getConfirmPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
        validateDriverBelongsToCollege(driver, userPrincipal);

        User user = driver.getUser();
        if (user == null || user.getDeletedAt() != null) {
            throw new BadRequestException("Cannot set password: User account not found or deleted.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenRepository.deleteByUser(user);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "DRIVER_PASSWORD_SET",
                "Administrator set new password for driver: " + user.getEmail(),
                "0.0.0.0",
                "Driver",
                driver.getId().toString(),
                null,
                null
        );

        return ResponseEntity.ok(ApiResponse.success("Password updated successfully.", null));
    }

    private void validatePhoneNumber(String phone) {
        if (phone != null && !phone.isBlank() && !phone.matches("^\\+?[0-9]{7,15}$")) {
            throw new BadRequestException("Invalid phone number format. Must contain 7 to 15 digits with optional leading +.");
        }
    }

    @RequestMapping(value = {"/{id}/approval", "/{id}/approve"}, method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<ApiResponse<DriverDto>> updateApproval(
            @PathVariable UUID id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approvalStatus,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        String resolvedStatus = (approvalStatus != null && !approvalStatus.isBlank()) ? approvalStatus : status;
        if (resolvedStatus == null || resolvedStatus.isBlank()) {
            throw new BadRequestException("Approval status is required.");
        }

        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
        validateDriverBelongsToCollege(driver, userPrincipal);

        String oldApproval = driver.getApprovalStatus();
        driver.setApprovalStatus(resolvedStatus.toUpperCase());
        driver.setApproved("APPROVED".equalsIgnoreCase(resolvedStatus));
        Driver savedDriver = driverRepository.save(driver);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "APPROVED".equalsIgnoreCase(resolvedStatus) ? "DRIVER_APPROVED" : "DRIVER_REJECTED",
                "Changed driver approval status of " + driver.getUser().getEmail() + " to " + resolvedStatus,
                "0.0.0.0",
                "Driver",
                driver.getId().toString(),
                oldApproval,
                resolvedStatus
        );

        return ResponseEntity.ok(ApiResponse.success("Driver approval status updated to " + resolvedStatus, mapToDto(savedDriver)));
    }

    @RequestMapping(value = {"/{id}/status", "/{id}/suspend"}, method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<ApiResponse<DriverDto>> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
        validateDriverBelongsToCollege(driver, userPrincipal);

        String oldStatus = driver.getStatus();
        driver.setStatus(status.toUpperCase());
        Driver savedDriver = driverRepository.save(driver);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "SUSPENDED".equalsIgnoreCase(status) ? "DRIVER_SUSPENDED" : "DRIVER_STATUS_CHANGED",
                "Changed status of driver " + driver.getUser().getEmail() + " to " + status,
                "0.0.0.0",
                "Driver",
                driver.getId().toString(),
                oldStatus,
                status
        );

        return ResponseEntity.ok(ApiResponse.success("Driver status updated to " + status, mapToDto(savedDriver)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDriver(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
        validateDriverBelongsToCollege(driver, userPrincipal);

        User user = driver.getUser();
        if (user.getDeletedAt() != null) {
            throw new BadRequestException("Driver is already deleted.");
        }

        // Safety Validation 1: Check active trip
        var activeTripOpt = tripRepository.findByDriverIdAndStatusIn(driver.getId(), List.of("EN_ROUTE", "PAUSED"));
        if (activeTripOpt.isPresent()) {
            throw new BadRequestException("Cannot delete driver: Driver is currently assigned to an active trip (" + activeTripOpt.get().getStatus() + "). End the trip first.");
        }

        // Safety Validation 2: Check active schedules
        var activeSchedules = scheduleRepository.findByDriverIdAndDeletedAtIsNull(driver.getId());
        if (!activeSchedules.isEmpty()) {
            throw new BadRequestException("Cannot delete driver: Driver is assigned to " + activeSchedules.size() + " active schedule(s). Reassign or delete schedules first.");
        }

        // Soft delete user and mark driver INACTIVE
        user.setDeletedAt(java.time.LocalDateTime.now());
        user.setActive(false);
        userRepository.save(user);

        driver.setStatus("INACTIVE");
        driverRepository.save(driver);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "DRIVER_DELETED",
                "Soft-deleted driver: " + user.getEmail(),
                "0.0.0.0",
                "Driver",
                driver.getId().toString(),
                null,
                null
        );

        return ResponseEntity.ok(ApiResponse.success("Driver deleted successfully", null));
    }

    private DriverDto mapToDto(Driver driver) {
        return DriverDto.builder()
                .id(driver.getId())
                .userId(driver.getUser().getId())
                .name(driver.getUser().getFirstName() + " " + driver.getUser().getLastName())
                .phone(driver.getUser().getPhoneNumber())
                .email(driver.getUser().getEmail())
                .licenseNumber(driver.getLicenseNumber())
                .licenseExpiry(driver.getLicenseExpiry())
                .emergencyContact(driver.getEmergencyContact())
                .status(driver.getStatus())
                .approvalStatus(driver.getApprovalStatus())
                .employeeId(driver.getEmployeeId())
                .averageRating(driver.getAverageRating())
                .build();
    }

    private void validateDriverBelongsToCollege(Driver driver, UserPrincipal userPrincipal) {
        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            if (driver.getCollege() == null || !userPrincipal.getCollegeId().equals(driver.getCollege().getId())) {
                throw new ResourceNotFoundException("Driver not found with id: " + driver.getId());
            }
        }
    }
}
