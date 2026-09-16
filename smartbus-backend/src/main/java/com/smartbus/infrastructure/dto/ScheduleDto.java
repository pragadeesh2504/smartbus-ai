package com.smartbus.infrastructure.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleDto {
    private UUID id;
    private UUID routeId;
    private String routeName;
    private UUID busId;
    private String busNumber;
    private UUID driverId;
    private String driverName;
    private String departureTime;
    private String arrivalTime;
    private String daysOfWeek;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private String status;
}
