package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.entity.PayrollAuditLog;
import com.justjava.humanresource.payroll.repositories.PayrollAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PayrollAuditService {

    private final PayrollAuditLogRepository repository;

    public void log(
            String entityType,
            Long entityId,
            String action,
            String performedBy,
            String performedRole,
            String details) {

        PayrollAuditLog log = new PayrollAuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setPerformedBy("HR");
        log.setPerformedRole(performedRole);
        log.setPerformedAt(LocalDateTime.now());
        log.setDetails(details);

        repository.save(log);
    }
}
