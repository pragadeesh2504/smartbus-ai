package com.smartbus.infrastructure.dto.analytics;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverPerformanceResponse {
    private UUID driverId;
    private String driverName;
    private String employeeId;
    private long assignedTrips;
    private long completedTrips;
    private long delayedTrips;
    private double onTimePercentage;
    private double averageDelayMinutes;
    private double averageTripDurationMinutes;
    private long deviationCount;
    private double gpsHealthPercentage;
    private double totalActiveDrivingHours;
    private String performanceStatus; // EXCELLENT, GOOD, ATTENTION, CRITICAL
}
