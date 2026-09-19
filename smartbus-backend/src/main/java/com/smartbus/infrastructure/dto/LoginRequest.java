package com.smartbus.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    private String role;
    private String collegeCode;

    @JsonSetter("collegeCode")
    public void setCollegeCode(String collegeCode) {
        this.collegeCode = collegeCode != null ? collegeCode.trim().toUpperCase() : null;
    }

    public String getCollegeCode() {
        return collegeCode != null ? collegeCode.trim().toUpperCase() : null;
    }

    @JsonSetter("email")
    public void setEmail(String email) {
        this.email = email != null ? email.trim().toLowerCase() : null;
    }

    public String getEmail() {
        return email != null ? email.trim().toLowerCase() : null;
    }
}