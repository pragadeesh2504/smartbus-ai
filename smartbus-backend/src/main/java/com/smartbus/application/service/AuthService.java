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
            // Handle ADMIN / SUPER_ADMIN matching
            boolean matches = storedRole.equalsIgnoreCase(selectedRole) ||
                    (selectedRole.equals("ADMIN") && storedRole.equals("SUPER_ADMIN"));
            if (!matches) {
                log.warn("Login role mismatch for user {}: selected {}, stored {}", user.getEmail(), selectedRole, storedRole);
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
                .build();
    }

    @Override
    public LoginResponse loginWithGoogle(String idToken) {
        return loginWithGoogle(idToken, null);
    }

    @Override
    public LoginResponse loginWithGoogle(String idToken, String selectedRole) {
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
            boolean matches = storedRole.equalsIgnoreCase(normalizedSelected) ||
                    (normalizedSelected.equals("ADMIN") && storedRole.equals("SUPER_ADMIN"));
            if (!matches) {
                log.warn("Google login role mismatch for user {}: selected {}, stored {}", email, normalizedSelected, storedRole);
                throw new BadRequestException("Access denied: The selected role does not match this account.");
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

        // Generate JWT token with existing stored role
        String jwt = tokenProvider.generateTokenFromUsername(user.getEmail());
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("Google authentication successful for user: {} with role: {}", user.getEmail(), user.getRole());

        return LoginResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .name(user.getFirstName() + " " + user.getLastName())
                .build();
    }

    @Override
    public void register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BadRequestException("Email address already in use!");
        }

        Role roleVal;
        try {
            roleVal = Role.valueOf(registerRequest.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role specified. Supported: STUDENT, DRIVER");
        }

        User user = User.builder()
                .email(registerRequest.getEmail())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .phoneNumber(registerRequest.getPhoneNumber())
                .role(roleVal)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        if (roleVal == Role.STUDENT) {
            if (registerRequest.getStudentId() == null || registerRequest.getDepartment() == null || registerRequest.getBatch() == null) {
                throw new BadRequestException("Student fields (studentId, department, batch) are required for student registration");
            }
            Student student = Student.builder()
                    .user(savedUser)
                    .studentId(registerRequest.getStudentId())
                    .department(registerRequest.getDepartment())
                    .batch(registerRequest.getBatch())
                    .build();
            studentRepository.save(student);
        } else if (roleVal == Role.DRIVER) {
            if (registerRequest.getLicenseNumber() == null) {
                throw new BadRequestException("License number is required for driver registration");
            }
            Driver driver = Driver.builder()
                    .user(savedUser)
                    .licenseNumber(registerRequest.getLicenseNumber())
                    .isApproved(false) // requires admin approval
                    .status("AVAILABLE")
                    .build();
            driverRepository.save(driver);
        }
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenRepository.findByToken(requestRefreshToken)
                .map(this::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = tokenProvider.generateTokenFromUsername(user.getEmail());
                    return LoginResponse.builder()
                            .accessToken(token)
                            .refreshToken(requestRefreshToken)
                            .email(user.getEmail())
                            .role(user.getRole().name())
                            .name(user.getFirstName() + " " + user.getLastName())
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
