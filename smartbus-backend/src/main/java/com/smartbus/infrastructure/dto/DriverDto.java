package com.smartbus.infrastructure.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverDto {
    private UUID id;
    private UUID userId;
    private String name;
    private String phone;
    private String email;
    private String licenseNumber;
    private LocalDate licenseExpiry;
    private String emergencyContact;
    private String status;
    private String approvalStatus;
    private String employeeId;
    private BigDecimal averageRating;
    private String password;
}
