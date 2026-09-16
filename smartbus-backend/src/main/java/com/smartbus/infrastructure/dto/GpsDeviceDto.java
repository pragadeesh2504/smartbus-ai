package com.smartbus.infrastructure.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsDeviceDto {
    private UUID id;
    private String deviceId;
    private String imei;
    private String simIdentifier;
    private String provider;
    private String status;
    private LocalDateTime lastSeen;
    private Integer batteryLevel;
    private String firmwareVersion;
    private UUID assignedBusId;
    private String assignedBusNumber;
}
