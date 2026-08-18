package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.entity.PayrollAuditLog;
import com.justjava.humanresource.payroll.repositories.PayrollAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PayrollAuditService {

    private final PayrollAuditLogRepository repository;

    /**
     * Records a payroll audit event.
     *
     * @param entityType   the entity being acted on (e.g. "PayrollRun", "PayrollPeriod")
     * @param entityId     the PK of that entity
     * @param action       the business action (e.g. "AMENDMENT_CREATED", "PAYROLL_POSTED")
     * @param details      free-text details / amendment reason — may be null
     */
    public void log(
            String entityType,
            Long entityId,
            String action,
            String details) {

        try {
            PayrollAuditLog entry = new PayrollAuditLog();
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setAction(action);
            entry.setPerformedBy(getCurrentUser());
            entry.setPerformedRole(getCurrentRole());
            entry.setPerformedAt(LocalDateTime.now());
            entry.setDetails(details);

            repository.save(entry);
        } catch (Exception ignored) {
            // Audit failures must never break the business flow.
        }
    }

    // ── SecurityContext helpers ───────────────────────────────────────────────

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "system";
    }

    /**
     * Returns the first granted authority as the "role", or "SYSTEM" when no
     * authentication context is available (e.g. background Flowable threads).
     */
    private String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return "SYSTEM";
        }
        return auth.getAuthorities().iterator().next().getAuthority();
    }
}
