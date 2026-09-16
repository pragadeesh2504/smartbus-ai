package com.smartbus.application.service;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LocationData {
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double heading;
    private Double accuracy;
    private LocalDateTime timestamp;
    private Integer batteryLevel;
    private String deviceStatus; // ONLINE, OFFLINE, DEGRADED
}
