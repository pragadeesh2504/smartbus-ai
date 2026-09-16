package com.smartbus.infrastructure.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripDto {
    private UUID id;
    private UUID scheduleId;
    private UUID busId;
    private String busNumber;
    private UUID driverId;
    private String driverName;
    private UUID routeId;
    private String routeName;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime actualDeparture;
    private LocalDateTime actualArrival;
}
