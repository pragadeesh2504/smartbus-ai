package com.smartbus.infrastructure.controller;

import com.smartbus.application.port.in.AuthUseCase;
import com.smartbus.domain.exception.RateLimitExceededException;
import com.smartbus.infrastructure.dto.LoginRequest;
import com.smartbus.infrastructure.dto.LoginResponse;
import com.smartbus.infrastructure.dto.RegisterRequest;
import com.smartbus.infrastructure.dto.RefreshTokenRequest;
import com.smartbus.security.ratelimit.RateLimitResult;
import com.smartbus.security.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;
    private final RateLimitService rateLimitService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        String clientIp = getClientIp(request);
        RateLimitResult rateLimitResult = rateLimitService.checkLoginLimit(clientIp, loginRequest.getEmail());
        if (!rateLimitResult.isAllowed()) {
            throw new RateLimitExceededException("Too many login attempts. Please try again later.", rateLimitResult.getRetryAfterSeconds());
        }
        return ResponseEntity.ok(authUseCase.login(loginRequest));
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> authenticateGoogleUser(
            @Valid @RequestBody com.smartbus.infrastructure.dto.GoogleLoginRequest googleRequest,
            HttpServletRequest request) {
        String clientIp = getClientIp(request);
        RateLimitResult rateLimitResult = rateLimitService.checkGoogleAuthLimit(clientIp);
        if (!rateLimitResult.isAllowed()) {
            throw new RateLimitExceededException("Too many login attempts. Please try again later.", rateLimitResult.getRetryAfterSeconds());
        }
        return ResponseEntity.ok(authUseCase.loginWithGoogle(googleRequest.getIdToken(), googleRequest.getRole()));
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        authUseCase.register(registerRequest);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshAccessToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        return ResponseEntity.ok(authUseCase.refreshToken(refreshTokenRequest));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(
            @RequestBody(required = false) com.smartbus.infrastructure.dto.ForgotPasswordRequest body,
            @RequestParam(required = false) String email,
            HttpServletRequest request) {
        String targetEmail = (body != null && body.getEmail() != null) ? body.getEmail() : email;
        String clientIp = getClientIp(request);

        RateLimitResult rateLimitResult = rateLimitService.checkForgotPasswordLimit(clientIp, targetEmail);
        if (!rateLimitResult.isAllowed()) {
            throw new RateLimitExceededException("Too many password reset requests. Please try again later.", rateLimitResult.getRetryAfterSeconds());
        }

        if (targetEmail != null && !targetEmail.isBlank()) {
            authUseCase.forgotPassword(targetEmail);
        }

        // Always return generic response to prevent account enumeration
        return ResponseEntity.ok(java.util.Collections.singletonMap("message", "If the account exists, a password reset link has been sent."));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Boolean> verifyOtp(@RequestParam String email, @RequestParam String otp) {
        return ResponseEntity.ok(authUseCase.verifyOtp(email, otp));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(
            @RequestBody(required = false) com.smartbus.infrastructure.dto.ResetPasswordRequest resetRequest,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String otp,
            @RequestParam(required = false) String newPassword,
            HttpServletRequest request) {
        String clientIp = getClientIp(request);
        RateLimitResult rateLimitResult = rateLimitService.checkResetPasswordLimit(clientIp);
        if (!rateLimitResult.isAllowed()) {
            throw new RateLimitExceededException("Too many reset attempts. Please try again later.", rateLimitResult.getRetryAfterSeconds());
        }

        if (resetRequest != null && resetRequest.getToken() != null && !resetRequest.getToken().isBlank()) {
            authUseCase.resetPasswordWithToken(resetRequest);
        } else if (email != null && otp != null && newPassword != null) {
            authUseCase.resetPassword(email, otp, newPassword);
        } else {
            throw new com.smartbus.domain.exception.BadRequestException("Invalid password reset parameters");
        }

        return ResponseEntity.ok(java.util.Collections.singletonMap("message", "Password updated successfully"));
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        String xr = request.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) {
            return xr.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
