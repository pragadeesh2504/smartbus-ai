package com.smartbus.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gps_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id")
    private College college;

    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Column(unique = true)
    private String imei;

    @Column(name = "sim_identifier")
    private String simIdentifier;

    private String provider;

    @Column(nullable = false)
    private String status = "NOT_CONFIGURED"; // NOT_CONFIGURED, ONLINE, OFFLINE, MAINTENANCE

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
