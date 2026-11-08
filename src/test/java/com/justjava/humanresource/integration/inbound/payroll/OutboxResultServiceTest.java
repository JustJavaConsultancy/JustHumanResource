package com.justjava.humanresource.integration.inbound.payroll;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationOutboxRepository;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.IntegrationAlertService;
import com.justjava.humanresource.integration.inbound.payroll.service.outbox.OutboxClaimService;
import com.justjava.humanresource.integration.inbound.payroll.service.outbox.OutboxResultService;

import com.justjava.humanresource.integration.inbound.payroll.dto.ClaimedPayrollEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;





@ExtendWith(MockitoExtension.class)
class OutboxResultServiceTest {
    @Mock
    private IntegrationOutboxRepository outbox;
    @Mock
    private IntegrationAlertService alerts;
    @Mock
    private PayrollJournalSnapshotRepository snapshots;

    @Test
    void confirmedPublishMarksOutboxAndSnapshotPublished() {
        UUID eventId = UUID.randomUUID();
        LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(1);
        ClaimedPayrollEvent claim = new ClaimedPayrollEvent(
                eventId, "PAY-2026-10-57", "events", "routing.key", "{}", "correlation",
                1, leaseUntil);
        when(outbox.markPublished(eq(eventId), eq(OutboxStatus.PUBLISHED), any(), eq(leaseUntil)))
                .thenReturn(1);
        when(snapshots.markPublished(eq(claim.aggregateId()), any())).thenReturn(1);

        new OutboxResultService(outbox, alerts, new IntegrationPayrollProperties(), snapshots)
                .published(claim);

        verify(snapshots).markPublished(eq(claim.aggregateId()), any());
        verify(alerts).resolvePublishFailure(eventId.toString());
    }
}
