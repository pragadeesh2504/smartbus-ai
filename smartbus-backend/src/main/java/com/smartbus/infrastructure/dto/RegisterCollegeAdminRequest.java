package com.smartbus.infrastructure.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO for College Transport Head / Admin self-registration.
 * Atomically registers a new College and its initial ADMIN user account.
 * Note: Never accept a role field from this request; role is strictly assigned server-side as ADMIN.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterCollegeAdminRequest {

    @NotBlank(message = "College name is required")
    @Size(min = 2, max = 150, message = "College name must be between 2 and 150 characters")
    private String collegeName;

    @NotBlank(message = "College code is required")
    @Size(min = 2, max = 20, message = "College code must be between 2 and 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,20}$", message = "College code must contain only alphanumeric characters, dashes, and underscores")
    private String collegeCode;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @Pattern(regexp = "^(\\+?[0-9]{7,15})?$", message = "Please enter a valid phone number (7 to 15 digits, optional leading +)")
    private String phoneNumber;

    public void setCollegeCode(String collegeCode) {
        this.collegeCode = collegeCode != null ? collegeCode.trim().toUpperCase() : null;
    }

    public String getCollegeCode() {
        return this.collegeCode != null ? this.collegeCode.trim().toUpperCase() : null;
    }

    public String getCollegeName() {
        return this.collegeName != null ? this.collegeName.trim() : null;
    }

    public String getEmail() {
        return this.email != null ? this.email.trim().toLowerCase() : null;
    }

    public String getFirstName() {
        return this.firstName != null ? this.firstName.trim() : null;
    }

    public String getLastName() {
        return this.lastName != null ? this.lastName.trim() : null;
    }

    public String getPhoneNumber() {
        return this.phoneNumber != null ? this.phoneNumber.trim() : null;
    }
}