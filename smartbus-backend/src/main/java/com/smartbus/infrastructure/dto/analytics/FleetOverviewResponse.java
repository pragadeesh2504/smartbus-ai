package com.smartbus.infrastructure.dto.analytics;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FleetOverviewResponse {
    private long totalBuses;
    private long activeTrips;
    private long totalPeriodTrips;
    private long completedTrips;
    private long delayedTrips;
    private double onTimePercentage;
    private double averageTripDurationMinutes;
    private double averageDelayMinutes;
    private long routeDeviationCount;
    private double gpsHealthPercentage;
    private long healthyGpsUpdates;
    private long staleGpsEvents;

    // Detailed fleet metrics
    private long inactiveBuses;
    private long liveBuses;
    private long staleBuses;

    // Driver metrics
    private long totalDrivers;
    private long approvedDrivers;
    private long activeDrivers;

    // Student metrics
    private long totalStudents;
    private long activeStudents;

    // Trip timeframe metrics
    private long tripsToday;
    private long tripsThisWeek;
    private long tripsThisMonth;
    private long cancelledTrips;
    private long inProgressTrips;

    // Operational averages
    private double averageDistanceKm;
    private double averageSpeedKmh;
}
