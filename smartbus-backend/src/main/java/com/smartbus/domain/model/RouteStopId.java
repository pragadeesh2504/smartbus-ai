package com.smartbus.domain.model;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RouteStopId implements Serializable {
    private UUID route;
    private int sequenceNumber;
}
