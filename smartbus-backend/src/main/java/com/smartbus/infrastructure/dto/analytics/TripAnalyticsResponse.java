package com.smartbus.infrastructure.dto.analytics;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripAnalyticsResponse {
    private UUID tripId;
    private UUID busId;
    private String busNumber;
    private String busCode;
    private UUID driverId;
    private String driverName;
    private UUID routeId;
    private String routeName;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double durationMinutes;
    private Double scheduledDurationMinutes;
    private Double distanceKm;
    private Double delayMinutes;
    private long stopCount;
    private long completedStopsCount;
    private boolean offRoute;
    private boolean gpsStale;
    private List<StopTimelineItem> stopTimeline;
    private List<Map<String, Object>> telemetryTrailSample; // Max 50 points sample for replay map

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StopTimelineItem {
        private UUID stopId;
        private String stopName;
        private int sequenceNumber;
        private LocalDateTime arrivalTime;
        private LocalDateTime departureTime;
        private boolean completed;
    }
}
