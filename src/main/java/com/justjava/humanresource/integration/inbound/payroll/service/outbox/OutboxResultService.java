package com.justjava.humanresource.integration.inbound.payroll.service.outbox;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertType;
import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationOutboxRepository;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.IntegrationAlertService;

import com.justjava.humanresource.integration.inbound.payroll.dto.ClaimedPayrollEvent;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




@Service
@RequiredArgsConstructor
public class OutboxResultService {
    private final IntegrationOutboxRepository repository;
    private final IntegrationAlertService alerts;
    private final IntegrationPayrollProperties properties;
    private final PayrollJournalSnapshotRepository snapshots;

    @Transactional
    public void published(ClaimedPayrollEvent claim) {
        LocalDateTime publishedAt = LocalDateTime.now();
        int updated = repository.markPublished(
                claim.eventId(), OutboxStatus.PUBLISHED, publishedAt, claim.leaseUntil());
        if (updated == 1) {
            if (snapshots.markPublished(claim.aggregateId(), publishedAt) != 1) {
                throw new IllegalStateException("Published payroll event has no pending snapshot.");
            }
            alerts.resolvePublishFailure(claim.eventId().toString());
        }
    }

    @Transactional
    public void failed(ClaimedPayrollEvent claim, String safeReason) {
        boolean exhausted = claim.attempts() >= properties.getMaxAttempts();
        OutboxStatus status = exhausted ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        LocalDateTime nextAttempt = exhausted ? null : LocalDateTime.now().plusNanos(backoffNanos(claim.attempts()));
        int updated = repository.returnToPendingOrFail(
                claim.eventId(), status, nextAttempt, safeReason, claim.leaseUntil());
        if (updated == 1 && exhausted) {
            alerts.createIfAbsent(
                    IntegrationAlertType.PUBLISH_FAILED,
                    "PUBLISHFAILED:" + claim.eventId(),
                    claim.aggregateId(),
                    periodIdForReference(claim.aggregateId()),
                    "Journal event could not be published to the accounting broker.");
        }
    }

    private long backoffNanos(int attempt) {
        int exponent = Math.min(Math.max(attempt - 1, 0), 30);
        long uncapped = properties.getBackoffBaseMs() > (Long.MAX_VALUE >> exponent)
                ? Long.MAX_VALUE
                : properties.getBackoffBaseMs() << exponent;
        long capped = Math.min(uncapped, properties.getBackoffCapMs());
        double jitter = ThreadLocalRandom.current().nextDouble(0.75, 1.25);
        return Math.max(1L, (long) (capped * jitter)) * 1_000_000L;
    }

    private Long periodIdForReference(String reference) {
        try {
            int separator = reference.lastIndexOf('-');
            return Long.parseLong(reference.substring(separator + 1));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
