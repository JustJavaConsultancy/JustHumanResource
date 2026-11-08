package com.justjava.humanresource.payroll.workflow;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollJournalSnapshotService;

import com.justjava.humanresource.HumanResourceApplication;
import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationOutboxEvent;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationOutboxRepository;
import com.justjava.humanresource.integration.inbound.payroll.workflow.relay.PayrollOutboxRelay;
import com.justjava.humanresource.payroll.entity.PayrollJournalBatch;
import com.justjava.humanresource.payroll.entity.PayrollJournalEntry;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollJournalBatchRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.utils.PayrollLockEmailService;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.HistoryService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ContextConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;





/**
 * Verifies that a failure after the production close service writes still rolls back
 * the payroll and Flowable changes together. The local development database requires
 * the explicit Maven opt-in proof.allow.local.dev.db=true.
 */
@SpringBootTest(
        classes = HumanResourceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.scheduling.enabled=false",
                "logging.level.org.hibernate.orm.connections.pooling=OFF"
        })
@ActiveProfiles("proof-local")
@ContextConfiguration(initializers = PayrollProofDatabaseGuard.class)
class PayrollCloseTransactionProofTest {

    private static final AtomicLong COMPANY_IDS = new AtomicLong(9_000_000_000L);

    @Autowired
    private PayrollPeriodRepository periodRepository;

    @Autowired
    private FlowableTaskService flowableTaskService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private PayrollJournalBatchRepository batchRepository;

    @Autowired
    private PayrollJournalEntryRepository journalEntryRepository;

    @Autowired
    private IntegrationPayrollProperties integrationProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PayrollJournalSnapshotService snapshotService;

    @MockitoSpyBean
    private IntegrationOutboxRepository outboxRepository;

    @MockitoSpyBean
    private PayrollJournalEntryRepository journalEntriesSpy;

    @MockitoSpyBean
    private PayrollPeriodService payrollPeriodServiceSpy;

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

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private Long testCompanyId;
    private Long testPeriodId;
    private String closeProcessInstanceId;
    private String nextPayrollProcessBusinessKey;

