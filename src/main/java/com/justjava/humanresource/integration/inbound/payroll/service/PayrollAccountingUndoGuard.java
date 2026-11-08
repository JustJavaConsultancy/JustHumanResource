package com.justjava.humanresource.integration.inbound.payroll.service;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationOutboxEvent;
import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshot;
import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationOutboxRepository;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




@Service
@RequiredArgsConstructor
public class PayrollAccountingUndoGuard {
    private final IntegrationPayrollProperties properties;
    private final PayrollPeriodRepository periods;
    private final PayrollJournalSnapshotRepository snapshots;
    private final IntegrationOutboxRepository outbox;

    @Transactional
    public void assertCanReopen(Long periodId) {
        if (!properties.isEnabled() || properties.getCompanyId() == null) {
            return;
        }
        PayrollPeriod period = periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new IllegalStateException("Payroll period not found."));
        if (!properties.getCompanyId().equals(period.getCompanyId())) {
            return;
        }
        PayrollJournalSnapshot snapshot = snapshots
                .findByPayrollPeriodIdAndStatusNot(periodId, SnapshotStatus.VOID)
                .orElse(null);
        if (snapshot == null) {
            return;
        }
        if (snapshot.getStatus() != SnapshotStatus.PENDING_PUBLISH) {
            throw new IllegalStateException(
                    "This period's journal has been sent to accounting and cannot be reopened.");
        }

        IntegrationOutboxEvent event = outbox.findFirstByAggregateIdOrderByCreatedAtDesc(
                snapshot.getJournalReference()).orElse(null);
        if (event != null && event.getStatus() == OutboxStatus.PUBLISHING) {
            throw new IllegalStateException("Journal is being sent, try again shortly.");
        }
        if (event == null || event.getStatus() != OutboxStatus.PENDING
                || outbox.cancelPending(event.getEventId()) != 1) {
            throw new IllegalStateException(
                    "This period's accounting event is no longer pending and cannot be reopened.");
        }
        snapshot.setStatus(SnapshotStatus.VOID);
        snapshot.setActiveKey(null);
        snapshot.setVoidReason("Payroll period reopened before publication.");
        snapshot.setStatusChangedAt(LocalDateTime.now());
        snapshots.save(snapshot);
    }
}
