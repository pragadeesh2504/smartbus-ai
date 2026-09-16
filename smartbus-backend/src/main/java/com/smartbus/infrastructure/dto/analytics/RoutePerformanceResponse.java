package com.smartbus.infrastructure.dto.analytics;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutePerformanceResponse {
    private UUID routeId;
    private String routeName;
    private long totalTrips;
    private long completedTrips;
    private long delayedTrips;
    private double onTimePercentage;
    private double averageDelayMinutes;
    private double averageTripDurationMinutes;
    private long deviationCount;
    private double gpsHealthPercentage;
    private String performanceStatus; // EXCELLENT, GOOD, ATTENTION, CRITICAL
}
