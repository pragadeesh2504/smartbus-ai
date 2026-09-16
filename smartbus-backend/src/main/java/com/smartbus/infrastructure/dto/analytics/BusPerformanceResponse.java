package com.smartbus.infrastructure.dto.analytics;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusPerformanceResponse {
    private UUID busId;
    private String busNumber;
    private String busCode;
    private long totalTrips;
    private long completedTrips;
    private long delayedTrips;
    private double onTimePercentage;
    private double averageDelayMinutes;
    private double averageTripDurationMinutes;
    private long deviationCount;
    private long gpsStaleCount;
    private double gpsHealthPercentage;
    private double totalDistanceKm;
    private String performanceStatus; // EXCELLENT, GOOD, ATTENTION, CRITICAL
}
