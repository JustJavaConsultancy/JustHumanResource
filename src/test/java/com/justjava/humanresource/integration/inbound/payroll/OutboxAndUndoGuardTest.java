package com.justjava.humanresource.integration.inbound.payroll;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationOutboxEvent;
import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshot;
import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationOutboxRepository;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollAccountingUndoGuard;
import com.justjava.humanresource.integration.inbound.payroll.service.outbox.OutboxClaimService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.integration.inbound.payroll.dto.ClaimedPayrollEvent;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;





@ExtendWith(MockitoExtension.class)
class OutboxAndUndoGuardTest {
    @Mock
    private IntegrationOutboxRepository outboxRepository;
    @Mock
    private PayrollPeriodRepository periodRepository;
    @Mock
    private PayrollJournalSnapshotRepository snapshotRepository;

    private IntegrationPayrollProperties properties;

    @BeforeEach
    void setUp() {
        properties = new IntegrationPayrollProperties();
        properties.setEnabled(true);
        properties.setCompanyId(7L);
    }

    @Test
    void expiredPublishingLeaseIsReclaimedWithSameEventIdAndIncrementedAttempt() {
        UUID eventId = UUID.randomUUID();
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateId("PAY-2026-10-7");
        event.setExchangeName("events");
        event.setRoutingKey("justhr.payroll.finalised");
        event.setPayloadJson("{\"correlationId\":\"corr-1\"}");
        event.setStatus(OutboxStatus.PUBLISHING);
        event.setAttempts(1);
        event.setLeaseUntil(LocalDateTime.now().minusMinutes(1));
        when(outboxRepository.findDueForClaim(any(), anyInt())).thenReturn(List.of(event));

        OutboxClaimService service = new OutboxClaimService(
                outboxRepository, properties, new ObjectMapper());
        ClaimedPayrollEvent claim = service.claimDue().getFirst();

        assertThat(claim.eventId()).isEqualTo(eventId);
        assertThat(claim.correlationId()).isEqualTo("corr-1");
        assertThat(claim.attempts()).isEqualTo(2);
        assertThat(claim.leaseUntil()).isAfter(LocalDateTime.now());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
    }

    @Test
    void pendingEventCanBeCancelledAndSnapshotVoidedUnderPeriodLock() {
        PayrollPeriod period = period();
        PayrollJournalSnapshot snapshot = snapshot(SnapshotStatus.PENDING_PUBLISH);
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setStatus(OutboxStatus.PENDING);
        when(periodRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(period));
        when(snapshotRepository.findByPayrollPeriodIdAndStatusNot(4L, SnapshotStatus.VOID))
                .thenReturn(Optional.of(snapshot));
        when(outboxRepository.findFirstByAggregateIdOrderByCreatedAtDesc("PAY-2026-10-4"))
                .thenReturn(Optional.of(event));
        when(outboxRepository.cancelPending(event.getEventId())).thenReturn(1);

        new PayrollAccountingUndoGuard(
                properties, periodRepository, snapshotRepository, outboxRepository)
                .assertCanReopen(4L);

        assertThat(snapshot.getStatus()).isEqualTo(SnapshotStatus.VOID);
        assertThat(snapshot.getActiveKey()).isNull();
        verify(periodRepository).findByIdForUpdate(4L);
        verify(snapshotRepository).save(snapshot);
    }

    @Test
    void publishingEventRefusesReopenWithRetryMessage() {
        PayrollJournalSnapshot snapshot = snapshot(SnapshotStatus.PENDING_PUBLISH);
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setStatus(OutboxStatus.PUBLISHING);
        when(periodRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(period()));
        when(snapshotRepository.findByPayrollPeriodIdAndStatusNot(4L, SnapshotStatus.VOID))
                .thenReturn(Optional.of(snapshot));
        when(outboxRepository.findFirstByAggregateIdOrderByCreatedAtDesc("PAY-2026-10-4"))
                .thenReturn(Optional.of(event));

        assertThatThrownBy(() -> new PayrollAccountingUndoGuard(
                properties, periodRepository, snapshotRepository, outboxRepository)
                .assertCanReopen(4L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Journal is being sent, try again shortly.");
    }

    private static PayrollPeriod period() {
        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(7L);
        return period;
    }

    private static PayrollJournalSnapshot snapshot(SnapshotStatus status) {
        PayrollJournalSnapshot snapshot = new PayrollJournalSnapshot();
        snapshot.setPayrollPeriodId(4L);
        snapshot.setJournalReference("PAY-2026-10-4");
        snapshot.setActiveKey("PAY-2026-10-4");
        snapshot.setStatus(status);
        return snapshot;
    }
}
