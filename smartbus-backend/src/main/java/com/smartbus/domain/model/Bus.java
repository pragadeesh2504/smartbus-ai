package com.smartbus.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "buses", uniqueConstraints = {
    @UniqueConstraint(name = "uq_buses_college_bus_number", columnNames = {"college_id", "bus_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id")
    private College college;

    @Column(name = "bus_number", nullable = false)
    private String busNumber;

    @Column(name = "registration_number", unique = true)
    private String registrationNumber;

    private String manufacturer;

    @Column(name = "manufacturing_year")
    private Integer manufacturingYear;

    @Column(name = "bus_type")
    private String busType;

    @Column(name = "bus_code", unique = true)
    private String busCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gps_device_id")
    private GpsDevice gpsDevice;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, MAINTENANCE, INACTIVE

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
