package com.smartbus.application.service;

import com.smartbus.domain.model.AuditLog;
import com.smartbus.domain.model.User;
import com.smartbus.infrastructure.adapter.jpa.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logAction(User user, String action, String details, String ipAddress) {
        logAction(user, action, details, ipAddress, null, null, null, null);
    }

    @Transactional
    public void logAction(User user, String action, String details, String ipAddress,
                          String entityName, String entityId, String oldValue, String newValue) {
        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .details(details)
                .ipAddress(ipAddress != null ? ipAddress : "0.0.0.0")
                .entityName(entityName)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        auditLogRepository.save(auditLog);
    }
}
