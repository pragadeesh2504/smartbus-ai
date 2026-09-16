package com.smartbus.application.port.in;

import com.smartbus.infrastructure.dto.LoginRequest;
import com.smartbus.infrastructure.dto.LoginResponse;
import com.smartbus.infrastructure.dto.RegisterRequest;
import com.smartbus.infrastructure.dto.RefreshTokenRequest;
import com.smartbus.infrastructure.dto.ResetPasswordRequest;

public interface AuthUseCase {
    LoginResponse login(LoginRequest loginRequest);
    LoginResponse loginWithGoogle(String idToken);
    LoginResponse loginWithGoogle(String idToken, String selectedRole);
    void register(RegisterRequest registerRequest);
    LoginResponse refreshToken(RefreshTokenRequest request);
    void forgotPassword(String email);
    boolean verifyOtp(String email, String otp);
    void resetPassword(String email, String otp, String newPassword);
    void resetPasswordWithToken(ResetPasswordRequest request);
}