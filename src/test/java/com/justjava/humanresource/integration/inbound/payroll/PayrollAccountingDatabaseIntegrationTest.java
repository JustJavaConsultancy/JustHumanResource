package com.justjava.humanresource.integration.inbound.payroll;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationAlert;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationOutboxEvent;
import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshot;
import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshotLine;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertType;
import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationAlertRepository;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationOutboxRepository;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.IntegrationAlertService;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollAccountingChangeDetector;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollAccountingPanelService;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollAccountingUndoGuard;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollJournalSnapshotService;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollJournalStatusService;
import com.justjava.humanresource.integration.inbound.payroll.service.outbox.OutboxClaimService;
import com.justjava.humanresource.integration.inbound.payroll.service.outbox.OutboxResultService;
import com.justjava.humanresource.integration.inbound.payroll.workflow.relay.PayrollOutboxRelay;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.HumanResourceApplication;
import com.justjava.humanresource.integration.inbound.payroll.dto.PayrollJournalStatusRequest;
import com.justjava.humanresource.integration.inbound.payroll.dto.ClaimedPayrollEvent;
import com.justjava.humanresource.payroll.entity.PayrollJournalBatch;
import com.justjava.humanresource.payroll.entity.PayrollJournalEntry;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.PayrollController;
import com.justjava.humanresource.payroll.repositories.PayrollJournalBatchRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.service.PayrollJournalService;
import com.justjava.humanresource.payroll.workflow.delegates.ReverseLockDelegate;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;





@SpringBootTest(
        classes = HumanResourceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.scheduling.enabled=false",
                "integration.payroll-accounting.enabled=true",
                "integration.payroll-accounting.company-id=990000000001",
                "spring.rabbitmq.host=127.0.0.1",
                "spring.rabbitmq.port=5672",
                "spring.rabbitmq.username=integration-test",
                "spring.rabbitmq.password=integration-test",
                "spring.rabbitmq.dynamic=false",
                "spring.rabbitmq.ssl.enabled=false"
        })
