package com.smartbus.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveBusFleetDto {
    private UUID tripId;
    private UUID busId;
    private String busCode;
    private String busNumber;
    private UUID routeId;
    private String routeName;
    private UUID driverId;
    private String driverName;
    private String tripStatus; // IN_PROGRESS, PAUSED

    // Authoritative Telemetry
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double heading;
    private Double accuracy;
    private String trackingSource;
    private LocalDateTime lastUpdated;

    // ETA & Geofencing status
    private Integer etaMinutes;
    private String etaStatus; // ON_TIME, DELAYED, etc.
    private Double distanceMeters;
    private String nextStopName;
    private Integer nextStopSequence;
    private boolean offRoute;
    private Double routeDeviationMeters;
    private Integer delayMinutes;
    private boolean gpsStale;
    private String gpsStatus; // LIVE, GPS_STALE, UNAVAILABLE
    private java.util.List<EtaResponse.UpcomingStopEta> upcomingStops;
    private EtaResponse.RouteProgressInfo routeProgress;

    // Route Geometry & Waypoints
    private Double startLatitude;
    private Double startLongitude;
    private Double endLatitude;
    private Double endLongitude;
    private String polyline;
    private java.util.List<com.smartbus.infrastructure.dto.RouteStopDto> stops;
}
