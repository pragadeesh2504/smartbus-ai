package com.smartbus.infrastructure.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusDto {
    private UUID id;
    private String busNumber;
    private String registrationNumber;
    private String manufacturer;
    private Integer manufacturingYear;
    private String busType;
    private String busCode;
    private String gpsDeviceId;
    private String gpsDeviceStatus;
    private String model;
    private int capacity;
    private String status;
    private Double currentLatitude;
    private Double currentLongitude;
    private LocalDateTime lastUpdated;
}
