package com.smartbus.infrastructure.dto.analytics;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviationAnalyticsResponse {
    private long totalDeviations;
    private long activeDeviations;
    private double deviationThresholdMeters;
    private List<Map<String, Object>> deviationsByRoute; // [{routeId, routeName, count}]
    private List<Map<String, Object>> deviationsByBus;   // [{busId, busNumber, count}]
    private List<Map<String, Object>> dailyDeviationTrend; // [{date, count}]
}
