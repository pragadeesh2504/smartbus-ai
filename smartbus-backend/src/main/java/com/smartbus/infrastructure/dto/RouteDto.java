package com.smartbus.infrastructure.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteDto {
    private UUID id;
    private String routeName;
    private String startPoint;
    private String endPoint;
    private Double distance;
    private int estimatedDurationMins;
    private String status;
    private Double startLatitude;
    private Double startLongitude;
    private Double endLatitude;
    private Double endLongitude;
    private String polyline;
    private Integer version;
    private java.time.LocalDateTime updatedAt;
    private List<RouteStopDto> stops;
}
