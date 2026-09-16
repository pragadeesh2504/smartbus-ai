package com.smartbus.infrastructure.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {
    private UUID id;
    private String userEmail;
    private String action;
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;
    private String entityName;
    private String entityId;
    private String oldValue;
    private String newValue;
}
