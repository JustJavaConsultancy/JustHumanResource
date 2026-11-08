package com.justjava.humanresource.integration.inbound.payroll.service;

import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationAlert;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertType;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationAlertRepository;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




@Service
@RequiredArgsConstructor
public class IntegrationAlertService {
    private final IntegrationAlertRepository repository;

    @Transactional
    public void createIfAbsent(
            IntegrationAlertType type,
            String dedupeKey,
            String journalReference,
            Long periodId,
            String message) {
        repository.insertIfAbsent(
                type.name(),
                limit(dedupeKey, 160),
                limit(journalReference, 80),
                periodId,
                limit(message, 500),
                LocalDateTime.now());
    }

    @Transactional
    public void resolveRejected(String journalReference) {
        repository.findByDedupeKey("REJECTED:" + journalReference).ifPresent(alert -> {
            if (alert.getStatus() == IntegrationAlertStatus.OPEN) {
                alert.setStatus(IntegrationAlertStatus.RESOLVED);
                alert.setResolvedAt(LocalDateTime.now());
                repository.save(alert);
            }
        });
    }

    @Transactional
    public void resolvePublishFailure(String eventId) {
        repository.resolveOpen("PUBLISHFAILED:" + eventId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<IntegrationAlert> openAlerts(Long periodId) {
        return repository.findByPayrollPeriodIdAndStatusOrderByCreatedAtDesc(
                periodId, IntegrationAlertStatus.OPEN);
    }

    @Transactional
    public void acknowledge(Long id, String actor) {
        IntegrationAlert alert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Integration alert not found."));
        if (alert.getType() != IntegrationAlertType.PAYROLL_CHANGED_AFTER_SEND
                || alert.getStatus() != IntegrationAlertStatus.OPEN) {
            throw new IllegalStateException("Only open payroll-change alerts can be acknowledged.");
        }
        alert.setStatus(IntegrationAlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(limit(actor, 160));
        alert.setAcknowledgedAt(LocalDateTime.now());
        repository.save(alert);
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }
}
