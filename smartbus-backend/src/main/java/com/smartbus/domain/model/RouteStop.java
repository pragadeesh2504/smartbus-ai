package com.smartbus.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "route_stops")
@IdClass(RouteStopId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteStop {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Id
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stop_id", nullable = false)
    private Stop stop;

    @Column(name = "distance_from_start", nullable = false)
    private Double distanceFromStart;

    @Column(name = "duration_from_start_mins", nullable = false)
    private int durationFromStartMins;

    @Column(name = "expected_arrival_time")
    private java.time.LocalTime expectedArrivalTime;

    @Column(name = "expected_departure_time")
    private java.time.LocalTime expectedDepartureTime;
}
