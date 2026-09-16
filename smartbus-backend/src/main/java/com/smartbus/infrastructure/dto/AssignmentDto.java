package com.smartbus.infrastructure.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentDto {
    private UUID id;
    private UUID busId;
    private String busNumber;
    private UUID driverId;
    private String driverName;
    private UUID routeId;
    private String routeName;
    private UUID scheduleId;
    private String departureTime;
    private LocalDateTime assignedAt;
    private String status;
}