@AutoConfigureMockMvc
@ContextConfiguration(initializers = com.justjava.humanresource.payroll.workflow.PayrollProofDatabaseGuard.class)
class PayrollAccountingDatabaseIntegrationTest {
    private static final AtomicInteger COMPANY_SEQUENCE = new AtomicInteger();
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 31);

    @Autowired private IntegrationPayrollProperties properties;
    @Autowired private PayrollPeriodRepository periods;
    @Autowired private PayrollJournalBatchRepository batches;
    @Autowired private PayrollJournalEntryRepository entries;
    @Autowired private PayrollJournalSnapshotRepository snapshots;
    @Autowired private IntegrationOutboxRepository outbox;
    @Autowired private IntegrationAlertRepository alerts;
    @Autowired private IntegrationAlertService alertService;
    @Autowired private PayrollJournalStatusService statusService;
    @Autowired private PayrollAccountingUndoGuard undoGuard;
    @Autowired private PayrollAccountingChangeDetector changeDetector;
    @Autowired private PayrollAccountingPanelService panelService;
    @Autowired private PayrollJournalService journalService;
    @Autowired private PayrollJournalSnapshotService snapshotService;
    @Autowired private OutboxClaimService claimService;
    @Autowired private OutboxResultService resultService;
    @Autowired private PayrollOutboxRelay relay;
    @Autowired private ReverseLockDelegate reverseLockDelegate;
    @Autowired private PayrollController payrollController;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mockMvc;

    @MockitoBean private RabbitTemplate rabbitTemplate;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoSpyBean private IntegrationOutboxRepository outboxSpy;
    @MockitoSpyBean private PayrollJournalSnapshotRepository snapshotsSpy;

    private long companyId;
    private Long periodId;
    private String reference;

    @BeforeEach
    void createIsolatedCompanyScope() {
        companyId = 8_800_000_000L + COMPANY_SEQUENCE.incrementAndGet();
        properties.setEnabled(true);
        properties.setCompanyId(companyId);
        properties.setConfirmTimeoutMs(50);
        properties.setMaxAttempts(3);
        properties.setBackoffBaseMs(1000);
        properties.setBackoffCapMs(60_000);
    }

    @AfterEach
    void removeOnlyThisTestData() {
        if (periodId != null) {
            jdbc.update("""
                    delete from payroll_journal_snapshot_line
                    where snapshot_id in (
                        select id from payroll_journal_snapshot where payroll_period_id = ?
                    )
                    """, periodId);
            jdbc.update("delete from integration_outbox where aggregate_id = ?", reference);
            jdbc.update("delete from integration_alert where payroll_period_id = ?", periodId);
            jdbc.update("delete from payroll_journal_snapshot where payroll_period_id = ?", periodId);
        }
        entries.deleteAll(entries.findByCompanyIdAndPayrollPeriodId(companyId, periodId));
        batches.findByCompanyIdAndPayrollPeriodId(companyId, periodId).ifPresent(batches::delete);
        periods.deleteAll(periods.findByCompanyIdOrderByPeriodStartDesc(companyId));
        properties.setCompanyId(990000000001L);
    }

    @Test
    void publishedSnapshotStatusCallbackCreatesResolvesAndDeduplicatesRejectedAlert() {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        PayrollJournalSnapshot snapshot = createSnapshot(period, SnapshotStatus.PUBLISHED, true);

        statusService.record(reference, new PayrollJournalStatusRequest(
                "REJECTED", null, "INVALID", "Rejected safely"));
        statusService.record(reference, new PayrollJournalStatusRequest(
                "REJECTED", null, "INVALID", "Repeat"));
        assertThat(snapshots.findById(snapshot.getId()).orElseThrow().getStatus())
                .isEqualTo(SnapshotStatus.REJECTED);
        assertThat(alerts.findByDedupeKey("REJECTED:" + reference)).isPresent();

        statusService.record(reference, new PayrollJournalStatusRequest(
                "POSTED", 45L, null, null));
        statusService.record(reference, new PayrollJournalStatusRequest(
                "POSTED", 45L, null, null));
        assertThat(snapshots.findById(snapshot.getId()).orElseThrow().getStatus())
                .isEqualTo(SnapshotStatus.POSTED);
        assertThat(alerts.findByDedupeKey("REJECTED:" + reference).orElseThrow().getStatus())
                .isEqualTo(IntegrationAlertStatus.RESOLVED);
        assertThatThrownBy(() -> statusService.record(reference,
                new PayrollJournalStatusRequest("REJECTED", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void statusCallbackRejectsUnpublishedAndInvalidTransitions() throws Exception {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PENDING_PUBLISH, true);

        assertThatThrownBy(() -> statusService.record(reference,
                new PayrollJournalStatusRequest("POSTED", 1L, null, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> statusService.record(reference,
                new PayrollJournalStatusRequest("REJECTED", 1L, null, null)))
                .isInstanceOf(IllegalStateException.class);

        mockDecoderTokens();
        mockMvc.perform(post("/api/integrations/payroll-journals/" + reference + "/status")
                        .header("Authorization", "Bearer write-ok")
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"POSTED\",\"amsJournalId\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void readAndStatusEndpointsEnforceFullTokenClaimAndScopeMatrix() throws Exception {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PUBLISHED, true);
        mockDecoderTokens();

        mockMvc.perform(get("/api/integrations/payroll-journals/" + reference))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/integrations/payroll-journals/" + reference)
                        .header("Authorization", "Bearer wrong-audience"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/integrations/payroll-journals/" + reference)
                        .header("Authorization", "Bearer wrong-azp"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/integrations/payroll-journals/" + reference)
                        .header("Authorization", "Bearer missing-read-scope"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/integrations/payroll-journals/" + reference)
                        .header("Authorization", "Bearer read-ok"))
                .andExpect(status().isOk());

        String callback = "{\"status\":\"POSTED\",\"amsJournalId\":45}";
        mockMvc.perform(post("/api/integrations/payroll-journals/" + reference + "/status")
                        .contentType(APPLICATION_JSON).content(callback))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/integrations/payroll-journals/" + reference + "/status")
                        .header("Authorization", "Bearer wrong-audience")
                        .contentType(APPLICATION_JSON).content(callback))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/integrations/payroll-journals/" + reference + "/status")
                        .header("Authorization", "Bearer wrong-azp")
                        .contentType(APPLICATION_JSON).content(callback))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/integrations/payroll-journals/" + reference + "/status")
                        .header("Authorization", "Bearer missing-write-scope")
                        .contentType(APPLICATION_JSON).content(callback))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/integrations/payroll-journals/" + reference + "/status")
                        .header("Authorization", "Bearer write-ok")
                        .contentType(APPLICATION_JSON).content(callback))
                .andExpect(status().isOk());
    }

    @Test
    void readEndpointReturnsNotFoundForUnknownAndVoidedReferences() throws Exception {
        mockDecoderTokens();
        mockMvc.perform(get("/api/integrations/payroll-journals/unknown-reference")
                        .header("Authorization", "Bearer read-ok"))
                .andExpect(status().isNotFound());

        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        PayrollJournalSnapshot voided = createSnapshot(period, SnapshotStatus.VOID, false);
        voided.setActiveKey(null);
        snapshots.saveAndFlush(voided);
        mockMvc.perform(get("/api/integrations/payroll-journals/" + reference)
                        .header("Authorization", "Bearer read-ok"))
                .andExpect(status().isNotFound());
    }

    @Test
    void relayClaimUsesSkipLockedSoTwoInstancesClaimAndPublishOnlyOnce() throws Exception {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PENDING_PUBLISH, true);
        AtomicInteger sends = new AtomicInteger();
        doAnswer(invocation -> {
                    sends.incrementAndGet();
                    CorrelationData correlation = invocation.getArgument(3);
                    correlation.getFuture().complete(new CorrelationData.Confirm(true, ""));
                    return null;
                }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> { start.await(); relay.publishDueEvents(); return null; });
            var second = executor.submit(() -> { start.await(); relay.publishDueEvents(); return null; });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(sends).hasValue(1);
        assertThat(outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(snapshots.findByJournalReferenceAndStatusNot(reference, SnapshotStatus.VOID)
                .orElseThrow().getStatus()).isEqualTo(SnapshotStatus.PUBLISHED);
    }

    @Test
    void relayNackReturnAndTimeoutBackOffAndMaxAttemptsAlertThenResolveOnSuccess() throws Exception {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PENDING_PUBLISH, true);
        doAnswer(invocation -> {
                    CorrelationData correlation = invocation.getArgument(3);
                    correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
                    return null;
                }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        relay.publishDueEvents();
        IntegrationOutboxEvent row = outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow();
        LocalDateTime firstRetry = row.getNextAttemptAt();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(firstRetry).isAfter(LocalDateTime.now());

        row.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        row.setAttempts(1);
        outbox.saveAndFlush(row);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(
                    invocation.getArgument(2), 312, "NO_ROUTE",
                    invocation.getArgument(0), invocation.getArgument(1)));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, ""));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        relay.publishDueEvents();
        row = outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getNextAttemptAt()).isAfter(firstRetry);

        properties.setConfirmTimeoutMs(1);
        row.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        row.setAttempts(2);
        outbox.saveAndFlush(row);
        reset(rabbitTemplate);
        doNothing().when(rabbitTemplate).send(
                anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        properties.setMaxAttempts(3);
        relay.publishDueEvents();
        row = outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(alerts.findByDedupeKey("PUBLISHFAILED:" + row.getEventId())).isPresent();

        row.setStatus(OutboxStatus.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        outbox.saveAndFlush(row);
        doAnswer(invocation -> {
                    CorrelationData correlation = invocation.getArgument(3);
                    correlation.getFuture().complete(new CorrelationData.Confirm(true, ""));
                    return null;
                }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        relay.publishDueEvents();
        assertThat(alerts.findByDedupeKey("PUBLISHFAILED:" + row.getEventId()).orElseThrow().getStatus())
                .isEqualTo(IntegrationAlertStatus.RESOLVED);
    }

    @Test
    void outboxAndSnapshotPublicationTransitionIsAtomicOnSuccessAndEitherFailure() {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PENDING_PUBLISH, true);
        ClaimedPayrollEvent claim = claimService.claimDue().getFirst();
        resultService.published(claim);
        assertThat(outbox.findById(claim.eventId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(snapshots.findByJournalReferenceAndStatusNot(reference, SnapshotStatus.VOID)
                .orElseThrow().getStatus()).isEqualTo(SnapshotStatus.PUBLISHED);

        outbox.findById(claim.eventId()).ifPresent(row -> {
            row.setStatus(OutboxStatus.PUBLISHING);
            row.setLeaseUntil(LocalDateTime.now().plusMinutes(1));
            outbox.saveAndFlush(row);
        });
        var secondClaim = new ClaimedPayrollEvent(
                claim.eventId(), reference, claim.exchange(), claim.routingKey(), claim.payload(),
                claim.correlationId(), claim.attempts(), outbox.findById(claim.eventId()).orElseThrow().getLeaseUntil());
        snapshots.findByJournalReferenceAndStatusNot(reference, SnapshotStatus.VOID).ifPresent(snapshot -> {
            snapshot.setStatus(SnapshotStatus.PENDING_PUBLISH);
            snapshots.saveAndFlush(snapshot);
        });

        doThrow(new IllegalStateException("Injected snapshot update failure"))
                .when(snapshotsSpy).markPublished(eq(reference), any());
        assertThatThrownBy(() -> resultService.published(secondClaim))
                .isInstanceOf(IllegalStateException.class);
        assertThat(outbox.findById(claim.eventId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
        assertThat(snapshots.findByJournalReferenceAndStatusNot(reference, SnapshotStatus.VOID)
                .orElseThrow().getStatus()).isEqualTo(SnapshotStatus.PENDING_PUBLISH);

        reset(snapshotsSpy);
        doThrow(new IllegalStateException("Injected outbox update failure"))
                .when(outboxSpy).markPublished(eq(claim.eventId()), eq(OutboxStatus.PUBLISHED), any(), any());
        assertThatThrownBy(() -> resultService.published(secondClaim))
                .isInstanceOf(IllegalStateException.class);
        assertThat(outbox.findById(claim.eventId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
        assertThat(snapshots.findByJournalReferenceAndStatusNot(reference, SnapshotStatus.VOID)
                .orElseThrow().getStatus()).isEqualTo(SnapshotStatus.PENDING_PUBLISH);
    }

    @Test
    void crashAfterConfirmedPublishAndBeforeMarkResendsSameEventId() {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PENDING_PUBLISH, true);
        UUID eventId = outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow().getEventId();
        AtomicReference<String> firstMessageId = new AtomicReference<>();
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            firstMessageId.set(message.getMessageProperties().getMessageId());
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, ""));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        doThrow(new IllegalStateException("Injected mark failure"))
                .when(outboxSpy).markPublished(eq(eventId), eq(OutboxStatus.PUBLISHED), any(), any());

        assertThatThrownBy(relay::publishDueEvents).isInstanceOf(IllegalStateException.class);
        assertThat(outbox.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
        assertThat(snapshots.findByJournalReferenceAndStatusNot(reference, SnapshotStatus.VOID)
                .orElseThrow().getStatus()).isEqualTo(SnapshotStatus.PENDING_PUBLISH);

        IntegrationOutboxEvent event = outbox.findById(eventId).orElseThrow();
        event.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        outbox.saveAndFlush(event);
        reset(outboxSpy);
        AtomicReference<String> resentMessageId = new AtomicReference<>();
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            resentMessageId.set(message.getMessageProperties().getMessageId());
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, ""));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.publishDueEvents();

        assertThat(firstMessageId.get()).isEqualTo(eventId.toString());
        assertThat(resentMessageId.get()).isEqualTo(firstMessageId.get());
        assertThat(outbox.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(claimService.claimDue()).isEmpty();
    }

    @Test
    void pendingReopenVoidsCancelsAllowsRecloseAndPublishingOrderRefuses() {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        PayrollJournalBatch batch = createBalancedBatch(period);
        createJournalLines(period, batch, BigDecimal.ZERO);
        PayrollJournalSnapshot original = createSnapshot(period, SnapshotStatus.PENDING_PUBLISH, true);

        undoGuard.assertCanReopen(periodId);
        assertThat(snapshots.findById(original.getId()).orElseThrow().getStatus()).isEqualTo(SnapshotStatus.VOID);
        assertThat(outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.CANCELLED);
        assertThat(claimService.claimDue()).isEmpty();

        period.setStatus(PayrollPeriodStatus.CLOSED);
        periods.saveAndFlush(period);
        snapshotService.createForClosedPeriod(periodId, companyId);
        assertThat(snapshots.findByPayrollPeriodIdAndStatusNot(periodId, SnapshotStatus.VOID))
                .isPresent();
        assertThat(periods.findById(periodId).orElseThrow().getCompanyId()).isEqualTo(companyId);
        assertThat(snapshots.findAll().stream().filter(s -> s.getPayrollPeriodId().equals(periodId))
                .map(PayrollJournalSnapshot::getJournalReference).distinct()).containsExactly(reference);

        ClaimedPayrollEvent claimed = claimService.claimDue().getFirst();
        assertThat(claimed.aggregateId()).isEqualTo(reference);
        assertThatThrownBy(() -> undoGuard.assertCanReopen(periodId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Journal is being sent, try again shortly.");
    }

    @Test
    void relayFirstPublishesAndMakesReopenRefuse() throws Exception {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PENDING_PUBLISH, true);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, ""));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.publishDueEvents();

        assertThat(outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(snapshots.findByJournalReferenceAndStatusNot(reference, SnapshotStatus.VOID)
                .orElseThrow().getStatus()).isEqualTo(SnapshotStatus.PUBLISHED);
        assertThatThrownBy(() -> undoGuard.assertCanReopen(periodId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This period's journal has been sent to accounting and cannot be reopened.");
    }

    @Test
    void relayAndStatusCallbackLogsNeverContainAmountsLinesPayloadOrPersonalData() {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PENDING_PUBLISH, true);
        IntegrationOutboxEvent event = outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow();
        String privatePayload = "{\"amount\":\"817.32\",\"accountCode\":\"ACCOUNT_MARKER\","
                + "\"employeeName\":\"PERSON_MARKER\"}";
        event.setPayloadJson(privatePayload);
        outbox.saveAndFlush(event);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, ""));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level previousLevel = logger.getLevel();
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            relay.publishDueEvents();
            statusService.record(reference, new PayrollJournalStatusRequest(
                    "REJECTED", null, "STATUS_REASON_MARKER",
                    "PERSONAL_NAME_MARKER ACCOUNT_NUMBER_MARKER"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        String emitted = appender.list.stream()
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(emitted).doesNotContain(
                "817.32", "ACCOUNT_MARKER", "PERSON_MARKER", privatePayload,
                "STATUS_REASON_MARKER", "PERSONAL_NAME_MARKER", "ACCOUNT_NUMBER_MARKER");
    }

    @Test
    void publishedPostedRejectedAndPublishingSnapshotsBlockReverseLockDelegate() {
        for (SnapshotStatus snapshotStatus : List.of(
                SnapshotStatus.PUBLISHED, SnapshotStatus.POSTED, SnapshotStatus.REJECTED)) {
            PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
            PayrollJournalSnapshot savedSnapshot = createSnapshot(period, snapshotStatus, true);
            assertThat(snapshots.findById(savedSnapshot.getId()).orElseThrow().getStatus())
                    .isEqualTo(snapshotStatus);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getCompanyId()).isEqualTo(companyId);
            assertThat(snapshots.findByPayrollPeriodIdAndStatusNot(periodId, SnapshotStatus.VOID))
                    .isPresent();
            assertThatThrownBy(() -> undoGuard.assertCanReopen(periodId))
                    .isInstanceOf(IllegalStateException.class);
            DelegateExecution execution = mock(DelegateExecution.class);
            when(execution.getVariable("periodId")).thenReturn(periodId);
            when(execution.getVariable("companyId")).thenReturn(companyId);
            assertThatThrownBy(() -> reverseLockDelegate.execute(execution))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("This period's journal has been sent to accounting and cannot be reopened.");
            cleanupCurrentRowsAndPeriod();
            properties.setCompanyId(companyId);
        }
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PENDING_PUBLISH, true);
        IntegrationOutboxEvent event = outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow();
        event.setStatus(OutboxStatus.PUBLISHING);
        event.setLeaseUntil(LocalDateTime.now().plusMinutes(1));
        outbox.saveAndFlush(event);
        assertThatThrownBy(() -> undoGuard.assertCanReopen(periodId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Journal is being sent, try again shortly.");
    }

    @Test
    void changeDetectorCreatesOneSafeAlertForClosedPeriodAndDoesNotBlock() {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.PUBLISHED, false);
        changeDetector.detect(77L, companyId, PERIOD_START, PERIOD_END);
        changeDetector.detect(77L, companyId, PERIOD_START, PERIOD_END);
        List<IntegrationAlert> found = alerts.findByPayrollPeriodIdAndStatusOrderByCreatedAtDesc(
                periodId, IntegrationAlertStatus.OPEN);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getDedupeKey()).isEqualTo("CHANGED:77");
        assertThat(found.getFirst().getMessage()).doesNotContain("77");
    }

    @Test
    void csvRefusesConfiguredCompanyWithoutChangingExportFlagsAndWorksWhenDisabled() {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        PayrollJournalBatch batch = createBalancedBatch(period);
        createJournalLines(period, batch, BigDecimal.ZERO);

        assertThatThrownBy(() -> journalService.exportPeriodCsv(companyId, periodId, "integration-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CSV export is disabled");
        assertThat(batches.findById(batch.getId()).orElseThrow().isExported()).isFalse();
        assertThat(entries.findByCompanyIdAndPayrollPeriodId(companyId, periodId))
                .allMatch(entry -> !entry.isExported());

        properties.setEnabled(false);
        byte[] csv = journalService.exportPeriodCsv(companyId, periodId, "integration-test");
        assertThat(csv).isNotEmpty();
        assertThat(batches.findById(batch.getId()).orElseThrow().isExported()).isTrue();
        assertThat(entries.findByCompanyIdAndPayrollPeriodId(companyId, periodId))
                .allMatch(PayrollJournalEntry::isExported);
    }

    @Test
    void panelShowsStatusesAndSafeAlertsAndAcknowledgementEnforcesFinanceRoleInService() {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        createSnapshot(period, SnapshotStatus.REJECTED, false);
        alertService.createIfAbsent(IntegrationAlertType.PAYROLL_CHANGED_AFTER_SEND,
                "CHANGED:999", reference, periodId, "Safe change alert.");
        assertThat(panelService.panel().snapshots()).extracting(
                PayrollAccountingPanelService.SnapshotView::status).contains("Rejected");
        assertThat(panelService.panel().alerts()).hasSize(1);

        Long alertId = panelService.panel().alerts().getFirst().getId();
        setGroupAuthentication(List.of("employees"));
        assertThatThrownBy(() -> payrollController.acknowledgeAccountingAlert(
                alertId, mock(RedirectAttributes.class)))
                .isInstanceOf(AccessDeniedException.class);
        setGroupAuthentication(List.of("financialofficers"));
        payrollController.acknowledgeAccountingAlert(alertId, mock(RedirectAttributes.class));
        SecurityContextHolder.clearContext();
        assertThat(alerts.findById(alertId).orElseThrow().getStatus())
                .isEqualTo(IntegrationAlertStatus.ACKNOWLEDGED);
    }

    @Test
    void snapshotLinesAreImmutableToUpdatesAndCannotBeDeletedViaEntityLifecycle() {
        PayrollPeriod period = createPeriod(PayrollPeriodStatus.CLOSED);
        PayrollJournalSnapshot snapshot = createSnapshot(period, SnapshotStatus.PUBLISHED, false);
        PayrollJournalSnapshotLine line = createLine(snapshot, 1, "SAFECODE", "Safe account",
                "DEBIT", "100.00", "0.00");
        Long lineId = line.getId();

        new TransactionTemplate(transactionManager).executeWithoutResult(transactionStatus -> {
            PayrollJournalSnapshotLine managed = entityManager.find(PayrollJournalSnapshotLine.class, lineId);
            managed.setAccountName("Changed account");
            entityManager.flush();
        });
        assertThat(jdbc.queryForObject(
                "select account_name from payroll_journal_snapshot_line where id=?", String.class, lineId))
                .isEqualTo("Safe account");
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transactionStatus -> {
            entityManager.remove(entityManager.find(PayrollJournalSnapshotLine.class, lineId));
            entityManager.flush();
        })).isInstanceOf(IllegalStateException.class);
    }

    private void setGroupAuthentication(List<String> groups) {
        Instant now = Instant.now();
        OidcIdToken token = new OidcIdToken("database-test-token", now, now.plusSeconds(600),
                Map.of("sub", "database-test-user", "groups", groups));
        DefaultOidcUser principal = new DefaultOidcUser(List.of(), token, "sub");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "test-client"));
    }

    private void mockDecoderTokens() {
        when(jwtDecoder.decode("wrong-audience")).thenThrow(new BadJwtException("invalid token"));
        when(jwtDecoder.decode("wrong-azp")).thenReturn(token("wrong-azp", "humanresource-api",
                "other-client", "payroll-journal:read payroll-status:write"));
        when(jwtDecoder.decode("missing-read-scope")).thenReturn(token("missing-read-scope", "humanresource-api",
                "jjeip-humanresource", "payroll-status:write"));
        when(jwtDecoder.decode("missing-write-scope")).thenReturn(token("missing-write-scope", "humanresource-api",
                "jjeip-humanresource", "payroll-journal:read"));
        when(jwtDecoder.decode("read-ok")).thenReturn(token("read-ok", "humanresource-api",
                "jjeip-humanresource", "payroll-journal:read"));
        when(jwtDecoder.decode("write-ok")).thenReturn(token("write-ok", "humanresource-api",
                "jjeip-humanresource", "payroll-status:write"));
    }

    private Jwt token(String value, String audience, String azp, String scope) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(value)
                .header("alg", "none")
                .issuer("https://test-issuer.invalid")
                .audience(List.of(audience))
                .claim("azp", azp)
                .claim("scope", scope)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
    }

    private PayrollPeriod createPeriod(PayrollPeriodStatus status) {
        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setPeriodStart(PERIOD_START);
        period.setPeriodEnd(PERIOD_END);
        period.setPlannedPeriodEnd(PERIOD_END);
        period.setStatus(status);
        periodId = periods.saveAndFlush(period).getId();
        reference = "PAY-2026-10-" + periodId;
        return period;
    }

    private PayrollJournalSnapshot createSnapshot(
            PayrollPeriod period, SnapshotStatus status, boolean createOutbox) {
        PayrollJournalSnapshot snapshot = new PayrollJournalSnapshot();
        snapshot.setJournalReference(reference);
        snapshot.setActiveKey(status == SnapshotStatus.VOID ? null : reference);
        snapshot.setCompanyId(companyId);
        snapshot.setPayrollPeriodId(periodId);
        snapshot.setPeriodStart(PERIOD_START);
        snapshot.setPeriodEnd(PERIOD_END);
        snapshot.setCurrency(IntegrationPayrollProperties.CURRENCY);
        snapshot.setRevision("ORIGINAL");
        snapshot.setHeaderNarration("Payroll 2026-10");
        snapshot.setTotalDebit(new BigDecimal("100.00"));
        snapshot.setTotalCredit(new BigDecimal("100.00"));
        snapshot.setLineCount(2);
        snapshot.setContentHash("a".repeat(64));
        snapshot.setStatus(status);
        snapshot.setStatusChangedAt(LocalDateTime.now());
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot = snapshots.saveAndFlush(snapshot);
        if (createOutbox) {
            createOutbox(status == SnapshotStatus.PENDING_PUBLISH ? OutboxStatus.PENDING : OutboxStatus.PUBLISHED);
        }
        return snapshot;
    }

    private IntegrationOutboxEvent createOutbox(OutboxStatus status) {
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("payroll.finalised");
        event.setEventVersion(1);
        event.setAggregateId(reference);
        event.setRoutingKey(properties.getRoutingKey());
        event.setExchangeName(properties.getExchange());
        event.setPayloadJson("{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"correlationId\":\"test-correlation\"}");
        event.setStatus(status);
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        event.setCreatedAt(LocalDateTime.now());
        return outbox.saveAndFlush(event);
    }

    private PayrollJournalBatch createBalancedBatch(PayrollPeriod period) {
        PayrollJournalBatch batch = new PayrollJournalBatch();
        batch.setCompanyId(companyId);
        batch.setPayrollPeriodId(periodId);
        batch.setPeriodStart(PERIOD_START);
        batch.setPeriodEnd(PERIOD_END);
        batch.setTotalDebit(new BigDecimal("100.00"));
        batch.setTotalCredit(new BigDecimal("100.00"));
        batch.setBalanced(true);
        return batches.saveAndFlush(batch);
    }

    private void createJournalLines(PayrollPeriod period, PayrollJournalBatch batch, BigDecimal amount) {
        createEntry(batch, "5000", "Test expense", new BigDecimal("100.00").add(amount),
                BigDecimal.ZERO, "DEBIT");
        createEntry(batch, "2100", "Test payable", BigDecimal.ZERO,
                new BigDecimal("100.00").add(amount), "CREDIT");
    }

    private void createEntry(
            PayrollJournalBatch batch, String code, String name,
            BigDecimal debit, BigDecimal credit, String type) {
        PayrollJournalEntry entry = new PayrollJournalEntry();
        entry.setCompanyId(companyId);
        entry.setPayrollPeriodId(periodId);
        entry.setJournalBatch(batch);
        entry.setPeriodStart(PERIOD_START);
        entry.setPeriodEnd(PERIOD_END);
        entry.setAccountCode(code);
        entry.setAccountName(name);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setDescription("Fixed fixture");
        entry.setLineType(type);
        entries.save(entry);
    }

    private PayrollJournalSnapshotLine createLine(
            PayrollJournalSnapshot snapshot, int number, String code, String name,
            String type, String debit, String credit) {
        PayrollJournalSnapshotLine line = new PayrollJournalSnapshotLine();
        line.setSnapshot(snapshot);
        line.setLineNumber(number);
        line.setAccountCode(code);
        line.setAccountName(name);
        line.setLineType(type);
        line.setDebitAmount(new BigDecimal(debit));
        line.setCreditAmount(new BigDecimal(credit));
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<PayrollJournalSnapshotLine>) connection -> {
            var statement = connection.prepareStatement("""
                    insert into payroll_journal_snapshot_line
                    (snapshot_id, line_number, account_code, account_name, line_type, debit_amount, credit_amount)
                    values (?, ?, ?, ?, ?, ?, ?) returning id
                    """);
            statement.setLong(1, snapshot.getId());
            statement.setInt(2, number);
            statement.setString(3, code);
            statement.setString(4, name);
            statement.setString(5, type);
            statement.setBigDecimal(6, new BigDecimal(debit));
            statement.setBigDecimal(7, new BigDecimal(credit));
            var result = statement.executeQuery();
            result.next();
            line.setId(result.getLong(1));
            return line;
        });
    }

    private void cleanupCurrentRowsAndPeriod() {
        removeOnlyThisTestData();
        periodId = null;
        reference = null;
    }
}
