package com.smartbus.infrastructure.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformMetricsDto {
    private long totalColleges;
    private long activeColleges;
    private long disabledColleges;
    private long totalStudents;
    private long totalDrivers;
    private long totalBuses;
}
