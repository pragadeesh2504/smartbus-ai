package com.smartbus.infrastructure.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollegeDto {
    private UUID id;
    private String name;
    private String collegeCode;
    private String status;
    private String logoUrl;
    private String contactEmail;
    private Long studentCount;
    private Long driverCount;
    private Long busCount;
    private Long routeCount;
    private Long activeTripCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
