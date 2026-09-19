package com.smartbus.infrastructure.controller;

import com.smartbus.application.port.in.AuthUseCase;
import com.smartbus.domain.exception.RateLimitExceededException;
import com.smartbus.infrastructure.dto.LoginRequest;
import com.smartbus.infrastructure.dto.LoginResponse;
import com.smartbus.security.ratelimit.RateLimitResult;
import com.smartbus.security.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private AuthUseCase authUseCase;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AuthController authController;

    private LoginRequest loginRequest;
    private LoginResponse loginResponse;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest();
        loginRequest.setEmail("driver@smartbus.ai");
        loginRequest.setPassword("Password123!");

        loginResponse = LoginResponse.builder()
                .accessToken("access-token-123")
                .refreshToken("refresh-token-456")
                .role("DRIVER")
                .name("Driver Name")
                .build();
    }

    @Test
    void testLogin_AllowedUnderLimit() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.50");
        when(rateLimitService.checkLoginLimit("192.168.1.50", "driver@smartbus.ai"))
                .thenReturn(RateLimitResult.allow());
        when(authUseCase.login(loginRequest)).thenReturn(loginResponse);

        ResponseEntity<LoginResponse> response = authController.authenticateUser(loginRequest, request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("access-token-123", response.getBody().getAccessToken());
        verify(authUseCase, times(1)).login(loginRequest);
    }

    @Test
    void testLogin_BlockedWhenLimitExceeded() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.50");
        when(rateLimitService.checkLoginLimit("192.168.1.50", "driver@smartbus.ai"))
                .thenReturn(RateLimitResult.block(60));

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> authController.authenticateUser(loginRequest, request)
        );

        assertEquals(60, ex.getRetryAfterSeconds());
        assertTrue(ex.getMessage().contains("Too many login attempts"));
        verify(authUseCase, never()).login(any());
    }

    @Test
    void testGoogleLogin_Success() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.50");
        when(rateLimitService.checkGoogleAuthLimit("192.168.1.50")).thenReturn(RateLimitResult.allow());
        when(authUseCase.loginWithGoogle(eq("valid-token"), any(), any())).thenReturn(loginResponse);

        com.smartbus.infrastructure.dto.GoogleLoginRequest googleReq = new com.smartbus.infrastructure.dto.GoogleLoginRequest("valid-token", "DRIVER");
        ResponseEntity<LoginResponse> response = authController.authenticateGoogleUser(googleReq, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("access-token-123", response.getBody().getAccessToken());
    }

    @Test
    void testForgotPassword_ReturnsGenericSuccess() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.50");
        when(rateLimitService.checkForgotPasswordLimit("192.168.1.50", "someone@smartbus.edu"))
                .thenReturn(RateLimitResult.allow());

        com.smartbus.infrastructure.dto.ForgotPasswordRequest req = new com.smartbus.infrastructure.dto.ForgotPasswordRequest("someone@smartbus.edu");
        ResponseEntity<java.util.Map<String, String>> response = authController.forgotPassword(req, null, request);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().get("message").contains("If the account exists"));
        verify(authUseCase, times(1)).forgotPassword("someone@smartbus.edu");
    }

    @Test
    void testResetPassword_WithToken_Success() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.50");
        when(rateLimitService.checkResetPasswordLimit("192.168.1.50")).thenReturn(RateLimitResult.allow());

        com.smartbus.infrastructure.dto.ResetPasswordRequest resetReq = com.smartbus.infrastructure.dto.ResetPasswordRequest.builder()
                .token("valid-token")
                .newPassword("NewPassword@123")
                .confirmPassword("NewPassword@123")
                .build();

        ResponseEntity<java.util.Map<String, String>> response = authController.resetPassword(resetReq, null, null, null, request);

        assertEquals(200, response.getStatusCode().value());
        verify(authUseCase, times(1)).resetPasswordWithToken(resetReq);
    }
}
