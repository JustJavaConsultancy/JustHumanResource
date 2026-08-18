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
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import com.justjava.humanresource.payroll.statutory.repositories.PensionSchemeRepository;
import lombok.RequiredArgsConstructor;
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
public class PayrollJournalService {

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

        Map<JournalKey, BigDecimal> grouped = new LinkedHashMap<>();

        for (PayrollRun run : runs) {
            List<PayrollLineItem> lineItems = payrollLineItemRepository.findByPayrollRunId(run.getId());
            for (PayrollLineItem line : lineItems) {
                if (line.isOutOfPayroll() || line.getAmount() == null
                        || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                for (JournalLine journalLine : resolveJournalLines(line, allowances, deductions, pensionScheme, settings)) {
                    grouped.merge(journalLine.key(), line.getAmount(), BigDecimal::add);
                }
            }
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

        BigDecimal totalDebit = grouped.entrySet().stream()
                .filter(entry -> entry.getKey().lineType().equals("DEBIT"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = grouped.entrySet().stream()
                .filter(entry -> entry.getKey().lineType().equals("CREDIT"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalStateException(
                    "Generated payroll journal is unbalanced. Debit: "
                            + totalDebit + ", Credit: " + totalCredit
            );
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

        if (line.getComponentType() == PayComponentType.EARNING) {
            Allowance allowance = allowances.get(line.getComponentCode());
            AccountRef account = allowance == null
                    ? new AccountRef(settings.getDefaultEarningExpenseAccountCode(), settings.getDefaultEarningExpenseAccountName())
                    : resolveAccount(
                            allowance.getExpenseAccountCode(),
                            allowance.getExpenseAccountName(),
                            settings.getDefaultEarningExpenseAccountCode(),
                            settings.getDefaultEarningExpenseAccountName(),
                            "earning " + allowance.getCode()
                    );

            return List.of(debit(account, line, "Payroll earning expense"));
        }

        if (line.getComponentType() == PayComponentType.DEDUCTION) {
            AccountRef account;
            if ("PAYE".equalsIgnoreCase(line.getComponentCode())) {
                account = new AccountRef(settings.getPayePayableAccountCode(), settings.getPayePayableAccountName());
            } else if ("PENSION_EMP".equalsIgnoreCase(line.getComponentCode())) {
                account = pensionScheme
                        .map(scheme -> resolveAccount(
                                scheme.getEmployeePayableAccountCode(),
                                scheme.getEmployeePayableAccountName(),
                                settings.getPensionPayableAccountCode(),
                                settings.getPensionPayableAccountName(),
                                "employee pension payable"
                        ))
                        .orElse(new AccountRef(settings.getPensionPayableAccountCode(), settings.getPensionPayableAccountName()));
            } else {
                Deduction deduction = deductions.get(line.getComponentCode());
                account = deduction == null
                        ? new AccountRef(settings.getDefaultDeductionPayableAccountCode(), settings.getDefaultDeductionPayableAccountName())
                        : resolveAccount(
                                deduction.getPayableAccountCode(),
                                deduction.getPayableAccountName(),
                                settings.getDefaultDeductionPayableAccountCode(),
                                settings.getDefaultDeductionPayableAccountName(),
                                "deduction " + deduction.getCode()
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
                            "employer pension expense"
                    ))
                    .orElse(new AccountRef(settings.getEmployerPensionExpenseAccountCode(), settings.getEmployerPensionExpenseAccountName()));

            AccountRef payable = pensionScheme
                    .map(scheme -> resolveAccount(
                            scheme.getEmployerPayableAccountCode(),
                            scheme.getEmployerPayableAccountName(),
                            settings.getEmployerPensionPayableAccountCode(),
                            settings.getEmployerPensionPayableAccountName(),
                            "employer pension payable"
                    ))
                    .orElse(new AccountRef(settings.getEmployerPensionPayableAccountCode(), settings.getEmployerPensionPayableAccountName()));

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
        ));
    }

    private AccountRef resolveAccount(
            String accountCode,
            String accountName,
            String defaultCode,
            String defaultName,
            String label) {

        String code = hasText(accountCode) ? accountCode.trim() : defaultCode;
        String name = hasText(accountName) ? accountName.trim() : defaultName;
        if (!hasText(code) || !hasText(name)) {
            throw new IllegalStateException("Missing account mapping for " + label + ".");
        }
        return new AccountRef(code, name);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private record AccountRef(String code, String name) {
    }

    private record JournalLine(JournalKey key) {
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
