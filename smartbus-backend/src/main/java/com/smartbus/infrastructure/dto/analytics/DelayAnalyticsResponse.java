package com.smartbus.infrastructure.dto.analytics;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelayAnalyticsResponse {
    private long totalDelayedTrips;
    private double averageDelayMinutes;
    private double maxDelayMinutes;
    private double delayThresholdMinutes;
    private List<Map<String, Object>> dailyDelayTrend; // [{date: "2026-09-01", delayedTrips: 2, avgDelay: 8.5}]
    private List<Map<String, Object>> affectedRoutes;  // [{routeName: "Route 1", delayCount: 5, avgDelay: 7.2}]
}
