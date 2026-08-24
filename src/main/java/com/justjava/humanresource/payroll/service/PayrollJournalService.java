package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.dto.PayrollJournalEntryDTO;
import com.justjava.humanresource.payroll.entity.Allowance;
import com.justjava.humanresource.payroll.entity.Deduction;
import com.justjava.humanresource.payroll.entity.PayrollAccountingSettings;
import com.justjava.humanresource.payroll.entity.PayrollJournalBatch;
import com.justjava.humanresource.payroll.entity.PayrollJournalEntry;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.repositories.AllowanceRepository;
import com.justjava.humanresource.payroll.repositories.DeductionRepository;
import com.justjava.humanresource.payroll.repositories.PayrollAccountingSettingsRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalBatchRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.diagnostics.PayrollJournalDiagnosticLine;
import com.justjava.humanresource.payroll.service.diagnostics.PayrollJournalDiagnostics;
import com.justjava.humanresource.payroll.service.diagnostics.PayrollJournalImbalanceException;
import com.justjava.humanresource.payroll.service.diagnostics.PayrollRunDiagnosticSummary;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import com.justjava.humanresource.payroll.statutory.repositories.PensionSchemeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollJournalService {
    private static final String RESIDUAL_COMPONENT_CODE = "RESIDUAL";
    private static final String PAYMENT_CLEARING_SOURCE_TYPE = "PAYMENT_CLEARING";
    private static final String BANK_PAYMENT_SOURCE_CODE = "BANK_PAYMENT";

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollLineItemRepository payrollLineItemRepository;
    private final PayrollJournalEntryRepository journalRepository;
    private final PayrollJournalBatchRepository batchRepository;
    private final PayrollAccountingSettingsRepository settingsRepository;
    private final PayrollPeriodService payrollPeriodService;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final AllowanceRepository allowanceRepository;
    private final DeductionRepository deductionRepository;
    private final PensionSchemeRepository pensionSchemeRepository;

    @Transactional
    public void generatePaymentClearingEntries(
            Long companyId,
            Long periodId,
            BigDecimal amount,
            String paymentBatchReference) {

        BigDecimal clearingAmount = safe(amount);
        if (clearingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Payment clearing amount must be greater than zero.");
        }

        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalStateException("Payroll period not found."));

        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException("Period does not belong to provided company.");
        }

        String clearingSourceCode = paymentClearingSourceCode(paymentBatchReference);
        if (journalRepository.existsByCompanyIdAndPayrollPeriodIdAndSourceTypeAndSourceCode(
                companyId,
                periodId,
                PAYMENT_CLEARING_SOURCE_TYPE,
                clearingSourceCode)) {
            log.info("Payment clearing journal already exists for company {} period {} batch {}. Skipping.",
                    companyId, periodId, paymentBatchReference);
            return;
        }

        PayrollJournalBatch batch = batchRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId)
                .orElseThrow(() -> new IllegalStateException("Journal batch not found for period."));

        PayrollAccountingSettings settings = resolveSettings(companyId);
        BigDecimal outstandingSuspense = suspenseBalance(companyId, periodId, settings.getSuspenseAccountCode());
        if (outstandingSuspense.compareTo(clearingAmount) != 0) {
            throw new IllegalStateException("Successful payment total " + clearingAmount
                    + " does not match outstanding payroll suspense " + outstandingSuspense + ".");
        }

        PayrollJournalEntry suspenseDebit = paymentClearingEntry(
                companyId,
                period,
                batch,
                settings.getSuspenseAccountCode(),
                settings.getSuspenseAccountName(),
                clearingAmount,
                BigDecimal.ZERO,
                "DEBIT",
                "Clear payroll suspense after successful salary payment",
                clearingSourceCode
        );

        PayrollJournalEntry bankCredit = paymentClearingEntry(
                companyId,
                period,
                batch,
                settings.getBankAccountCode(),
                settings.getBankAccountName(),
                BigDecimal.ZERO,
                clearingAmount,
                "CREDIT",
                "Record bank/cash outflow for salary payment",
                clearingSourceCode
        );

        journalRepository.save(suspenseDebit);
        journalRepository.save(bankCredit);

        batch.setTotalDebit(safe(batch.getTotalDebit()).add(clearingAmount));
        batch.setTotalCredit(safe(batch.getTotalCredit()).add(clearingAmount));
        batch.setBalanced(batch.getTotalDebit().compareTo(batch.getTotalCredit()) == 0);
        batchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public void verifyPaymentSuspenseCleared(Long companyId, Long periodId) {
        PayrollAccountingSettings settings = resolveSettings(companyId);
        BigDecimal balance = suspenseBalance(companyId, periodId, settings.getSuspenseAccountCode());
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Payroll suspense is not cleared for period " + periodId
                    + ". Outstanding credit balance: " + balance + ".");
        }
    }

    @Transactional
    public void generateJournalEntries(
            Long companyId,
            Long periodId,
            LocalDate start,
            LocalDate end) {

        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalStateException("Payroll period not found."));

        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException("Period does not belong to provided company.");
        }

        if (!journalRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId).isEmpty()
                || batchRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId).isPresent()) {
            throw new IllegalStateException("Journal entries already exist for this period.");
        }

        List<PayrollRun> runs = payrollRunRepository.findLatestByCompanyAndPeriodAndStatus(
                companyId,
                period.getPeriodStart(),
                period.getPeriodEnd(),
                PayrollRunStatus.POSTED
        );

        if (runs.isEmpty()) {
            throw new IllegalStateException("No POSTED payroll runs found for period.");
        }

        PayrollAccountingSettings settings = resolveSettings(companyId);
        Map<String, Allowance> allowances = allowanceRepository.findAll().stream()
                .collect(Collectors.toMap(Allowance::getCode, Function.identity(), (a, b) -> a));
        Map<String, Deduction> deductions = deductionRepository.findAll().stream()
                .collect(Collectors.toMap(Deduction::getCode, Function.identity(), (a, b) -> a));
        Optional<PensionScheme> pensionScheme = pensionSchemeRepository.findEffectiveScheme(
                period.getPeriodEnd(),
                com.justjava.humanresource.core.enums.RecordStatus.ACTIVE
        );
        PayrollJournalDiagnostics diagnostics = new PayrollJournalDiagnostics(
                companyId,
                periodId,
                period.getPeriodStart(),
                period.getPeriodEnd()
        );

        Map<JournalKey, BigDecimal> grouped = new LinkedHashMap<>();

        for (PayrollRun run : runs) {
            List<PayrollLineItem> lineItems = payrollLineItemRepository.findByPayrollRunId(run.getId());
            BigDecimal includedLineItemsTotal = BigDecimal.ZERO;
            BigDecimal skippedLineItemsTotal = BigDecimal.ZERO;
            for (PayrollLineItem line : lineItems) {
                String skipReason = skipReason(line);
                if (skipReason != null) {
                    skippedLineItemsTotal = skippedLineItemsTotal.add(safe(line.getAmount()));
                    diagnostics.addSkippedLine(diagnosticLine(run, line, null, null, skipReason));
                    continue;
                }
                includedLineItemsTotal = includedLineItemsTotal.add(line.getAmount());

                for (JournalLine journalLine : resolveJournalLines(line, allowances, deductions, pensionScheme, settings)) {
                    grouped.merge(journalLine.key(), line.getAmount(), BigDecimal::add);
                    diagnostics.addGeneratedLine(diagnosticLine(run, line, journalLine, line.getAmount(), null));
                }
            }
            diagnostics.addPayrollRun(PayrollRunDiagnosticSummary.builder()
                    .payrollRunId(run.getId())
                    .employeeId(run.getEmployee() != null ? run.getEmployee().getId() : null)
                    .employeeNumber(run.getEmployee() != null ? run.getEmployee().getEmployeeNumber() : null)
                    .employeeName(run.getEmployee() != null ? run.getEmployee().getFullName() : null)
                    .grossPay(safe(run.getGrossPay()))
                    .totalDeductions(safe(run.getTotalDeductions()))
                    .netPay(safe(run.getNetPay()))
                    .includedLineItemsTotal(includedLineItemsTotal)
                    .skippedLineItemsTotal(skippedLineItemsTotal)
                    .build());
        }

        BigDecimal netPay = runs.stream()
                .map(PayrollRun::getNetPay)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        grouped.merge(
                new JournalKey(
                        settings.getSuspenseAccountCode(),
                        settings.getSuspenseAccountName(),
                        "CREDIT",
                        "NET_PAY",
                        "NET",
                        "Net Salary",
                        "Net salary payable through payroll suspense"
                ),
                netPay,
                BigDecimal::add
        );
        for (PayrollRun run : runs) {
            PayrollLineItem netLine = new PayrollLineItem();
            netLine.setComponentType(null);
            netLine.setComponentCode("NET_PAY");
            netLine.setDescription("Net Salary");
            netLine.setAmount(safe(run.getNetPay()));
            diagnostics.addGeneratedLine(diagnosticLine(
                    run,
                    netLine,
                    new JournalLine(new JournalKey(
                            settings.getSuspenseAccountCode(),
                            settings.getSuspenseAccountName(),
                            "CREDIT",
                            "NET_PAY",
                            "NET",
                            "Net Salary",
                            "Net salary payable through payroll suspense"
                    ), "Payroll accounting settings"),
                    safe(run.getNetPay()),
                    null
            ));
        }

        BigDecimal totalDebit = grouped.entrySet().stream()
                .filter(entry -> entry.getKey().lineType().equals("DEBIT"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = grouped.entrySet().stream()
                .filter(entry -> entry.getKey().lineType().equals("CREDIT"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        diagnostics.setTotals(totalDebit, totalCredit);

        if (totalDebit.compareTo(totalCredit) != 0) {
            log.error(diagnostics.toLogReport());
            throw new PayrollJournalImbalanceException(diagnostics);
        }

        PayrollJournalBatch batch = new PayrollJournalBatch();
        batch.setCompanyId(companyId);
        batch.setPayrollPeriodId(periodId);
        batch.setPeriodStart(period.getPeriodStart());
        batch.setPeriodEnd(period.getPeriodEnd());
        batch.setTotalDebit(totalDebit);
        batch.setTotalCredit(totalCredit);
        batch.setBalanced(true);
        batch = batchRepository.save(batch);

        PayrollJournalBatch savedBatch = batch;
        grouped.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().accountCode()))
                .forEach(entry -> saveEntry(companyId, period, savedBatch, entry.getKey(), entry.getValue()));
    }

    @Transactional(readOnly = true)
    public List<PayrollJournalEntryDTO> getUnexported(Long companyId) {
        return journalRepository.findUnexportedByCompany(companyId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PayrollJournalEntryDTO> getByPeriod(Long companyId, Long periodId) {
        return journalRepository.findByCompanyAndPeriod(companyId, periodId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public byte[] exportPeriodCsv(Long companyId, Long periodId, String exportedBy) {
        PayrollJournalBatch batch = batchRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId)
                .orElseThrow(() -> new IllegalStateException("Journal batch not found for period."));

        List<PayrollJournalEntry> entries = journalRepository.findByCompanyAndPeriod(companyId, periodId);
        if (entries.isEmpty()) {
            throw new IllegalStateException("No journal entries found for period.");
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Batch ID,Entry ID,Period Start,Period End,Account Code,Account Name,Debit,Credit,Source Type,Source Code,Source Name,Description\n");
        for (PayrollJournalEntry entry : entries) {
            csv.append(batch.getId()).append(',')
                    .append(entry.getId()).append(',')
                    .append(entry.getPeriodStart()).append(',')
                    .append(entry.getPeriodEnd()).append(',')
                    .append(escapeCsv(entry.getAccountCode())).append(',')
                    .append(escapeCsv(entry.getAccountName())).append(',')
                    .append(entry.getDebitAmount()).append(',')
                    .append(entry.getCreditAmount()).append(',')
                    .append(escapeCsv(entry.getSourceType())).append(',')
                    .append(escapeCsv(entry.getSourceCode())).append(',')
                    .append(escapeCsv(entry.getSourceName())).append(',')
                    .append(escapeCsv(entry.getDescription())).append('\n');
        }

        LocalDateTime exportedAt = LocalDateTime.now();
        batch.setExported(true);
        batch.setExportedAt(exportedAt);
        batch.setExportedBy(exportedBy);
        batch.setExportFormat("CSV");
        batch.setExportReference("PAYROLL-JOURNAL-" + periodId + "-" + exportedAt);

        entries.forEach(entry -> {
            entry.setExported(true);
            entry.setExportedAt(exportedAt);
            entry.setExportedBy(exportedBy);
        });

        batchRepository.save(batch);
        journalRepository.saveAll(entries);

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void markAsExported(Long journalEntryId) {
        PayrollJournalEntry entry = journalRepository.findById(journalEntryId)
                .orElseThrow(() -> new IllegalStateException("Journal entry not found."));
        entry.setExported(true);
        entry.setExportedAt(LocalDateTime.now());
        journalRepository.save(entry);
    }

    private PayrollAccountingSettings resolveSettings(Long companyId) {
        return settingsRepository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    PayrollAccountingSettings settings = new PayrollAccountingSettings();
                    settings.setCompanyId(companyId);
                    return settingsRepository.save(settings);
                });
    }

    private List<JournalLine> resolveJournalLines(
            PayrollLineItem line,
            Map<String, Allowance> allowances,
            Map<String, Deduction> deductions,
            Optional<PensionScheme> pensionScheme,
            PayrollAccountingSettings settings) {

        if (RESIDUAL_COMPONENT_CODE.equalsIgnoreCase(line.getComponentCode())) {
            AccountRef expense = resolveAccount(
                    settings.getResidualAdjustmentExpenseAccountCode(),
                    settings.getResidualAdjustmentExpenseAccountName(),
                    PayrollAccountingSettings.DEFAULT_RESIDUAL_ADJUSTMENT_EXPENSE_ACCOUNT_CODE,
                    PayrollAccountingSettings.DEFAULT_RESIDUAL_ADJUSTMENT_EXPENSE_ACCOUNT_NAME,
                    "Residual adjustment expense settings",
                    "System default residual adjustment expense"
            );
            AccountRef clearing = resolveAccount(
                    settings.getResidualClearingAccountCode(),
                    settings.getResidualClearingAccountName(),
                    PayrollAccountingSettings.DEFAULT_RESIDUAL_CLEARING_ACCOUNT_CODE,
                    PayrollAccountingSettings.DEFAULT_RESIDUAL_CLEARING_ACCOUNT_NAME,
                    "Residual clearing settings",
                    "System default residual clearing"
            );

            return List.of(
                    internalAdjustmentLine(expense, line, "DEBIT", "Payroll residual adjustment expense"),
                    internalAdjustmentLine(clearing, line, "CREDIT", "Payroll residual clearing")
            );
        }

        if (line.getComponentType() == PayComponentType.EARNING) {
            Allowance allowance = allowances.get(line.getComponentCode());
            AccountRef account = allowance == null
                    ? new AccountRef(settings.getDefaultEarningExpenseAccountCode(), settings.getDefaultEarningExpenseAccountName(), "Default earnings expense")
                    : resolveAccount(
                            allowance.getExpenseAccountCode(),
                            allowance.getExpenseAccountName(),
                            settings.getDefaultEarningExpenseAccountCode(),
                            settings.getDefaultEarningExpenseAccountName(),
                            "Allowance " + allowance.getCode(),
                            "Default earnings expense"
                    );

            return List.of(debit(account, line, "Payroll earning expense"));
        }

        if (line.getComponentType() == PayComponentType.DEDUCTION) {
            AccountRef account;
            if ("PAYE".equalsIgnoreCase(line.getComponentCode())) {
                account = new AccountRef(settings.getPayePayableAccountCode(), settings.getPayePayableAccountName(), "PAYE payable settings");
            } else if ("PENSION_EMP".equalsIgnoreCase(line.getComponentCode())) {
                account = pensionScheme
                        .map(scheme -> resolveAccount(
                                scheme.getEmployeePayableAccountCode(),
                                scheme.getEmployeePayableAccountName(),
                                settings.getPensionPayableAccountCode(),
                                settings.getPensionPayableAccountName(),
                                "Pension scheme employee payable",
                                "Employee pension payable settings"
                        ))
                        .orElse(new AccountRef(settings.getPensionPayableAccountCode(), settings.getPensionPayableAccountName(), "Employee pension payable settings"));
            } else {
                Deduction deduction = deductions.get(line.getComponentCode());
                account = deduction == null
                        ? new AccountRef(settings.getDefaultDeductionPayableAccountCode(), settings.getDefaultDeductionPayableAccountName(), "Default deduction payable")
                        : resolveAccount(
                                deduction.getPayableAccountCode(),
                                deduction.getPayableAccountName(),
                                settings.getDefaultDeductionPayableAccountCode(),
                                settings.getDefaultDeductionPayableAccountName(),
                                "Deduction " + deduction.getCode(),
                                "Default deduction payable"
                        );
            }
            return List.of(credit(account, line, "Payroll deduction liability"));
        }

        if (line.getComponentType() == PayComponentType.EMPLOYER_COST) {
            AccountRef expense = pensionScheme
                    .map(scheme -> resolveAccount(
                            scheme.getEmployerExpenseAccountCode(),
                            scheme.getEmployerExpenseAccountName(),
                            settings.getEmployerPensionExpenseAccountCode(),
                            settings.getEmployerPensionExpenseAccountName(),
                            "Pension scheme employer expense",
                            "Employer pension expense settings"
                    ))
                    .orElse(new AccountRef(settings.getEmployerPensionExpenseAccountCode(), settings.getEmployerPensionExpenseAccountName(), "Employer pension expense settings"));

            AccountRef payable = pensionScheme
                    .map(scheme -> resolveAccount(
                            scheme.getEmployerPayableAccountCode(),
                            scheme.getEmployerPayableAccountName(),
                            settings.getEmployerPensionPayableAccountCode(),
                            settings.getEmployerPensionPayableAccountName(),
                            "Pension scheme employer payable",
                            "Employer pension payable settings"
                    ))
                    .orElse(new AccountRef(settings.getEmployerPensionPayableAccountCode(), settings.getEmployerPensionPayableAccountName(), "Employer pension payable settings"));

            return List.of(
                    debit(expense, line, "Employer payroll cost"),
                    credit(payable, line, "Employer payroll liability")
            );
        }

        return List.of();
    }

    private void saveEntry(Long companyId, PayrollPeriod period, PayrollJournalBatch batch, JournalKey key, BigDecimal amount) {
        PayrollJournalEntry entry = new PayrollJournalEntry();
        entry.setCompanyId(companyId);
        entry.setPayrollPeriodId(period.getId());
        entry.setJournalBatch(batch);
        entry.setPeriodStart(period.getPeriodStart());
        entry.setPeriodEnd(period.getPeriodEnd());
        entry.setAccountCode(key.accountCode());
        entry.setAccountName(key.accountName());
        entry.setDebitAmount(key.lineType().equals("DEBIT") ? amount : BigDecimal.ZERO);
        entry.setCreditAmount(key.lineType().equals("CREDIT") ? amount : BigDecimal.ZERO);
        entry.setDescription(key.description());
        entry.setSourceType(key.sourceType());
        entry.setSourceCode(key.sourceCode());
        entry.setSourceName(key.sourceName());
        entry.setLineType(key.lineType());
        entry.setExported(false);
        journalRepository.save(entry);
    }

    private PayrollJournalEntry paymentClearingEntry(
            Long companyId,
            PayrollPeriod period,
            PayrollJournalBatch batch,
            String accountCode,
            String accountName,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String lineType,
            String description,
            String sourceCode) {

        if (!hasText(accountCode) || !hasText(accountName)) {
            throw new IllegalStateException("Missing account mapping for payroll payment clearing.");
        }

        PayrollJournalEntry entry = new PayrollJournalEntry();
        entry.setCompanyId(companyId);
        entry.setPayrollPeriodId(period.getId());
        entry.setJournalBatch(batch);
        entry.setPeriodStart(period.getPeriodStart());
        entry.setPeriodEnd(period.getPeriodEnd());
        entry.setAccountCode(accountCode);
        entry.setAccountName(accountName);
        entry.setDebitAmount(safe(debitAmount));
        entry.setCreditAmount(safe(creditAmount));
        entry.setDescription(description);
        entry.setSourceType(PAYMENT_CLEARING_SOURCE_TYPE);
        entry.setSourceCode(sourceCode);
        entry.setSourceName("Payroll bank payment");
        entry.setLineType(lineType);
        entry.setExported(false);
        return entry;
    }

    private BigDecimal suspenseBalance(Long companyId, Long periodId, String suspenseAccountCode) {
        return journalRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId).stream()
                .filter(entry -> Objects.equals(suspenseAccountCode, entry.getAccountCode()))
                .map(entry -> safe(entry.getCreditAmount()).subtract(safe(entry.getDebitAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String paymentClearingSourceCode(String paymentBatchReference) {
        if (!hasText(paymentBatchReference)) {
            throw new IllegalStateException("Payment batch reference is required for journal clearing.");
        }
        return BANK_PAYMENT_SOURCE_CODE + ":" + paymentBatchReference;
    }

    private JournalLine debit(AccountRef account, PayrollLineItem line, String description) {
        return journalLine(account, line, "DEBIT", description);
    }

    private JournalLine credit(AccountRef account, PayrollLineItem line, String description) {
        return journalLine(account, line, "CREDIT", description);
    }

    private JournalLine journalLine(AccountRef account, PayrollLineItem line, String lineType, String description) {
        return new JournalLine(new JournalKey(
                account.code(),
                account.name(),
                lineType,
                line.getComponentType().name(),
                line.getComponentCode(),
                line.getDescription(),
                description + " - " + line.getDescription()
        ), account.source());
    }

    private JournalLine internalAdjustmentLine(AccountRef account, PayrollLineItem line, String lineType, String description) {
        return new JournalLine(new JournalKey(
                account.code(),
                account.name(),
                lineType,
                "INTERNAL_ADJUSTMENT",
                RESIDUAL_COMPONENT_CODE,
                line.getDescription(),
                description + " - " + line.getDescription()
        ), account.source());
    }

    private AccountRef resolveAccount(
            String accountCode,
            String accountName,
            String defaultCode,
            String defaultName,
            String specificLabel,
            String defaultLabel) {

        String code = hasText(accountCode) ? accountCode.trim() : defaultCode;
        String name = hasText(accountName) ? accountName.trim() : defaultName;
        if (!hasText(code) || !hasText(name)) {
            throw new IllegalStateException("Missing account mapping for " + specificLabel + ".");
        }
        String source = hasText(accountCode) && hasText(accountName) ? specificLabel : defaultLabel;
        return new AccountRef(code, name, source);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String skipReason(PayrollLineItem line) {
        if (line.isOutOfPayroll()) {
            return "OUT_OF_PAYROLL";
        }
        if (line.getAmount() == null) {
            return "MISSING_AMOUNT";
        }
        if (line.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return "ZERO_AMOUNT";
        }
        if (line.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            return "NEGATIVE_AMOUNT";
        }
        return null;
    }

    private PayrollJournalDiagnosticLine diagnosticLine(
            PayrollRun run,
            PayrollLineItem line,
            JournalLine journalLine,
            BigDecimal amount,
            String skipReason) {

        String lineType = journalLine != null ? journalLine.key().lineType() : null;
        BigDecimal debit = "DEBIT".equals(lineType) ? safe(amount) : BigDecimal.ZERO;
        BigDecimal credit = "CREDIT".equals(lineType) ? safe(amount) : BigDecimal.ZERO;
        return PayrollJournalDiagnosticLine.builder()
                .payrollRunId(run != null ? run.getId() : null)
                .employeeId(run != null && run.getEmployee() != null ? run.getEmployee().getId() : line.getEmployee() != null ? line.getEmployee().getId() : null)
                .employeeNumber(run != null && run.getEmployee() != null ? run.getEmployee().getEmployeeNumber() : line.getEmployee() != null ? line.getEmployee().getEmployeeNumber() : null)
                .employeeName(run != null && run.getEmployee() != null ? run.getEmployee().getFullName() : line.getEmployee() != null ? line.getEmployee().getFullName() : null)
                .componentType(journalLine != null ? journalLine.key().sourceType() : line.getComponentType() != null ? line.getComponentType().name() : "NET_PAY")
                .componentCode(journalLine != null ? journalLine.key().sourceCode() : line.getComponentCode())
                .componentName(journalLine != null ? journalLine.key().sourceName() : line.getDescription())
                .payrollLineAmount(safe(line.getAmount()))
                .lineType(lineType)
                .accountCode(journalLine != null ? journalLine.key().accountCode() : null)
                .accountName(journalLine != null ? journalLine.key().accountName() : null)
                .accountSource(journalLine != null ? journalLine.accountSource() : null)
                .debitAmount(debit)
                .creditAmount(credit)
                .description(journalLine != null ? journalLine.key().description() : line.getDescription())
                .skipReason(skipReason)
                .build();
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private PayrollJournalEntryDTO mapToDto(PayrollJournalEntry entity) {
        PayrollPeriod period = payrollPeriodService.findById(entity.getPayrollPeriodId());
        return PayrollJournalEntryDTO.builder()
                .id(entity.getId())
                .companyId(entity.getCompanyId())
                .payrollPeriodId(entity.getPayrollPeriodId())
                .journalBatchId(entity.getJournalBatch() != null ? entity.getJournalBatch().getId() : null)
                .periodStart(entity.getPeriodStart() != null ? entity.getPeriodStart() : period != null ? period.getPeriodStart() : null)
                .periodEnd(entity.getPeriodEnd() != null ? entity.getPeriodEnd() : period != null ? period.getPeriodEnd() : null)
                .accountCode(entity.getAccountCode())
                .accountName(entity.getAccountName())
                .debitAmount(entity.getDebitAmount())
                .creditAmount(entity.getCreditAmount())
                .description(entity.getDescription())
                .sourceType(entity.getSourceType())
                .sourceCode(entity.getSourceCode())
                .sourceName(entity.getSourceName())
                .lineType(entity.getLineType())
                .exported(entity.isExported())
                .build();
    }

    private record AccountRef(String code, String name, String source) {
    }

    private record JournalLine(JournalKey key, String accountSource) {
    }

    private record JournalKey(
            String accountCode,
            String accountName,
            String lineType,
            String sourceType,
            String sourceCode,
            String sourceName,
            String description) {
    }
}
