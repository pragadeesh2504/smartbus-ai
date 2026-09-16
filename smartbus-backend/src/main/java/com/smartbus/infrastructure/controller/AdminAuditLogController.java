package com.smartbus.infrastructure.controller;

import com.smartbus.domain.model.AuditLog;
import com.smartbus.infrastructure.adapter.jpa.AuditLogRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.AuditLogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) String search) {

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        List<AuditLog> allLogs = auditLogRepository.findAll();

        if (search != null && !search.trim().isEmpty()) {
            String lowerSearch = search.toLowerCase();
            allLogs = allLogs.stream()
                    .filter(log -> log.getAction().toLowerCase().contains(lowerSearch) ||
                            log.getDetails().toLowerCase().contains(lowerSearch) ||
                            (log.getUser() != null && log.getUser().getEmail().toLowerCase().contains(lowerSearch)))
                    .collect(Collectors.toList());
        }

        // Apply in-memory sort if custom sorting is requested on nested/complex fields
        if ("createdAt".equals(sortBy)) {
            allLogs.sort((a, b) -> "DESC".equalsIgnoreCase(direction) ?
                    b.getCreatedAt().compareTo(a.getCreatedAt()) :
                    a.getCreatedAt().compareTo(b.getCreatedAt()));
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allLogs.size());

        List<AuditLogDto> content = new ArrayList<>();
        if (start <= allLogs.size()) {
            content = allLogs.subList(start, end).stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        }

        Page<AuditLogDto> pageResult = new PageImpl<>(content, pageable, allLogs.size());
        return ResponseEntity.ok(ApiResponse.success("Audit logs loaded successfully", pageResult));
    }

    private AuditLogDto mapToDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .userEmail(log.getUser() != null ? log.getUser().getEmail() : "System")
                .action(log.getAction())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .entityName(log.getEntityName())
                .entityId(log.getEntityId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .build();
    }
}
