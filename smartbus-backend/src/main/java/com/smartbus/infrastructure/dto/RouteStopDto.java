package com.smartbus.infrastructure.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteStopDto {
    private StopDto stop;
    private int sequenceNumber;
    private Double distanceFromStart;
    private int durationFromStartMins;
    private java.time.LocalTime expectedArrivalTime;
    private java.time.LocalTime expectedDepartureTime;
}
