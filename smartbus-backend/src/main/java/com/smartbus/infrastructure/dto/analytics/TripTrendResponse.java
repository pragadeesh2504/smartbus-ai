package com.smartbus.infrastructure.dto.analytics;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripTrendResponse {
    private String date;
    private long totalTrips;
    private long completedTrips;
    private long delayedTrips;
    private long cancelledTrips;
    private double onTimePercentage;
}