    @Test
    void failureAfterCloseWritesRollsBackPeriodAndFinanceTaskTogether() {
        startCloseProcessAtFinanceTask();

        setFinancialOfficerAuthentication();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            throw new ForcedPostCloseFailure();
        }).when(payrollPeriodServiceSpy).closeAndOpenNext(testCompanyId, LocalDate.of(2026, 10, 31));

        String financeTaskId = financeTaskId();
        assertThatThrownBy(() -> flowableTaskService.completeTask(
                financeTaskId,
                Map.of("approved", true)))
                .isInstanceOf(ForcedPostCloseFailure.class);

        PayrollPeriod reloaded = periodRepository.findById(testPeriodId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PayrollPeriodStatus.OPEN);
        assertThat(periodRepository.findByCompanyIdOrderByPeriodStartDesc(testCompanyId))
                .hasSize(1);
        assertThat(taskService.createTaskQuery().taskId(financeTaskId).singleResult())
                .as("Finance task remains active after rollback")
                .isNotNull();
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(nextPayrollProcessBusinessKey)
                .count())
                .as("next-period payroll process was rolled back")
                .isZero();
    }

    @Test
    void closeSucceedsWithoutInjectedFailure() {
        startCloseProcessAtFinanceTask();

        setFinancialOfficerAuthentication();
        String financeTaskId = financeTaskId();
        flowableTaskService.completeTask(financeTaskId, Map.of("approved", true));

        assertThat(periodRepository.findById(testPeriodId).orElseThrow().getStatus())
                .isEqualTo(PayrollPeriodStatus.CLOSED);
        assertThat(periodRepository.findByCompanyIdOrderByPeriodStartDesc(testCompanyId))
                .hasSize(2);
        assertThat(taskService.createTaskQuery().taskId(financeTaskId).singleResult())
                .as("Finance task completes in the control run")
                .isNull();
        assertThat(integrationProperties.isEnabled()).isFalse();
        assertThat(applicationContext.getBeansOfType(PayrollOutboxRelay.class)).isEmpty();
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void enabledIntegrationDoesNotSnapshotACompanyOutsideItsConfiguredScope() {
        enableIntegrationForTestCompany();
        startCloseProcessAtFinanceTask();
        createBalancedJournal();
        integrationProperties.setCompanyId(testCompanyId + 1);

        setFinancialOfficerAuthentication();
        flowableTaskService.completeTask(financeTaskId(), Map.of("approved", true));

        assertThat(periodRepository.findById(testPeriodId).orElseThrow().getStatus())
                .isEqualTo(PayrollPeriodStatus.CLOSED);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payroll_journal_snapshot where payroll_period_id=?",
                Integer.class, testPeriodId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from integration_outbox where aggregate_id=?",
                Integer.class, journalReference())).isZero();
    }

    @Test
    void snapshotWriteFailureRollsBackCloseAndFinanceTask() {
        enableIntegrationForTestCompany();
        startCloseProcessAtFinanceTask();
        createBalancedJournal();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new ForcedPostCloseFailure();
        }).when(snapshotService).createForClosedPeriod(testPeriodId, testCompanyId);

        setFinancialOfficerAuthentication();
        assertThatThrownBy(() -> flowableTaskService.completeTask(
                financeTaskId(), Map.of("approved", true)))
                .isInstanceOf(ForcedPostCloseFailure.class);
        assertCloseRolledBack();
    }

    @Test
    void outboxWriteFailureRollsBackSnapshotCloseAndFinanceTask() {
        enableIntegrationForTestCompany();
        startCloseProcessAtFinanceTask();
        createBalancedJournal();
        doThrow(new ForcedPostCloseFailure())
                .when(outboxRepository).save(any(IntegrationOutboxEvent.class));

        setFinancialOfficerAuthentication();
        assertThatThrownBy(() -> flowableTaskService.completeTask(
                financeTaskId(), Map.of("approved", true)))
                .isInstanceOf(ForcedPostCloseFailure.class);
        assertCloseRolledBack();
    }

    @Test
    void enabledCloseWritesOneSnapshotItsLinesAndOutboxWithBatchTotals() {
        enableIntegrationForTestCompany();
        startCloseProcessAtFinanceTask();
        PayrollJournalBatch batch = createNormalizedJournal();
        List<PayrollJournalEntry> privateLines =
                journalEntryRepository.findByCompanyAndPeriod(testCompanyId, testPeriodId);
        for (PayrollJournalEntry line : privateLines) {
            line.setAccountCode("PRIVATE_ACCOUNT_CODE");
            line.setAccountName("PRIVATE_ACCOUNT_NAME");
            line.setDescription("PRIVATE_LINE_MARKER PERSONAL_NAME_MARKER ACCOUNT_NUMBER_MARKER");
        }
        journalEntryRepository.saveAllAndFlush(privateLines);

        setFinancialOfficerAuthentication();
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            flowableTaskService.completeTask(financeTaskId(), Map.of("approved", true));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String reference = journalReference();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payroll_journal_snapshot where payroll_period_id = ? and status <> 'VOID'",
                Integer.class, testPeriodId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payroll_journal_snapshot_line l join payroll_journal_snapshot s on s.id=l.snapshot_id where s.payroll_period_id=?",
                Integer.class, testPeriodId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from integration_outbox where aggregate_id = ?",
                Integer.class, reference)).isEqualTo(1);
        var snapshot = jdbcTemplate.queryForMap(
                "select total_debit, total_credit, currency, header_narration from payroll_journal_snapshot where payroll_period_id=?",
                testPeriodId);
        assertThat((BigDecimal) snapshot.get("total_debit")).isEqualByComparingTo(batch.getTotalDebit());
        assertThat((BigDecimal) snapshot.get("total_credit")).isEqualByComparingTo(batch.getTotalCredit());
        assertThat(snapshot.get("currency")).isEqualTo("NGN");
        assertThat(snapshot.get("header_narration")).isEqualTo("Payroll 2026-10");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payroll_journal_snapshot_line l join payroll_journal_snapshot s on s.id=l.snapshot_id where s.payroll_period_id=? and l.narration is null",
                Integer.class, testPeriodId)).isEqualTo(4);
        String emitted = appender.list.stream()
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(emitted).doesNotContain(
                "100.00", "PRIVATE_LINE_MARKER", "PRIVATE_ACCOUNT_CODE",
                "PRIVATE_ACCOUNT_NAME", "PERSONAL_NAME_MARKER", "ACCOUNT_NUMBER_MARKER",
                "\"eventType\":\"payroll.finalised\"");
    }

    @Test
    void largeJournalAggregatesToTwoLinesWithoutChangingTotals() {
        enableIntegrationForTestCompany();
        startCloseProcessAtFinanceTask();
        PayrollJournalBatch batch = createAggregatableJournal();

        setFinancialOfficerAuthentication();
        flowableTaskService.completeTask(financeTaskId(), Map.of("approved", true));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payroll_journal_snapshot_line l join payroll_journal_snapshot s on s.id=l.snapshot_id where s.payroll_period_id=?",
                Integer.class, testPeriodId)).isEqualTo(2);
        var totals = jdbcTemplate.queryForMap(
                "select total_debit, total_credit from payroll_journal_snapshot where payroll_period_id=?",
                testPeriodId);
        assertThat((BigDecimal) totals.get("total_debit")).isEqualByComparingTo(batch.getTotalDebit());
        assertThat((BigDecimal) totals.get("total_credit")).isEqualByComparingTo(batch.getTotalCredit());
    }

    @ParameterizedTest(name = "finance close rolls back for preflight failure: {0}")
    @EnumSource(PreflightFailure.class)
    void preflightFailureRollsBackCloseAndFinanceTaskWithReadableCause(PreflightFailure failure) {
        enableIntegrationForTestCompany();
        startCloseProcessAtFinanceTask();
        createPreflightJournal(failure);
        if (failure == PreflightFailure.EXCESS_SCALE) {
            List<PayrollJournalEntry> loaded =
                    journalEntriesSpy.findByCompanyAndPeriod(testCompanyId, testPeriodId);
            loaded.getFirst().setDebitAmount(new BigDecimal("100.001"));
            doReturn(loaded).when(journalEntriesSpy)
                    .findByCompanyAndPeriod(eq(testCompanyId), eq(testPeriodId));
        }

        String financeTask = financeTaskId();
        setFinancialOfficerAuthentication();
        assertThatThrownBy(() -> flowableTaskService.completeTask(financeTask, Map.of("approved", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(failure.expectedMessage());
        assertThat(periodRepository.findById(testPeriodId).orElseThrow().getStatus())
                .isEqualTo(PayrollPeriodStatus.OPEN);
        assertThat(periodRepository.findByCompanyIdOrderByPeriodStartDesc(testCompanyId)).hasSize(1);
        assertThat(taskService.createTaskQuery().taskId(financeTask).singleResult()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payroll_journal_snapshot where payroll_period_id=?",
                Integer.class, testPeriodId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from integration_outbox where aggregate_id=?",
                Integer.class, journalReference())).isZero();
    }

    private void assertCloseRolledBack() {
        String financeTask = financeTaskId();
        assertThat(periodRepository.findById(testPeriodId).orElseThrow().getStatus())
                .isEqualTo(PayrollPeriodStatus.OPEN);
        assertThat(periodRepository.findByCompanyIdOrderByPeriodStartDesc(testCompanyId)).hasSize(1);
        assertThat(taskService.createTaskQuery().taskId(financeTask).singleResult()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payroll_journal_snapshot where payroll_period_id=?",
                Integer.class, testPeriodId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from integration_outbox where aggregate_id=?",
                Integer.class, journalReference())).isZero();
    }

    private void enableIntegrationForTestCompany() {
        integrationProperties.setEnabled(true);
    }

    private PayrollJournalBatch createBalancedJournal() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        PayrollJournalBatch batch = new PayrollJournalBatch();
        batch.setCompanyId(testCompanyId);
        batch.setPayrollPeriodId(testPeriodId);
        batch.setPeriodStart(start);
        batch.setPeriodEnd(end);
        batch.setTotalDebit(new BigDecimal("100.00"));
        batch.setTotalCredit(new BigDecimal("100.00"));
        batch.setBalanced(true);
        batch = batchRepository.saveAndFlush(batch);
        saveJournalLine(batch, start, end, "5000", "Payroll expense",
                new BigDecimal("100.00"), BigDecimal.ZERO, "DEBIT");
        saveJournalLine(batch, start, end, "2100", "Payroll payable",
                BigDecimal.ZERO, new BigDecimal("100.00"), "CREDIT");
        return batch;
    }

    private PayrollJournalBatch createNormalizedJournal() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        PayrollJournalBatch batch = new PayrollJournalBatch();
        batch.setCompanyId(testCompanyId);
        batch.setPayrollPeriodId(testPeriodId);
        batch.setPeriodStart(start);
        batch.setPeriodEnd(end);
        batch.setTotalDebit(new BigDecimal("100.00"));
        batch.setTotalCredit(new BigDecimal("100.00"));
        batch.setBalanced(true);
        batch = batchRepository.saveAndFlush(batch);
        saveJournalLine(batch, start, end, "5000", "Expense",
                new BigDecimal("20.00"), new BigDecimal("30.00"), "BOTH");
        saveJournalLine(batch, start, end, "5100", "Expense two",
                new BigDecimal("80.00"), BigDecimal.ZERO, "DEBIT");
        saveJournalLine(batch, start, end, "2100", "Payable",
                BigDecimal.ZERO, new BigDecimal("70.00"), "CREDIT");
        saveJournalLine(batch, start, end, "9999", "Zero line",
                BigDecimal.ZERO, BigDecimal.ZERO, "ZERO");
        return batch;
    }

    private PayrollJournalBatch createAggregatableJournal() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        PayrollJournalBatch batch = new PayrollJournalBatch();
        batch.setCompanyId(testCompanyId);
        batch.setPayrollPeriodId(testPeriodId);
        batch.setPeriodStart(start);
        batch.setPeriodEnd(end);
        batch.setTotalDebit(new BigDecimal("101.00"));
        batch.setTotalCredit(new BigDecimal("101.00"));
        batch.setBalanced(true);
        batch = batchRepository.saveAndFlush(batch);
        for (int i = 0; i < 101; i++) {
            saveJournalLine(batch, start, end, "5000", "Expense",
                    BigDecimal.ONE, BigDecimal.ZERO, "DEBIT");
            saveJournalLine(batch, start, end, "2100", "Payable",
                    BigDecimal.ZERO, BigDecimal.ONE, "CREDIT");
        }
        return batch;
    }

    private void createPreflightJournal(PreflightFailure failure) {
        if (failure == PreflightFailure.NO_BATCH) {
            return;
        }
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = failure == PreflightFailure.PERIOD_END_MISMATCH
                ? LocalDate.of(2026, 11, 1) : LocalDate.of(2026, 10, 31);
        PayrollJournalBatch batch = new PayrollJournalBatch();
        batch.setCompanyId(testCompanyId);
        batch.setPayrollPeriodId(testPeriodId);
        batch.setPeriodStart(start);
        batch.setPeriodEnd(end);
        batch.setTotalDebit(new BigDecimal("100.00"));
        batch.setTotalCredit(new BigDecimal("100.00"));
        batch.setBalanced(failure != PreflightFailure.UNBALANCED);
        batchRepository.saveAndFlush(batch);
        if (failure == PreflightFailure.TOO_MANY_LINES) {
            for (int i = 0; i < 101; i++) {
                saveJournalLine(batch, start, end, "D" + i, "Debit " + i,
                        BigDecimal.ONE, BigDecimal.ZERO, "DEBIT");
                saveJournalLine(batch, start, end, "C" + i, "Credit " + i,
                        BigDecimal.ZERO, BigDecimal.ONE, "CREDIT");
            }
            batch.setTotalDebit(new BigDecimal("101.00"));
            batch.setTotalCredit(new BigDecimal("101.00"));
            batchRepository.saveAndFlush(batch);
            return;
        }
        if (failure == PreflightFailure.NEGATIVE_AMOUNT) {
            saveJournalLine(batch, start, end, "5000", "Expense",
                    new BigDecimal("-1.00"), BigDecimal.ZERO, "DEBIT");
            saveJournalLine(batch, start, end, "2100", "Payable",
                    BigDecimal.ZERO, BigDecimal.ONE, "CREDIT");
            return;
        }
        if (failure == PreflightFailure.BLANK_ACCOUNT_CODE) {
            saveJournalLine(batch, start, end, " ", "Expense",
                    new BigDecimal("100.00"), BigDecimal.ZERO, "DEBIT");
            saveJournalLine(batch, start, end, "2100", "Payable",
                    BigDecimal.ZERO, new BigDecimal("100.00"), "CREDIT");
            return;
        }
        saveJournalLine(batch, start, end, "5000", "Expense",
                new BigDecimal("100.00"), BigDecimal.ZERO, "DEBIT");
        saveJournalLine(batch, start, end, "2100", "Payable",
                BigDecimal.ZERO, new BigDecimal("100.00"), "CREDIT");
    }

    private void saveJournalLine(
            PayrollJournalBatch batch,
            LocalDate start,
            LocalDate end,
            String accountCode,
            String accountName,
            BigDecimal debit,
            BigDecimal credit,
            String lineType) {
        PayrollJournalEntry entry = new PayrollJournalEntry();
        entry.setCompanyId(testCompanyId);
        entry.setPayrollPeriodId(testPeriodId);
        entry.setJournalBatch(batch);
        entry.setPeriodStart(start);
        entry.setPeriodEnd(end);
        entry.setAccountCode(accountCode);
        entry.setAccountName(accountName);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setDescription("Fixed test fixture");
        entry.setLineType(lineType);
        entry.setExported(false);
        journalEntryRepository.save(entry);
    }

    private String journalReference() {
        return "PAY-2026-10-" + testPeriodId;
    }

    private enum PreflightFailure {
        NO_BATCH("journal batch is missing"),
        UNBALANCED("journal batch is not balanced"),
        PERIOD_END_MISMATCH("period end does not match"),
        TOO_MANY_LINES("exceeds the 200-line limit"),
        NEGATIVE_AMOUNT("cannot be negative"),
        EXCESS_SCALE("must fit precision 19, scale 2"),
        BLANK_ACCOUNT_CODE("needs an account code and name");

        private final String expectedMessage;

        PreflightFailure(String expectedMessage) {
            this.expectedMessage = expectedMessage;
        }

        String expectedMessage() {
            return expectedMessage;
        }
    }

    private void startCloseProcessAtFinanceTask() {
        testCompanyId = COMPANY_IDS.incrementAndGet();
        integrationProperties.setCompanyId(testCompanyId);
        LocalDate periodStart = LocalDate.of(2026, 10, 1);
        LocalDate periodEnd = LocalDate.of(2026, 10, 31);

        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(testCompanyId);
        period.setPeriodStart(periodStart);
        period.setPeriodEnd(periodEnd);
        period.setPlannedPeriodEnd(periodEnd);
        period.setStatus(PayrollPeriodStatus.OPEN);
        testPeriodId = periodRepository.saveAndFlush(period).getId();
        nextPayrollProcessBusinessKey = "BATCH_PERIOD_" + testPeriodId;
        doAnswer(invocation -> {
            org.flowable.engine.delegate.DelegateExecution execution = invocation.getArgument(0);
            execution.setVariable("employeeIds", List.of());
            return null;
        }).when(loadEmployeesForPayrollDelegate).execute(any());

        Map<String, Object> variables = Map.of(
                "companyId", testCompanyId,
                "periodId", testPeriodId,
                "periodStart", periodStart,
                "periodEnd", periodEnd,
                "actualPeriodEnd", periodEnd
        );
        ProcessInstance closeProcess = runtimeService.startProcessInstanceByKey(
                "payrollPeriodCloseProcess",
                "STAGE3_TX_PROOF_" + UUID.randomUUID(),
                variables
        );
        closeProcessInstanceId = closeProcess.getProcessInstanceId();
        Task financeTask = taskService.createTaskQuery()
                .processInstanceId(closeProcessInstanceId)
                .taskDefinitionKey("financeOfficer")
                .singleResult();
        assertThat(financeTask).as("production Finance approval task").isNotNull();
    }

    private String financeTaskId() {
        return taskService.createTaskQuery()
                .processInstanceId(closeProcessInstanceId)
                .taskDefinitionKey("financeOfficer")
                .singleResult()
                .getId();
    }

    @AfterEach
    void cleanUpOnlyThisTestData() {
        SecurityContextHolder.clearContext();
        if (closeProcessInstanceId != null
                && runtimeService.createProcessInstanceQuery()
                .processInstanceId(closeProcessInstanceId)
                .singleResult() != null) {
            runtimeService.deleteProcessInstance(closeProcessInstanceId, "Transaction proof cleanup");
        }
        if (closeProcessInstanceId != null
                && historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(closeProcessInstanceId)
                .singleResult() != null) {
            historyService.deleteHistoricProcessInstance(closeProcessInstanceId);
        }
        if (nextPayrollProcessBusinessKey != null) {
            runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(nextPayrollProcessBusinessKey)
                    .list()
                    .forEach(instance -> runtimeService.deleteProcessInstance(
                            instance.getId(), "Transaction proof cleanup"));
            historyService.createHistoricProcessInstanceQuery()
                    .processInstanceBusinessKey(nextPayrollProcessBusinessKey)
                    .list()
                    .forEach(instance -> historyService.deleteHistoricProcessInstance(instance.getId()));
        }
        if (testCompanyId != null) {
            if (testPeriodId != null) {
                jdbcTemplate.update("""
                        delete from payroll_journal_snapshot_line
                        where snapshot_id in (
                            select id from payroll_journal_snapshot where payroll_period_id = ?
                        )
                        """, testPeriodId);
                jdbcTemplate.update("delete from integration_outbox where aggregate_id = ?", journalReference());
                jdbcTemplate.update("delete from integration_alert where payroll_period_id = ?", testPeriodId);
                jdbcTemplate.update("delete from payroll_journal_snapshot where payroll_period_id = ?", testPeriodId);
                journalEntryRepository.deleteAll(
                        journalEntryRepository.findByCompanyIdAndPayrollPeriodId(testCompanyId, testPeriodId));
                batchRepository.findByCompanyIdAndPayrollPeriodId(testCompanyId, testPeriodId)
                        .ifPresent(batchRepository::delete);
            }
            periodRepository.deleteAll(periodRepository.findByCompanyIdOrderByPeriodStartDesc(testCompanyId));
        }
        integrationProperties.setEnabled(false);
        integrationProperties.setCompanyId(null);
    }

    private void setFinancialOfficerAuthentication() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "transaction-proof-token",
                now,
                now.plusSeconds(300),
                Map.of("sub", "stage3-transaction-proof", "groups", List.of("financialofficers"))
        );
        DefaultOidcUser principal = new DefaultOidcUser(List.of(), idToken, "sub");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak"));
    }

    private static final class ForcedPostCloseFailure extends RuntimeException {
    }
}
