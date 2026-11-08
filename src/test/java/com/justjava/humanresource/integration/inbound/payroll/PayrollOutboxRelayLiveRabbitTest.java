package com.justjava.humanresource.integration.inbound.payroll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.HumanResourceApplication;
import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationOutboxEvent;
import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshot;
import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationOutboxRepository;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.outbox.OutboxClaimService;
import com.justjava.humanresource.integration.inbound.payroll.service.outbox.OutboxResultService;
import com.justjava.humanresource.integration.inbound.payroll.workflow.relay.PayrollOutboxRelay;
import com.justjava.humanresource.payroll.entity.PayrollJournalBatch;
import com.justjava.humanresource.payroll.entity.PayrollJournalEntry;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollJournalBatchRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.utils.PayrollLockEmailService;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType.CORRELATED;

@EnabledIfSystemProperty(named = "live.rabbit", matches = "true")
@SpringBootTest(
        classes = HumanResourceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.scheduling.enabled=false",
                "integration.payroll-accounting.enabled=true",
                "integration.payroll-accounting.company-id=970000000000",
                "spring.jpa.hibernate.ddl-auto=update",
                "logging.level.root=OFF",
                "logging.level.org.hibernate.orm.connections.pooling=OFF"
        })
@ActiveProfiles("proof-local")
@ContextConfiguration(initializers = com.justjava.humanresource.payroll.workflow.PayrollProofDatabaseGuard.class)
class PayrollOutboxRelayLiveRabbitTest {
    private static final AtomicLong COMPANY_IDS = new AtomicLong(9_700_000_000L);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 31);

    @Autowired
    private IntegrationPayrollProperties properties;
    @Autowired
    private PayrollPeriodRepository periods;
    @Autowired
    private PayrollJournalBatchRepository batches;
    @Autowired
    private PayrollJournalEntryRepository entries;
    @Autowired
    private PayrollJournalSnapshotRepository snapshots;
    @Autowired
    private IntegrationOutboxRepository outbox;
    @Autowired
    private PayrollOutboxRelay relay;
    @Autowired
    private OutboxClaimService claims;
    @Autowired
    private OutboxResultService results;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ConnectionFactory connectionFactory;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FlowableTaskService flowableTaskService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @MockitoSpyBean
    private IntegrationOutboxRepository outboxSpy;

    @MockitoBean(name = "validatePeriodDelegate")
    private JavaDelegate validatePeriodDelegate;
    @MockitoBean(name = "generateReconciliationDelegate")
    private JavaDelegate generateReconciliationDelegate;
    @MockitoBean(name = "generateJournalDelegate")
    private JavaDelegate generateJournalDelegate;
    @MockitoBean(name = "generatePayslipsDelegate")
    private JavaDelegate generatePayslipsDelegate;
    @MockitoBean(name = "validateBatchPeriodDelegate")
    private JavaDelegate validateBatchPeriodDelegate;
    @MockitoBean(name = "loadEmployeesForPayrollDelegate")
    private JavaDelegate loadEmployeesForPayrollDelegate;
    @MockitoBean(name = "dispatchEmployeePayrollDelegate")
    private JavaDelegate dispatchEmployeePayrollDelegate;
    @MockitoBean(name = "markBatchCompleteDelegate")
    private JavaDelegate markBatchCompleteDelegate;
    @MockitoBean(name = "initiateBankTransferDelegate")
    private JavaDelegate initiateBankTransferDelegate;
    @MockitoBean(name = "processPaymentsDelegate")
    private JavaDelegate processPaymentsDelegate;
    @MockitoBean(name = "assumePaymentSuccessDelegate")
    private JavaDelegate assumePaymentSuccessDelegate;
    @MockitoBean(name = "clearPayrollSuspenseDelegate")
    private JavaDelegate clearPayrollSuspenseDelegate;
    @MockitoBean(name = "verifyPayrollSuspenseDelegate")
    private JavaDelegate verifyPayrollSuspenseDelegate;
    @MockitoBean
    private PayrollLockEmailService payrollLockEmailService;

    private final String queueName = "integration.payroll.accounting.livetest.justhr-" + UUID.randomUUID();
    private final Binding binding = new Binding(
            queueName, DestinationType.QUEUE, "justjava.business.events",
            "justhr.payroll.finalised", null);
    private RabbitAdmin rabbitAdmin;
    private Long companyId;
    private Long periodId;
    private String reference;
    private String closeProcessInstanceId;
    private String nextPayrollProcessBusinessKey;
    private boolean preserveFinalData;

    @Test
    void verifiesRealBrokerRelayAndLeavesOnePendingCloseForScheduledApplication() throws Exception {
        verifyEffectivePublisherSettings();
        rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.declareQueue(new Queue(queueName, false, false, false));
        rabbitAdmin.declareBinding(binding);
        properties.setEnabled(true);
        properties.setCompanyId(nextCompanyId());
        properties.setMaxAttempts(3);
        properties.setBackoffBaseMs(1000);
        properties.setBackoffCapMs(60_000);
        properties.setConfirmTimeoutMs(5000);

        verifyFinanceCloseHappyPath();
        verifyUnroutableAndRetry();
        verifyUnavailableBrokerAndRecovery();
        verifyTwoRelayInstancesClaimOnce();
        verifyConfirmedPublishCanBeSafelyRetriedAfterMarkFailure();
        createFinalPendingFinanceClose();
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "live.rabbit.inspect", matches = "true")
    void inspectHandoffRecord() {
        String handoffReference = System.getProperty("live.reference");
        assertThat(handoffReference).isNotBlank();
        IntegrationOutboxEvent event = outbox.findFirstByAggregateIdOrderByCreatedAtDesc(handoffReference)
                .orElseThrow();
        PayrollJournalSnapshot snapshot = snapshots
                .findByJournalReferenceAndStatusNot(handoffReference, SnapshotStatus.VOID)
                .orElseThrow();
        System.out.println("LIVE_HANDOFF reference=" + handoffReference
                + " outbox=" + event.getStatus()
                + " attempts=" + event.getAttempts()
                + " snapshot=" + snapshot.getStatus());
    }

    private void verifyEffectivePublisherSettings() {
        assertThat(connectionFactory).isInstanceOf(CachingConnectionFactory.class);
        CachingConnectionFactory caching = (CachingConnectionFactory) connectionFactory;
        assertThat(caching.isPublisherConfirms()).isTrue();
        assertThat(caching.isSimplePublisherConfirms()).isFalse();
        assertThat(caching.isPublisherReturns()).isTrue();
        Message probe = new Message(new byte[0]);
        assertThat(rabbitTemplate.isMandatoryFor(probe)).isTrue();
        System.out.println("LIVE_CHECK_1 confirmType=CORRELATED publisherReturns=true mandatory=true");
    }

    private void verifyFinanceCloseHappyPath() throws Exception {
        startCloseProcessAtFinanceTask();
        createBalancedJournal();
        approveClose();
        assertThat(jdbc.queryForObject(
                "select count(*) from payroll_journal_snapshot where payroll_period_id=? and status='PENDING_PUBLISH'",
                Integer.class, periodId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from integration_outbox where aggregate_id=? and status='PENDING'",
                Integer.class, reference)).isEqualTo(1);

        relay.publishDueEvents();
        Message message = receiveRequiredMessage();
        JsonNode body = objectMapper.readTree(message.getBody());
        assertThat(body.path("eventType").asText()).isEqualTo("payroll.finalised");
        assertThat(body.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(body.path("sourceApplication").asText()).isEqualTo("JUSTHR");
        assertThat(body.path("aggregateId").asText()).isEqualTo(reference);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(body.path("eventId").asText());
        assertThat(message.getMessageProperties().getCorrelationId()).isNotBlank();
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getReceivedDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(body.has("lines")).isFalse();
        assertThat(body.path("payload").has("lines")).isFalse();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).doesNotContain(
                "employeeName", "personalName", "accountNumber", "PRIVATE_PERSONAL_MARKER");

        var stored = jdbc.queryForMap("""
                select o.status as outbox_status, o.event_id, o.published_at,
                       s.status as snapshot_status, s.status_changed_at
                from integration_outbox o
                join payroll_journal_snapshot s on s.journal_reference=o.aggregate_id
                where o.aggregate_id=?
                """, reference);
        assertThat(stored.get("outbox_status").toString()).isEqualTo("PUBLISHED");
        assertThat(stored.get("snapshot_status").toString()).isEqualTo("PUBLISHED");
        assertThat(((Timestamp) stored.get("published_at")).toLocalDateTime())
                .isEqualTo(((Timestamp) stored.get("status_changed_at")).toLocalDateTime());
        assertThat(stored.get("event_id").toString()).isEqualTo(message.getMessageProperties().getMessageId());
        System.out.println("LIVE_CHECK_2 Finance close -> delivered; eventType=payroll.finalised "
                + "eventVersion=1 sourceApplication=JUSTHR aggregateIdMatches=true "
                + "messageIdMatchesEventId=true contentType=application/json persistent=true "
                + "correlationIdPresent=true journalLinesAbsent=true personalDataAbsent=true "
                + "outbox=PUBLISHED snapshot=PUBLISHED");
        cleanupCurrentPeriod();
    }

    private void verifyUnroutableAndRetry() throws Exception {
        TestPeriod test = createPendingFixture();
        rabbitAdmin.removeBinding(binding);
        relay.publishDueEvents();
        IntegrationOutboxEvent returned = outbox.findById(test.eventId()).orElseThrow();
        assertThat(returned.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(returned.getAttempts()).isEqualTo(1);
        assertThat(returned.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(returned.getLastError()).isEqualTo("Broker returned unroutable event.");
        assertThat(returned.getLastError()).doesNotContain(test.reference(), "PRIVATE", "payload", "account");
        assertThat(rabbitTemplate.receive(queueName, 500)).isNull();

        rabbitAdmin.declareBinding(binding);
        makeDue(test.eventId());
        relay.publishDueEvents();
        Message delivered = receiveRequiredMessage();
        assertThat(delivered.getMessageProperties().getMessageId()).isEqualTo(test.eventId().toString());
        assertThat(rabbitTemplate.receive(queueName, 500)).isNull();
        System.out.println("LIVE_CHECK_3 unroutable=PENDING attempts=1 retryScheduled=true "
                + "lastErrorSanitized=true restoredBindingDeliveryCount=1");
        cleanupCurrentPeriod();
    }

    private void verifyUnavailableBrokerAndRecovery() throws Exception {
        TestPeriod test = createPendingFixture();
        CachingConnectionFactory original = (CachingConnectionFactory) rabbitTemplate.getConnectionFactory();
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        CachingConnectionFactory unavailable = new CachingConnectionFactory("127.0.0.1");
        unavailable.setPort(closedPort);
        unavailable.setPublisherConfirmType(CORRELATED);
        unavailable.setPublisherReturns(true);
        try {
            rabbitTemplate.setConnectionFactory(unavailable);
            relay.publishDueEvents();
            IntegrationOutboxEvent failed = outbox.findById(test.eventId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(failed.getAttempts()).isEqualTo(1);
            assertThat(failed.getNextAttemptAt()).isAfter(LocalDateTime.now());
            assertThat(applicationContext.isActive()).isTrue();
        } finally {
            rabbitTemplate.setConnectionFactory(original);
            unavailable.destroy();
        }
        makeDue(test.eventId());
        relay.publishDueEvents();
        Message delivered = receiveRequiredMessage();
        assertThat(delivered.getMessageProperties().getMessageId()).isEqualTo(test.eventId().toString());
        assertThat(rabbitTemplate.receive(queueName, 500)).isNull();
        System.out.println("LIVE_CHECK_4 closedLocalBroker=PENDING attempts=1 backoffScheduled=true "
                + "applicationActive=true restoredBrokerDeliveryCount=1");
        cleanupCurrentPeriod();
    }

    private void verifyTwoRelayInstancesClaimOnce() throws Exception {
        TestPeriod test = createPendingFixture();
        PayrollOutboxRelay second = new PayrollOutboxRelay(claims, results, rabbitTemplate, properties);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstRun = executor.submit(() -> {
                start.await();
                relay.publishDueEvents();
                return null;
            });
            var secondRun = executor.submit(() -> {
                start.await();
                second.publishDueEvents();
                return null;
            });
            start.countDown();
            firstRun.get(15, TimeUnit.SECONDS);
            secondRun.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        Message delivered = receiveRequiredMessage();
        assertThat(delivered.getMessageProperties().getMessageId()).isEqualTo(test.eventId().toString());
        assertThat(rabbitTemplate.receive(queueName, 750)).isNull();
        assertThat(outbox.findById(test.eventId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        System.out.println("LIVE_CHECK_5 concurrentRelayInstances=2 deliveredCount=1 "
                + "outbox=PUBLISHED");
        cleanupCurrentPeriod();
    }

    private void verifyConfirmedPublishCanBeSafelyRetriedAfterMarkFailure() throws Exception {
        TestPeriod test = createPendingFixture();
        doThrow(new IllegalStateException("Injected post-confirm mark failure"))
                .when(outboxSpy).markPublished(
                        eq(test.eventId()), eq(OutboxStatus.PUBLISHED), any(LocalDateTime.class), any(LocalDateTime.class));
        assertThatThrownBy(relay::publishDueEvents)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Injected post-confirm mark failure");
        Message first = receiveRequiredMessage();
        assertThat(first.getMessageProperties().getMessageId()).isEqualTo(test.eventId().toString());
        assertThat(outbox.findById(test.eventId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHING);

        reset(outboxSpy);
        jdbc.update("update integration_outbox set lease_until=? where event_id=?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), test.eventId());
        relay.publishDueEvents();
        Message retry = receiveRequiredMessage();
        assertThat(retry.getMessageProperties().getMessageId()).isEqualTo(first.getMessageProperties().getMessageId());
        assertThat(rabbitTemplate.receive(queueName, 750)).isNull();
        assertThat(outbox.findById(test.eventId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        System.out.println("LIVE_CHECK_6 confirmedThenMarkFailed=true leaseExpiredRetry=true "
                + "firstAndRetryMessageIdsEqual=true duplicateExpected=true outbox=PUBLISHED");
        cleanupCurrentPeriod();
    }

    private void createFinalPendingFinanceClose() {
        startCloseProcessAtFinanceTask();
        createBalancedJournal();
        approveClose();
        assertThat(outbox.findFirstByAggregateIdOrderByCreatedAtDesc(reference).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PENDING);
        assertThat(snapshots.findByJournalReferenceAndStatusNot(reference, SnapshotStatus.VOID))
                .isPresent();
        preserveFinalData = true;
        System.out.println("LIVE_CHECK_8 preparedReference=" + reference
                + " companyId=" + companyId + " outbox=PENDING; start scheduled application to publish");
    }

    private TestPeriod createPendingFixture() {
        companyId = nextCompanyId();
        properties.setCompanyId(companyId);
        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setPeriodStart(PERIOD_START);
        period.setPeriodEnd(PERIOD_END);
        period.setPlannedPeriodEnd(PERIOD_END);
        period.setStatus(PayrollPeriodStatus.CLOSED);
        periodId = periods.saveAndFlush(period).getId();
        reference = journalReference();

        PayrollJournalSnapshot snapshot = new PayrollJournalSnapshot();
        snapshot.setJournalReference(reference);
        snapshot.setActiveKey(reference);
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
        snapshot.setStatus(SnapshotStatus.PENDING_PUBLISH);
        snapshot.setStatusChangedAt(LocalDateTime.now());
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshots.saveAndFlush(snapshot);

        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setEventId(eventId);
        event.setEventType("payroll.finalised");
        event.setEventVersion(1);
        event.setAggregateId(reference);
        event.setExchangeName(properties.getExchange());
        event.setRoutingKey(properties.getRoutingKey());
        event.setPayloadJson("""
                {"eventId":"%s","eventType":"payroll.finalised","eventVersion":1,
                 "sourceApplication":"JUSTHR","sourceOrganisationId":"%s",
                 "occurredAt":"%s","correlationId":"%s","aggregateId":"%s",
                 "payload":{"journalReference":"%s","revision":"ORIGINAL",
                 "payrollPeriodId":"%s","periodStart":"2026-10-01","periodEnd":"2026-10-31",
                 "currency":"NGN","lineCount":2,"totalDebit":100.00,"totalCredit":100.00,
                 "contentHash":"%s"}}
                """.formatted(eventId, companyId, Instant.now(), correlationId, reference,
                reference, periodId, "a".repeat(64)));
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        event.setCreatedAt(LocalDateTime.now());
        outbox.saveAndFlush(event);
        return new TestPeriod(periodId, companyId, reference, eventId);
    }

    private void makeDue(UUID eventId) {
        jdbc.update("update integration_outbox set next_attempt_at=? where event_id=?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), eventId);
    }

    private Message receiveRequiredMessage() {
        Message message = rabbitTemplate.receive(queueName, 10_000);
        assertThat(message).as("message routed to the isolated live-test queue").isNotNull();
        return message;
    }

    private Long nextCompanyId() {
        long id = COMPANY_IDS.incrementAndGet();
        properties.setCompanyId(id);
        return id;
    }

    private void startCloseProcessAtFinanceTask() {
        companyId = nextCompanyId();
        properties.setCompanyId(companyId);
        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setPeriodStart(PERIOD_START);
        period.setPeriodEnd(PERIOD_END);
        period.setPlannedPeriodEnd(PERIOD_END);
        period.setStatus(PayrollPeriodStatus.OPEN);
        periodId = periods.saveAndFlush(period).getId();
        reference = journalReference();
        nextPayrollProcessBusinessKey = "BATCH_PERIOD_" + periodId;
        doAnswer(invocation -> {
            org.flowable.engine.delegate.DelegateExecution execution = invocation.getArgument(0);
            execution.setVariable("employeeIds", List.of());
            return null;
        }).when(loadEmployeesForPayrollDelegate).execute(any());

        Map<String, Object> variables = Map.of(
                "companyId", companyId, "periodId", periodId,
                "periodStart", PERIOD_START, "periodEnd", PERIOD_END, "actualPeriodEnd", PERIOD_END);
        ProcessInstance process = runtimeService.startProcessInstanceByKey(
                "payrollPeriodCloseProcess", "STAGE3_LIVE_" + UUID.randomUUID(), variables);
        closeProcessInstanceId = process.getProcessInstanceId();
        Task financeTask = taskService.createTaskQuery()
                .processInstanceId(closeProcessInstanceId)
                .taskDefinitionKey("financeOfficer")
                .singleResult();
        assertThat(financeTask).as("production Finance approval task").isNotNull();
    }

    private void createBalancedJournal() {
        PayrollJournalBatch batch = new PayrollJournalBatch();
        batch.setCompanyId(companyId);
        batch.setPayrollPeriodId(periodId);
        batch.setPeriodStart(PERIOD_START);
        batch.setPeriodEnd(PERIOD_END);
        batch.setTotalDebit(new BigDecimal("100.00"));
        batch.setTotalCredit(new BigDecimal("100.00"));
        batch.setBalanced(true);
        batch = batches.saveAndFlush(batch);
        saveJournalLine(batch, "5000", "Stage3 test expense", new BigDecimal("100.00"), BigDecimal.ZERO, "DEBIT");
        saveJournalLine(batch, "2100", "Stage3 test payable", BigDecimal.ZERO, new BigDecimal("100.00"), "CREDIT");
    }

    private void saveJournalLine(
            PayrollJournalBatch batch, String accountCode, String accountName,
            BigDecimal debit, BigDecimal credit, String lineType) {
        PayrollJournalEntry entry = new PayrollJournalEntry();
        entry.setCompanyId(companyId);
        entry.setPayrollPeriodId(periodId);
        entry.setJournalBatch(batch);
        entry.setPeriodStart(PERIOD_START);
        entry.setPeriodEnd(PERIOD_END);
        entry.setAccountCode(accountCode);
        entry.setAccountName(accountName);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setDescription("Fixed live test fixture");
        entry.setLineType(lineType);
        entry.setExported(false);
        entries.save(entry);
    }

    private void approveClose() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "stage3-live-test-token", now, now.plusSeconds(300),
                Map.of("sub", "stage3-live-test", "groups", List.of("financialofficers")));
        DefaultOidcUser principal = new DefaultOidcUser(List.of(), idToken, "sub");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "test-client"));
        String taskId = taskService.createTaskQuery()
                .processInstanceId(closeProcessInstanceId)
                .taskDefinitionKey("financeOfficer")
                .singleResult()
                .getId();
        flowableTaskService.completeTask(taskId, Map.of("approved", true));
    }

    private String journalReference() {
        return "PAY-2026-10-" + periodId;
    }

    private void cleanupCurrentPeriod() {
        if (closeProcessInstanceId != null
                && runtimeService.createProcessInstanceQuery().processInstanceId(closeProcessInstanceId).singleResult() != null) {
            runtimeService.deleteProcessInstance(closeProcessInstanceId, "Stage 3 live-test cleanup");
        }
        if (closeProcessInstanceId != null
                && historyService.createHistoricProcessInstanceQuery().processInstanceId(closeProcessInstanceId).singleResult() != null) {
            historyService.deleteHistoricProcessInstance(closeProcessInstanceId);
        }
        if (nextPayrollProcessBusinessKey != null) {
            runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(nextPayrollProcessBusinessKey).list()
                    .forEach(instance -> runtimeService.deleteProcessInstance(instance.getId(), "Stage 3 live-test cleanup"));
            historyService.createHistoricProcessInstanceQuery().processInstanceBusinessKey(nextPayrollProcessBusinessKey).list()
                    .forEach(instance -> historyService.deleteHistoricProcessInstance(instance.getId()));
        }
        if (periodId != null) {
            jdbc.update("""
                    delete from payroll_journal_snapshot_line
                    where snapshot_id in (select id from payroll_journal_snapshot where payroll_period_id=?)
                    """, periodId);
            jdbc.update("delete from integration_outbox where aggregate_id=?", reference);
            jdbc.update("delete from integration_alert where payroll_period_id=?", periodId);
            jdbc.update("delete from payroll_journal_snapshot where payroll_period_id=?", periodId);
            entries.deleteAll(entries.findByCompanyIdAndPayrollPeriodId(companyId, periodId));
            batches.findByCompanyIdAndPayrollPeriodId(companyId, periodId).ifPresent(batches::delete);
            periods.deleteAll(periods.findByCompanyIdOrderByPeriodStartDesc(companyId));
        }
        periodId = null;
        companyId = null;
        reference = null;
        closeProcessInstanceId = null;
        nextPayrollProcessBusinessKey = null;
    }

    @AfterEach
    void cleanupUnlessDataIsBeingHandedOff() {
        SecurityContextHolder.clearContext();
        if (!preserveFinalData) {
            cleanupCurrentPeriod();
            if (rabbitAdmin != null) {
                rabbitAdmin.removeBinding(binding);
                rabbitAdmin.deleteQueue(queueName);
            }
        }
        properties.setEnabled(false);
        properties.setCompanyId(null);
    }

    private record TestPeriod(Long periodId, Long companyId, String reference, UUID eventId) {
    }
}
