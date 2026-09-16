package com.smartbus.infrastructure.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StopDto {
    private UUID id;
    private String stopName;
    private Double latitude;
    private Double longitude;
}
