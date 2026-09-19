package com.smartbus.application.service;

import com.smartbus.application.port.in.AuthUseCase;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.dto.*;
import com.smartbus.security.JwtTokenProvider;
import com.smartbus.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthService implements AuthUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final GoogleAuthService googleAuthService;
    private final CollegeRepository collegeRepository;
    
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshTokenDurationMs;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // Concurrent map for OTP storing: email -> otp
    private final Map<String, String> otpCache = new ConcurrentHashMap<>();

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String normalizedEmail = loginRequest.getEmail() != null ? loginRequest.getEmail().trim().toLowerCase() : "";
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        loginRequest.getPassword()
                )
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userPrincipal.getUser();

        // Enforce role check: selected role from UI must match the authoritative stored role
        if (loginRequest.getRole() != null && !loginRequest.getRole().isBlank()) {
            String selectedRole = loginRequest.getRole().trim().toUpperCase();
            String storedRole = user.getRole().name();
            if (!storedRole.equalsIgnoreCase(selectedRole)) {
                log.warn("Login role mismatch for user {}: selected {}, stored {}", user.getEmail(), selectedRole, storedRole);
                if ("SUPER_ADMIN".equalsIgnoreCase(storedRole)) {
                    throw new BadRequestException("Access denied: Platform Super Admin accounts must sign in via the Super Admin portal (/super-admin/login).");
                }
                throw new BadRequestException("Access denied: The selected role does not match this account.");
            }
        }

        // Driver approval and suspension policy check
        if (user.getRole() == Role.DRIVER) {
            Driver driver = driverRepository.findByUser(user)
                    .orElseThrow(() -> new BadRequestException("Driver profile not found."));
            boolean isDriverApproved = Boolean.TRUE.equals(driver.isApproved()) || "APPROVED".equalsIgnoreCase(driver.getApprovalStatus());
            if (!isDriverApproved) {
                log.warn("Login rejected for unapproved driver {}", user.getEmail());
                throw new BadRequestException("Driver account is pending administrator approval.");
            }
            if ("SUSPENDED".equalsIgnoreCase(driver.getStatus())) {
                log.warn("Login rejected for suspended driver {}", user.getEmail());
                throw new BadRequestException("Driver account is suspended. Please contact administrator.");
            }
        }

        // Student college validation check
        if (user.getRole() == Role.STUDENT || (loginRequest.getRole() != null && "STUDENT".equalsIgnoreCase(loginRequest.getRole().trim()))) {
            String code = loginRequest.getCollegeCode();
            if (code == null || code.trim().isEmpty()) {
                throw new BadRequestException("College Code is required for student login.");
            }
            College college = collegeRepository.findByCollegeCodeIgnoreCaseAndStatus(code.trim(), "ACTIVE")
                    .orElseThrow(() -> new BadRequestException("Invalid or inactive College Code."));
            if (user.getCollege() == null || !user.getCollege().getId().equals(college.getId())) {
                log.warn("Cross-tenant student login rejected for user {} attempting college {}", user.getEmail(), code);
                throw new BadRequestException("Invalid credentials: Account does not belong to the specified college.");
            }
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        // Delete existing refresh tokens for the user
        refreshTokenRepository.deleteByUser(user);

        // Create new refresh token
        RefreshToken refreshToken = createRefreshToken(user);

        return LoginResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshToken.getToken())
                .email(userPrincipal.getUsername())
                .role(user.getRole().name())
                .name(user.getFirstName() + " " + user.getLastName())
                .collegeId(user.getCollege() != null ? user.getCollege().getId() : null)
                .collegeName(user.getCollege() != null ? user.getCollege().getName() : null)
                .collegeCode(user.getCollege() != null ? user.getCollege().getCollegeCode() : null)
                .build();
    }

    @Override
    public LoginResponse loginWithGoogle(String idToken) {
        return loginWithGoogle(idToken, null, null);
    }

    @Override
    public LoginResponse loginWithGoogle(String idToken, String selectedRole) {
        return loginWithGoogle(idToken, selectedRole, null);
    }

    @Override
    public LoginResponse loginWithGoogle(String idToken, String selectedRole, String collegeCode) {
        GoogleUserInfo googleUser = googleAuthService.verifyToken(idToken);
        if (googleUser == null || !googleUser.emailVerified()) {
            throw new BadRequestException("Google email is not verified or token is invalid");
        }

        String email = googleUser.email().trim().toLowerCase();
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("This Google account is not registered for SmartBus. Please contact the administrator."));

        if (!user.isActive()) {
            throw new BadRequestException("User account is inactive. Please contact the administrator.");
        }

        // Enforce role check: selected role from UI must match the authoritative stored role
        if (selectedRole != null && !selectedRole.isBlank()) {
            String normalizedSelected = selectedRole.trim().toUpperCase();
            String storedRole = user.getRole().name();
            if (!storedRole.equalsIgnoreCase(normalizedSelected)) {
                log.warn("Google login role mismatch for user {}: selected {}, stored {}", email, normalizedSelected, storedRole);
                if ("SUPER_ADMIN".equalsIgnoreCase(storedRole)) {
                    throw new BadRequestException("Access denied: Platform Super Admin accounts must sign in via the Super Admin portal (/super-admin/login).");
                }
                throw new BadRequestException("Access denied: The selected role does not match this account.");
            }
        }

        // Student college validation check
        if (user.getRole() == Role.STUDENT || (selectedRole != null && "STUDENT".equalsIgnoreCase(selectedRole.trim()))) {
            if (collegeCode == null || collegeCode.trim().isEmpty()) {
                throw new BadRequestException("College Code is required for student Google login.");
            }
            College college = collegeRepository.findByCollegeCodeIgnoreCaseAndStatus(collegeCode.trim(), "ACTIVE")
                    .orElseThrow(() -> new BadRequestException("Invalid or inactive College Code."));
            if (user.getCollege() == null || !user.getCollege().getId().equals(college.getId())) {
                log.warn("Cross-tenant student Google login rejected for user {} attempting college {}", user.getEmail(), collegeCode);
                throw new BadRequestException("Invalid credentials: Account does not belong to the specified college.");
            }
        }

        // Driver approval and suspension policy check
        if (user.getRole() == Role.DRIVER) {
            Driver driver = driverRepository.findByUser(user)
                    .orElseThrow(() -> new BadRequestException("Driver profile not found."));
            boolean isDriverApproved = Boolean.TRUE.equals(driver.isApproved()) || "APPROVED".equalsIgnoreCase(driver.getApprovalStatus());
            if (!isDriverApproved) {
                log.warn("Google login rejected for unapproved driver {}", user.getEmail());
                throw new BadRequestException("Driver account is pending administrator approval.");
            }
            if ("SUSPENDED".equalsIgnoreCase(driver.getStatus())) {
                log.warn("Google login rejected for suspended driver {}", user.getEmail());
                throw new BadRequestException("Driver account is suspended. Please contact administrator.");
            }
        }

        // Delete existing refresh tokens for the user
        refreshTokenRepository.deleteByUser(user);

        // Generate complete JWT token with userId, role, and collegeId
        String jwt = tokenProvider.generateTokenForUser(user);
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("Google authentication successful for user: {} with role: {}", user.getEmail(), user.getRole());

        return LoginResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .name(user.getFirstName() + " " + user.getLastName())
                .collegeId(user.getCollege() != null ? user.getCollege().getId() : null)
                .collegeName(user.getCollege() != null ? user.getCollege().getName() : null)
                .collegeCode(user.getCollege() != null ? user.getCollege().getCollegeCode() : null)
                .build();
    }

    @Override
    public void register(RegisterRequest registerRequest) {
        if (registerRequest == null) {
            throw new BadRequestException("Registration request cannot be null");
        }

        // Hardening: Reject any attempt to register non-STUDENT roles via the public endpoint
        if (registerRequest.getRole() != null && !registerRequest.getRole().isBlank()) {
            String requestedRole = registerRequest.getRole().trim().toUpperCase();
            if (!"STUDENT".equals(requestedRole)) {
                log.warn("Public registration rejected attempted role escalation: {}", requestedRole);
                throw new BadRequestException("Public self-registration is restricted to students only. College administrators must register at /register/college and drivers are provisioned by college administrators.");
            }
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BadRequestException("Email address already in use!");
        }

        Role roleVal = Role.STUDENT;

        String collegeCode = registerRequest.getCollegeCode();
        if (collegeCode == null || collegeCode.trim().isEmpty()) {
            throw new BadRequestException("College Code is required for student registration");
        }
        College college = collegeRepository.findByCollegeCodeIgnoreCaseAndStatus(collegeCode.trim(), "ACTIVE")
                .orElseThrow(() -> new BadRequestException("Invalid or inactive College Code: " + collegeCode.trim()));

        User user = User.builder()
                .email(registerRequest.getEmail())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .phoneNumber(registerRequest.getPhoneNumber())
                .role(roleVal)
                .college(college)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        if (registerRequest.getStudentId() == null || registerRequest.getDepartment() == null || registerRequest.getBatch() == null) {
            throw new BadRequestException("Student fields (studentId, department, batch) are required for student registration");
        }
        if (studentRepository.existsByCollegeIdAndStudentId(college.getId(), registerRequest.getStudentId())) {
            throw new BadRequestException("Student ID " + registerRequest.getStudentId() + " already exists in this college");
        }
        Student student = Student.builder()
                .user(savedUser)
                .college(college)
                .studentId(registerRequest.getStudentId())
                .department(registerRequest.getDepartment())
                .batch(registerRequest.getBatch())
                .build();
        studentRepository.save(student);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerCollegeAdmin(RegisterCollegeAdminRequest request) {
        if (request == null) {
            throw new BadRequestException("Registration request cannot be null");
        }

        String collegeName = request.getCollegeName() != null ? request.getCollegeName().trim() : "";
        if (collegeName.length() < 2 || collegeName.length() > 150) {
            throw new BadRequestException("College name must be between 2 and 150 characters");
        }

        String rawCode = request.getCollegeCode();
        if (rawCode == null || rawCode.trim().isEmpty()) {
            throw new BadRequestException("College code is required");
        }
        String collegeCode = rawCode.trim().toUpperCase();
        if (!collegeCode.matches("^[A-Za-z0-9_-]{2,20}$")) {
            throw new BadRequestException("College code must be 2 to 20 characters (alphanumeric, dashes, underscores only)");
        }

        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (email.isEmpty()) {
            throw new BadRequestException("Email is required");
        }

        if (collegeRepository.existsByCollegeCodeIgnoreCase(collegeCode)) {
            throw new BadRequestException("College code already exists: " + collegeCode);
        }

        if (collegeRepository.existsByNameIgnoreCase(collegeName)) {
            throw new BadRequestException("College name already exists: " + collegeName);
        }

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email address already in use!");
        }

        // Atomically create College
        College college = College.builder()
                .name(collegeName)
                .collegeCode(collegeCode)
                .status("ACTIVE")
                .contactEmail(email)
                .build();
        College savedCollege = collegeRepository.save(college);
        log.info("Registered college: {} (code: {}) with id: {}", savedCollege.getName(), savedCollege.getCollegeCode(), savedCollege.getId());

        // Atomically create College Admin user linked directly to this college
        User collegeAdmin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName() != null ? request.getFirstName().trim() : "")
                .lastName(request.getLastName() != null ? request.getLastName().trim() : "")
                .phoneNumber(request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty() ? request.getPhoneNumber().trim() : null)
                .role(Role.ADMIN)
                .college(savedCollege)
                .isActive(true)
                .build();

        userRepository.save(collegeAdmin);
        log.info("Registered initial College Admin user: {} for college: {}", email, savedCollege.getName());
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenRepository.findByToken(requestRefreshToken)
                .map(this::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = tokenProvider.generateTokenForUser(user);
                    return LoginResponse.builder()
                            .accessToken(token)
                            .refreshToken(requestRefreshToken)
                            .email(user.getEmail())
                            .role(user.getRole().name())
                            .name(user.getFirstName() + " " + user.getLastName())
                            .collegeId(user.getCollege() != null ? user.getCollege().getId() : null)
                            .collegeName(user.getCollege() != null ? user.getCollege().getName() : null)
                            .collegeCode(user.getCollege() != null ? user.getCollege().getCollegeCode() : null)
                            .build();
                })
                .orElseThrow(() -> new BadRequestException("Refresh token is not in database!"));
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .token(UUID.randomUUID().toString())
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    private RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new BadRequestException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    private String hashToken(String rawToken) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        String safeEmail = email.trim().toLowerCase();
        userRepository.findByEmailAndDeletedAtIsNull(safeEmail).ifPresent(user -> {
            if (!user.isActive()) {
                return;
            }

            // Generate cryptographically secure random token (64 hex characters)
            String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            String tokenHash = hashToken(rawToken);

            // Invalidate any previously unused tokens for this user
            List<PasswordResetToken> existingTokens = passwordResetTokenRepository.findByUserAndUsedAtIsNull(user);
            for (PasswordResetToken pt : existingTokens) {
                pt.setUsedAt(Instant.now());
            }
            passwordResetTokenRepository.saveAll(existingTokens);

            // Save new single-use token with 15-minute expiry
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiryTime(Instant.now().plusSeconds(900)) // 15 minutes
                    .build();
            passwordResetTokenRepository.save(resetToken);

            // Dispatch reset email with link
            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

            // Backwards compatibility OTP cache
            String otp = String.valueOf((int) (Math.random() * 900000) + 100000);
            otpCache.put(safeEmail, otp);
        });
    }

    @Override
    public void resetPasswordWithToken(ResetPasswordRequest request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            throw new BadRequestException("Reset token is required");
        }

        String newPassword = request.getNewPassword();
        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long");
        }

        if (request.getConfirmPassword() != null && !request.getConfirmPassword().equals(newPassword)) {
            throw new BadRequestException("Passwords do not match");
        }

        String tokenHash = hashToken(request.getToken().trim());
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiryTimeAfter(tokenHash, Instant.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Invalidate token
        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        // Invalidate existing refresh tokens so active sessions are terminated
        refreshTokenRepository.deleteByUser(user);

        log.info("Password successfully updated via reset token for user: {}", user.getEmail());
    }

    @Override
    public boolean verifyOtp(String email, String otp) {
        if (email == null) return false;
        String cachedOtp = otpCache.get(email.trim().toLowerCase());
        return cachedOtp != null && cachedOtp.equals(otp);
    }

    @Override
    public void resetPassword(String email, String otp, String newPassword) {
        if (!verifyOtp(email, otp)) {
            throw new BadRequestException("Invalid or expired OTP");
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        otpCache.remove(email.trim().toLowerCase()); // consume OTP
    }
}
