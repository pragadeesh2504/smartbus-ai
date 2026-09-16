package com.smartbus.infrastructure.dto;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaResponse {
    private UUID busId;
    private String busNumber;
    private String busCode;
    private UUID tripId;
    private UUID routeId;
    private String routeName;
    
    // Stop references
    private UUID currentStopId;
    private String currentStopName;
    private Integer currentStopSequence;
    
    private UUID nextStopId;
    private String nextStopName;
    private Integer nextStopSequence;
    
    // Target stop (if calculated specifically for student preferred stop)
    private UUID targetStopId;
    private String targetStopName;

    // Distance & Travel metrics
    private Double distanceMeters;
    private Long estimatedTravelSeconds;
    private Integer minutesRemaining;
    private String estimatedArrivalTime;

    // Status: ON_TIME, ARRIVING, ARRIVED, DELAYED, OFF_ROUTE, GPS_STALE, ETA_UNAVAILABLE
    private String status;
    private Double confidence; // 0.0 to 1.0
    private String lastGpsUpdate;

    // Route deviation & GPS status
    private Double routeDeviationMeters;
    private boolean offRoute;
    private boolean gpsStale;
    private String gpsStatus; // LIVE, GPS_STALE, UNAVAILABLE
    private String trackingSource; // GPS_DEVICE, DRIVER_PHONE, LAST_KNOWN
    
    // Schedule details
    private String scheduledDeparture;
    private Integer delayMinutes;

    // Upcoming Stops with dynamic ETAs from current GPS
    private java.util.List<UpcomingStopEta> upcomingStops;
    private RouteProgressInfo routeProgress;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpcomingStopEta {
        private UUID stopId;
        private String stopName;
        private Integer sequence;
        private Double latitude;
        private Double longitude;
        private Double distanceMeters;
        private Integer etaMinutes;
        private String estimatedArrivalTime;
        
        @com.fasterxml.jackson.annotation.JsonProperty("passed")
        private boolean passed;
        
        @com.fasterxml.jackson.annotation.JsonProperty("isNext")
        private boolean isNext;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteProgressInfo {
        private int passedStops;
        private int totalStops;
        private Integer nextStopSequence;
        private String nextStopName;
        private double progressPercent;
    }
}
