package com.smartbus.application.service;

import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.College;
import com.smartbus.domain.model.Driver;
import com.smartbus.domain.model.PasswordResetToken;
import com.smartbus.domain.model.Role;
import com.smartbus.domain.model.User;
import com.smartbus.infrastructure.adapter.jpa.CollegeRepository;
import com.smartbus.infrastructure.adapter.jpa.DriverRepository;
import com.smartbus.infrastructure.adapter.jpa.PasswordResetTokenRepository;
import com.smartbus.infrastructure.adapter.jpa.RefreshTokenRepository;
import com.smartbus.infrastructure.adapter.jpa.UserRepository;
import com.smartbus.infrastructure.dto.GoogleUserInfo;
import com.smartbus.infrastructure.dto.LoginRequest;
import com.smartbus.infrastructure.dto.LoginResponse;
import com.smartbus.infrastructure.dto.ResetPasswordRequest;
import com.smartbus.security.JwtTokenProvider;
import com.smartbus.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthEnhancementTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private GoogleAuthService googleAuthService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private CollegeRepository collegeRepository;

    @InjectMocks
    private AuthService authService;

    private User activeAdminUser;
    private User activeSuperAdminUser;
    private User activeDriverUser;
    private User activeStudentUser;
    private User inactiveUser;
    private College testCollege;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(authService, "refreshTokenDurationMs", 604800000L);

        testCollege = College.builder()
                .id(UUID.randomUUID())
                .name("Chennai Institute of Technology")
                .collegeCode("CIT")
                .status("ACTIVE")
                .build();

        activeAdminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin.personal@gmail.com")
                .firstName("Super")
                .lastName("Admin")
                .phoneNumber("+919876500000")
                .role(Role.ADMIN)
                .isActive(true)
                .passwordHash("hashed_admin_pw")
                .build();

        activeSuperAdminUser = User.builder()
                .id(UUID.randomUUID())
                .email("superadmin@smartbus.com")
                .firstName("Platform")
                .lastName("SuperAdmin")
                .phoneNumber("+919999999999")
                .role(Role.SUPER_ADMIN)
                .college(null)
                .isActive(true)
                .passwordHash("hashed_superadmin_pw")
                .build();

        activeDriverUser = User.builder()
                .id(UUID.randomUUID())
                .email("driver.assigned@gmail.com")
                .firstName("Ramesh")
                .lastName("Kumar")
                .phoneNumber("+919876543210")
                .role(Role.DRIVER)
                .isActive(true)
                .passwordHash("hashed_pw")
                .build();

        activeStudentUser = User.builder()
                .id(UUID.randomUUID())
                .email("student@college.edu")
                .firstName("Priya")
                .lastName("Sharma")
                .phoneNumber("+919876501234")
                .role(Role.STUDENT)
                .college(testCollege)
                .isActive(true)
                .passwordHash("hashed_pw")
                .build();

        inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .email("inactive@college.edu")
                .firstName("Inactive")
                .lastName("User")
                .phoneNumber("+919876599999")
                .role(Role.STUDENT)
                .college(testCollege)
                .isActive(false)
                .passwordHash("hashed_pw")
                .build();
    }

    // -------------------------------------------------------------
    // ROLE-BASED LOGIN (EMAIL + PASSWORD) TESTS
    // -------------------------------------------------------------

    @Test
    void testLogin_Student_CorrectEmailAndPasswordAndRole_Passes() {
        UserPrincipal principal = new UserPrincipal(activeStudentUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("CIT", "ACTIVE")).thenReturn(Optional.of(testCollege));
        when(tokenProvider.generateToken(auth)).thenReturn("jwt-student");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoginRequest req = LoginRequest.builder()
                .email("student@college.edu")
                .password("Student@123")
                .role("STUDENT")
                .collegeCode("CIT")
                .build();

        LoginResponse resp = authService.login(req);
        assertNotNull(resp);
        assertEquals("jwt-student", resp.getAccessToken());
        assertEquals("STUDENT", resp.getRole());
    }

    @Test
    void testLogin_Student_WrongEmail_FailsAuthentication() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest req = LoginRequest.builder()
                .email("wrong.student@college.edu")
                .password("Student@123")
                .role("STUDENT")
                .build();

        assertThrows(BadCredentialsException.class, () -> authService.login(req));
    }

    @Test
    void testLogin_Student_CorrectEmail_WrongRoleSelected_Rejects() {
        UserPrincipal principal = new UserPrincipal(activeStudentUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest req = LoginRequest.builder()
                .email("student@college.edu")
                .password("Student@123")
                .role("DRIVER") // Selected DRIVER but stored as STUDENT
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("selected role does not match"));
        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    @Test
    void testLogin_Driver_AdminRegisteredEmail_Passes() {
        Driver mockDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(activeDriverUser)
                .licenseNumber("DL-12345")
                .isApproved(true)
                .approvalStatus("APPROVED")
                .status("AVAILABLE")
                .build();
        when(driverRepository.findByUser(activeDriverUser)).thenReturn(Optional.of(mockDriver));

        UserPrincipal principal = new UserPrincipal(activeDriverUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("jwt-driver");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoginRequest req = LoginRequest.builder()
                .email("driver.assigned@gmail.com")
                .password("Driver@123")
                .role("DRIVER")
                .build();

        LoginResponse resp = authService.login(req);
        assertNotNull(resp);
        assertEquals("DRIVER", resp.getRole());
    }

    @Test
    void testLogin_Driver_SelectedAdminRole_Rejects() {
        UserPrincipal principal = new UserPrincipal(activeDriverUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest req = LoginRequest.builder()
                .email("driver.assigned@gmail.com")
                .password("Driver@123")
                .role("ADMIN") // Selected ADMIN but stored as DRIVER
                .build();

        assertThrows(BadRequestException.class, () -> authService.login(req));
    }

    @Test
    void testLogin_Admin_PersonalEmail_Passes() {
        UserPrincipal principal = new UserPrincipal(activeAdminUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("jwt-admin");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoginRequest req = LoginRequest.builder()
                .email("admin.personal@gmail.com")
                .password("Admin@123")
                .role("ADMIN")
                .build();

        LoginResponse resp = authService.login(req);
        assertNotNull(resp);
        assertEquals("ADMIN", resp.getRole());
    }

    @Test
    void testLogin_SuperAdmin_CorrectCredentialsAndRole_Passes() {
        UserPrincipal principal = new UserPrincipal(activeSuperAdminUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("jwt-superadmin");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoginRequest req = LoginRequest.builder()
                .email("superadmin@smartbus.com")
                .password("Password123!")
                .role("SUPER_ADMIN")
                .build();

        LoginResponse resp = authService.login(req);
        assertNotNull(resp);
        assertEquals("jwt-superadmin", resp.getAccessToken());
        assertEquals("SUPER_ADMIN", resp.getRole());
        assertNull(resp.getCollegeId());
    }

    @Test
    void testLogin_SuperAdmin_SelectedAdminRole_RejectsAndDirectsToSuperAdminLogin() {
        UserPrincipal principal = new UserPrincipal(activeSuperAdminUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest req = LoginRequest.builder()
                .email("superadmin@smartbus.com")
                .password("Password123!")
                .role("ADMIN") // Selected ADMIN on normal login
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("Super Admin portal"));
        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    @Test
    void testLogin_Admin_SelectedSuperAdminRole_RejectsElevation() {
        UserPrincipal principal = new UserPrincipal(activeAdminUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest req = LoginRequest.builder()
                .email("admin.personal@gmail.com")
                .password("Admin@123")
                .role("SUPER_ADMIN") // Attempting elevation to SUPER_ADMIN
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("selected role does not match"));
        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    @Test
    void testLogin_Student_SelectedSuperAdminRole_RejectsElevation() {
        UserPrincipal principal = new UserPrincipal(activeStudentUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest req = LoginRequest.builder()
                .email("student@college.edu")
                .password("Student@123")
                .role("SUPER_ADMIN") // Student attempting elevation to SUPER_ADMIN
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("selected role does not match"));
        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    // -------------------------------------------------------------
    // GOOGLE AUTHENTICATION TESTS (WITH ROLE VERIFICATION)
    // -------------------------------------------------------------

    @Test
    void testGoogleLogin_ValidGoogleIdentity_MatchingDriverRole_Passes() {
        Driver mockDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(activeDriverUser)
                .licenseNumber("DL-12345")
                .isApproved(true)
                .approvalStatus("APPROVED")
                .status("AVAILABLE")
                .build();
        when(driverRepository.findByUser(activeDriverUser)).thenReturn(Optional.of(mockDriver));

        when(googleAuthService.verifyToken("valid-google-token"))
                .thenReturn(new GoogleUserInfo("driver.assigned@gmail.com", "Ramesh Kumar", true, "google-sub-123"));
        when(userRepository.findByEmailAndDeletedAtIsNull("driver.assigned@gmail.com"))
                .thenReturn(Optional.of(activeDriverUser));
        when(tokenProvider.generateTokenForUser(activeDriverUser))
                .thenReturn("mock-jwt-driver");
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = authService.loginWithGoogle("valid-google-token", "DRIVER");

        assertNotNull(response);
        assertEquals("mock-jwt-driver", response.getAccessToken());
        assertEquals("driver.assigned@gmail.com", response.getEmail());
        assertEquals("DRIVER", response.getRole());
        verify(refreshTokenRepository).deleteByUser(activeDriverUser);
    }

    @Test
    void testGoogleLogin_ValidGoogleIdentity_MatchingStudentRole_Passes() {
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("CIT", "ACTIVE"))
                .thenReturn(Optional.of(testCollege));
        when(googleAuthService.verifyToken("valid-google-token-student"))
                .thenReturn(new GoogleUserInfo("student@college.edu", "Priya Sharma", true, "google-sub-456"));
        when(userRepository.findByEmailAndDeletedAtIsNull("student@college.edu"))
                .thenReturn(Optional.of(activeStudentUser));
        when(tokenProvider.generateTokenForUser(activeStudentUser))
                .thenReturn("mock-jwt-student");
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = authService.loginWithGoogle("valid-google-token-student", "STUDENT", "CIT");

        assertNotNull(response);
        assertEquals("STUDENT", response.getRole());
    }

    @Test
    void testGoogleLogin_ValidGoogleIdentity_RoleMismatch_Rejects() {
        when(googleAuthService.verifyToken("valid-google-token-student"))
                .thenReturn(new GoogleUserInfo("student@college.edu", "Priya Sharma", true, "google-sub-456"));
        when(userRepository.findByEmailAndDeletedAtIsNull("student@college.edu"))
                .thenReturn(Optional.of(activeStudentUser));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.loginWithGoogle("valid-google-token-student", "DRIVER")); // Student tried logging in as DRIVER
        assertTrue(ex.getMessage().contains("selected role does not match"));
        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    @Test
    void testGoogleLogin_UnregisteredGoogleEmail_ThrowsSafeException() {
        when(googleAuthService.verifyToken("unknown-token"))
                .thenReturn(new GoogleUserInfo("unknown@gmail.com", "Unknown User", true, "sub-000"));
        when(userRepository.findByEmailAndDeletedAtIsNull("unknown@gmail.com"))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> authService.loginWithGoogle("unknown-token", "STUDENT"));
        assertTrue(ex.getMessage().contains("not registered for SmartBus"));
    }

    @Test
    void testGoogleLogin_InactiveUser_ThrowsBadRequest() {
        when(googleAuthService.verifyToken("inactive-token"))
                .thenReturn(new GoogleUserInfo("inactive@college.edu", "Inactive User", true, "sub-111"));
        when(userRepository.findByEmailAndDeletedAtIsNull("inactive@college.edu"))
                .thenReturn(Optional.of(inactiveUser));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.loginWithGoogle("inactive-token", "STUDENT"));
        assertTrue(ex.getMessage().contains("inactive"));
    }

    // -------------------------------------------------------------
    // FORGOT PASSWORD TESTS
    // -------------------------------------------------------------

    @Test
    void testForgotPassword_UnknownEmail_DoesNotThrowAndDoesNotCreateToken() {
        when(userRepository.findByEmailAndDeletedAtIsNull("nonexistent@smartbus.edu"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword("nonexistent@smartbus.edu"));

        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void testForgotPassword_ValidEmail_CreatesResetTokenAndDispatchesEmail() {
        when(userRepository.findByEmailAndDeletedAtIsNull("student@college.edu"))
                .thenReturn(Optional.of(activeStudentUser));
        when(passwordResetTokenRepository.findByUserAndUsedAtIsNull(activeStudentUser))
                .thenReturn(Collections.emptyList());

        authService.forgotPassword("student@college.edu");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertNotNull(savedToken.getTokenHash());
        assertEquals(activeStudentUser, savedToken.getUser());
        assertTrue(savedToken.getExpiryTime().isAfter(Instant.now()));

        verify(emailService).sendPasswordResetEmail(eq("student@college.edu"), contains("/reset-password?token="));
    }

    // -------------------------------------------------------------
    // RESET PASSWORD TESTS
    // -------------------------------------------------------------

    @Test
    void testResetPasswordWithToken_Success_UpdatesPasswordAndInvalidatesToken() {
        String rawToken = "sample-raw-reset-token-1234567890";
        PasswordResetToken mockResetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(activeStudentUser)
                .tokenHash("hashed")
                .expiryTime(Instant.now().plusSeconds(600))
                .build();

        when(passwordResetTokenRepository.findByTokenHashAndUsedAtIsNullAndExpiryTimeAfter(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(mockResetToken));
        when(passwordEncoder.encode("NewSecurePassword@123")).thenReturn("new_hashed_password");

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(rawToken)
                .newPassword("NewSecurePassword@123")
                .confirmPassword("NewSecurePassword@123")
                .build();

        assertDoesNotThrow(() -> authService.resetPasswordWithToken(request));

        assertEquals("new_hashed_password", activeStudentUser.getPasswordHash());
        assertNotNull(mockResetToken.getUsedAt());
        verify(passwordResetTokenRepository).save(mockResetToken);
        verify(refreshTokenRepository).deleteByUser(activeStudentUser);
    }

    @Test
    void testResetPasswordWithToken_UsedOrExpiredToken_ThrowsBadRequest() {
        when(passwordResetTokenRepository.findByTokenHashAndUsedAtIsNullAndExpiryTimeAfter(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("used-or-expired-token")
                .newPassword("NewSecurePassword@123")
                .confirmPassword("NewSecurePassword@123")
                .build();

        assertThrows(BadRequestException.class, () -> authService.resetPasswordWithToken(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testResetPasswordWithToken_PasswordMismatch_ThrowsBadRequest() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("some-token")
                .newPassword("PasswordA@123")
                .confirmPassword("PasswordB@123")
                .build();

        assertThrows(BadRequestException.class, () -> authService.resetPasswordWithToken(request));
    }

    @Test
    void testResetPasswordWithToken_TooShortPassword_ThrowsBadRequest() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("some-token")
                .newPassword("short")
                .confirmPassword("short")
                .build();

        assertThrows(BadRequestException.class, () -> authService.resetPasswordWithToken(request));
    }
}