package com.smartbus.infrastructure.dto.analytics;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsHealthResponse {
    private long totalTelemetryUpdates;
    private long healthyUpdates;
    private long staleEvents;
    private double gpsHealthPercentage;
    private String fleetStatus; // HEALTHY, DEGRADED, STALE
    private List<Map<String, Object>> deviceHealthSummary; // [{busNumber, status, lastSeen, isStale}]
}
