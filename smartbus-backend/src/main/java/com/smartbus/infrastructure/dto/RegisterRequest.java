package com.smartbus.infrastructure.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Phone number is required")
    @jakarta.validation.constraints.Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Please enter a valid phone number (7 to 15 digits, optional leading +)")
    private String phoneNumber;

    @NotBlank(message = "Role is required")
    private String role; // STUDENT, DRIVER

    // Student fields
    private String collegeCode;
    private String studentId;
    private String department;
    private String batch;

    public void setCollegeCode(String collegeCode) {
        this.collegeCode = collegeCode != null ? collegeCode.trim().toUpperCase() : null;
    }

    public String getCollegeCode() {
        return this.collegeCode != null ? this.collegeCode.trim().toUpperCase() : null;
    }

    // Driver fields
    private String licenseNumber;
}
