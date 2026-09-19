package com.smartbus.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id")
    private College college;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "license_number", nullable = false, unique = true)
    private String licenseNumber;

    @Column(name = "employee_id", unique = true)
    private String employeeId;

    @Column(name = "license_expiry")
    private java.time.LocalDate licenseExpiry;

    @Column(name = "emergency_contact")
    private String emergencyContact;

    @Column(name = "approval_status", nullable = false)
    @Builder.Default
    private String approvalStatus = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(name = "is_approved", nullable = false)
    @Builder.Default
    private boolean isApproved = false;

    @Column(name = "average_rating", nullable = false)
    @Builder.Default
    private BigDecimal averageRating = new BigDecimal("5.00");

    @Column(nullable = false)
    @Builder.Default
    private String status = "AVAILABLE"; // AVAILABLE, ASSIGNED, ON_TRIP, OFF_DUTY, SUSPENDED
}
