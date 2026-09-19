package com.smartbus.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;

    private String role;
    private String collegeCode;

    public GoogleLoginRequest(String idToken, String role) {
        this.idToken = idToken;
        this.role = role;
    }
}