package com.justjava.humanresource.integration.inbound.payroll.service;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.dto.IntegrationSnapshotContract;
import com.justjava.humanresource.integration.inbound.payroll.dto.IntegrationSnapshotLine;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationOutboxEvent;
import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshot;
import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshotLine;
import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;
import com.justjava.humanresource.integration.inbound.payroll.exception.IntegrationResourceNotFoundException;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationOutboxRepository;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.integration.inbound.payroll.util.JournalContentHash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.payroll.entity.PayrollJournalBatch;
import com.justjava.humanresource.payroll.entity.PayrollJournalEntry;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollJournalBatchRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




@Service
@RequiredArgsConstructor
public class PayrollJournalSnapshotService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private final IntegrationPayrollProperties properties;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollJournalBatchRepository batchRepository;
    private final PayrollJournalEntryRepository journalRepository;
    private final PayrollJournalSnapshotRepository snapshotRepository;
    private final IntegrationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void createForClosedPeriod(Long periodId, Long companyId) {
        if (!properties.isEnabled() || properties.getCompanyId() == null
                || !properties.getCompanyId().equals(companyId)) {
            return;
        }

        PayrollPeriod period = periodRepository.findByIdForUpdate(periodId)
                .orElseThrow(() -> new IllegalStateException("Closed payroll period was not found."));
        if (!companyId.equals(period.getCompanyId()) || period.getStatus() != PayrollPeriodStatus.CLOSED) {
            throw new IllegalStateException("Only a closed period for the configured company can be snapshotted.");
        }
        if (snapshotRepository.findByPayrollPeriodIdAndStatusNot(periodId, SnapshotStatus.VOID).isPresent()) {
            return;
        }

        PayrollJournalBatch batch = batchRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId)
                .orElseThrow(() -> new IllegalStateException("Cannot close for accounting: journal batch is missing."));
        if (!batch.isBalanced()
                || batch.getTotalDebit() == null
                || batch.getTotalCredit() == null
                || batch.getTotalDebit().compareTo(batch.getTotalCredit()) != 0) {
            throw new IllegalStateException("Cannot close for accounting: journal batch is not balanced.");
        }
        if (!period.getPeriodEnd().equals(batch.getPeriodEnd())) {
            throw new IllegalStateException("Cannot close for accounting: journal batch period end does not match.");
        }

        List<JournalLine> lines = prepareLines(journalRepository.findByCompanyAndPeriod(companyId, periodId));
        if (lines.size() > 200) {
            lines = aggregate(lines);
        }
        validateLines(lines, batch);

        String reference = "PAY-" + period.getPeriodEnd().format(MONTH) + "-" + periodId;
        String narration = "Payroll " + period.getPeriodEnd().format(MONTH);
        String currency = properties.getCurrency();
        List<JournalLine> orderedLines = lines.stream().sorted(LINE_ORDER).toList();
        String hash = JournalContentHash.calculate(
                reference,
                "ORIGINAL",
                periodId.toString(),
                period.getPeriodStart().toString(),
                period.getPeriodEnd().toString(),
                currency,
                narration,
                orderedLines.stream().map(line -> new JournalContentHash.Line(
                        line.accountCode(), line.accountName(), line.lineType(),
                        line.debit(), line.credit(), null)).toList());

        PayrollJournalSnapshot snapshot = new PayrollJournalSnapshot();
        snapshot.setJournalReference(reference);
        snapshot.setActiveKey(reference);
        snapshot.setCompanyId(companyId);
        snapshot.setPayrollPeriodId(periodId);
        snapshot.setPeriodStart(period.getPeriodStart());
        snapshot.setPeriodEnd(period.getPeriodEnd());
        snapshot.setCurrency(currency);
        snapshot.setRevision("ORIGINAL");
        snapshot.setHeaderNarration(narration);
        snapshot.setTotalDebit(batch.getTotalDebit().setScale(2));
        snapshot.setTotalCredit(batch.getTotalCredit().setScale(2));
        snapshot.setLineCount(orderedLines.size());
        snapshot.setContentHash(hash);
        snapshot.setStatus(SnapshotStatus.PENDING_PUBLISH);
        snapshot.setStatusChangedAt(LocalDateTime.now());
        snapshot.setCreatedAt(LocalDateTime.now());
        List<PayrollJournalSnapshotLine> snapshotLines = new ArrayList<>();
        for (int i = 0; i < orderedLines.size(); i++) {
            JournalLine line = orderedLines.get(i);
            PayrollJournalSnapshotLine snapshotLine = new PayrollJournalSnapshotLine();
            snapshotLine.setSnapshot(snapshot);
            snapshotLine.setLineNumber(i + 1);
            snapshotLine.setAccountCode(line.accountCode());
            snapshotLine.setAccountName(line.accountName());
            snapshotLine.setLineType(line.lineType());
            snapshotLine.setDebitAmount(line.debit());
            snapshotLine.setCreditAmount(line.credit());
            snapshotLine.setNarration(null);
            snapshotLines.add(snapshotLine);
        }
        snapshot.setLines(snapshotLines);
        snapshotRepository.saveAndFlush(snapshot);

        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        PayrollEventEnvelope envelope = new PayrollEventEnvelope(
                eventId.toString(),
                "payroll.finalised",
                1,
                "JUSTHR",
                companyId.toString(),
                Instant.now().toString(),
                correlationId.toString(),
                reference,
                new PayrollEventPayload(
                        reference,
                        "ORIGINAL",
                        periodId.toString(),
                        period.getPeriodStart().toString(),
                        period.getPeriodEnd().toString(),
                        currency,
                        orderedLines.size(),
                        batch.getTotalDebit().setScale(2),
                        batch.getTotalCredit().setScale(2),
                        hash));
        IntegrationOutboxEvent outbox = new IntegrationOutboxEvent();
        outbox.setEventId(eventId);
        outbox.setEventType("payroll.finalised");
        outbox.setEventVersion(1);
        outbox.setAggregateId(reference);
        outbox.setRoutingKey(properties.getRoutingKey());
        outbox.setExchangeName(properties.getExchange());
        outbox.setPayloadJson(serialize(envelope));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(LocalDateTime.now());
        outbox.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outbox);
    }

    @Transactional(readOnly = true)
    public IntegrationSnapshotContract findReadable(String reference) {
        if (!properties.isEnabled()) {
            throw new IntegrationResourceNotFoundException();
        }
        PayrollJournalSnapshot snapshot = snapshotRepository.findByJournalReferenceAndStatusNot(reference, SnapshotStatus.VOID)
                .orElseThrow(() -> new IntegrationResourceNotFoundException());
        List<IntegrationSnapshotLine> lines = snapshot.getLines().stream()
                .map(line -> new IntegrationSnapshotLine(
                        line.getAccountCode(), line.getAccountName(), line.getLineType(),
                        line.getDebitAmount(), line.getCreditAmount(), line.getNarration()))
                .toList();
        return new IntegrationSnapshotContract(
                "JUSTHR", snapshot.getJournalReference(), snapshot.getRevision(),
                snapshot.getPayrollPeriodId().toString(), snapshot.getPeriodStart(),
                snapshot.getPeriodEnd(), snapshot.getCurrency(), snapshot.getHeaderNarration(),
                lines, snapshot.getStatus(), snapshot.getContentHash());
    }

    private List<JournalLine> prepareLines(List<PayrollJournalEntry> source) {
        List<JournalLine> result = new ArrayList<>();
        for (PayrollJournalEntry entry : source) {
            BigDecimal debit = requireAmount(entry.getDebitAmount());
            BigDecimal credit = requireAmount(entry.getCreditAmount());
            validateAmount(debit);
            validateAmount(credit);
            if (debit.signum() == 0 && credit.signum() == 0) {
                continue;
            }
            requireAccount(entry);
            if (debit.signum() > 0 && credit.signum() > 0) {
                result.add(new JournalLine(entry.getAccountCode(), entry.getAccountName(), "DEBIT",
                        debit.setScale(2), ZERO, null));
                result.add(new JournalLine(entry.getAccountCode(), entry.getAccountName(), "CREDIT",
                        ZERO, credit.setScale(2), null));
            } else {
                String type = entry.getLineType();
                if (type == null || type.isBlank()) {
                    type = debit.signum() > 0 ? "DEBIT" : "CREDIT";
                }
                result.add(new JournalLine(entry.getAccountCode(), entry.getAccountName(), type,
                        debit.setScale(2), credit.setScale(2), null));
            }
        }
        return result;
    }

    private List<JournalLine> aggregate(List<JournalLine> lines) {
        Map<AggregateKey, BigDecimal> amounts = new LinkedHashMap<>();
        for (JournalLine line : lines) {
            boolean debitSide = line.debit().signum() > 0;
            AggregateKey key = new AggregateKey(line.accountCode(), line.accountName(), line.lineType(), debitSide);
            amounts.merge(key, debitSide ? line.debit() : line.credit(), BigDecimal::add);
        }
        List<JournalLine> result = new ArrayList<>();
        amounts.forEach((key, amount) -> result.add(new JournalLine(
                key.accountCode(), key.accountName(), key.lineType(),
                key.debitSide() ? amount.setScale(2) : ZERO,
                key.debitSide() ? ZERO : amount.setScale(2),
                null)));
        return result;
    }

    private void validateLines(List<JournalLine> lines, PayrollJournalBatch batch) {
        if (lines.size() < 2) {
            throw new IllegalStateException("Cannot close for accounting: journal must contain at least two non-zero lines.");
        }
        if (lines.size() > 200) {
            throw new IllegalStateException("Cannot close for accounting: journal exceeds the 200-line limit after aggregation.");
        }
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalLine line : lines) {
            if (line.accountCode() == null || line.accountCode().isBlank()
                    || line.accountName() == null || line.accountName().isBlank()) {
                throw new IllegalStateException("Cannot close for accounting: every journal line needs an account code and name.");
            }
            debit = debit.add(line.debit());
            credit = credit.add(line.credit());
        }
        if (debit.compareTo(credit) != 0
                || debit.compareTo(batch.getTotalDebit()) != 0
                || credit.compareTo(batch.getTotalCredit()) != 0) {
            throw new IllegalStateException("Cannot close for accounting: snapshot journal totals do not balance to the batch.");
        }
    }

    private static void requireAccount(PayrollJournalEntry entry) {
        if (entry.getAccountCode() == null || entry.getAccountCode().isBlank()
                || entry.getAccountName() == null || entry.getAccountName().isBlank()) {
            throw new IllegalStateException("Cannot close for accounting: every journal line needs an account code and name.");
        }
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalStateException("Cannot close for accounting: journal line amount is missing.");
        }
        return amount;
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalStateException("Cannot close for accounting: journal amounts cannot be negative.");
        }
        if (amount.stripTrailingZeros().scale() > 2 || amount.precision() > 19) {
            throw new IllegalStateException("Cannot close for accounting: journal amounts must fit precision 19, scale 2.");
        }
    }

    private String serialize(PayrollEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot close for accounting: outbox event could not be serialized.", exception);
        }
    }

    private static final Comparator<JournalLine> LINE_ORDER =
            Comparator.comparing(JournalLine::accountCode, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(JournalLine::lineType, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(JournalLine::debit, BigDecimal::compareTo)
                    .thenComparing(JournalLine::credit, BigDecimal::compareTo)
                    .thenComparing(JournalLine::accountName, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(JournalLine::narration, Comparator.nullsFirst(String::compareTo));

    private record JournalLine(
            String accountCode, String accountName, String lineType,
            BigDecimal debit, BigDecimal credit, String narration) {
    }

    private record AggregateKey(String accountCode, String accountName, String lineType, boolean debitSide) {
    }

    private record PayrollEventEnvelope(
            String eventId, String eventType, int eventVersion, String sourceApplication,
            String sourceOrganisationId, String occurredAt, String correlationId,
            String aggregateId, PayrollEventPayload payload) {
    }

    private record PayrollEventPayload(
            String journalReference, String revision, String payrollPeriodId,
            String periodStart, String periodEnd, String currency, int lineCount,
            BigDecimal totalDebit, BigDecimal totalCredit, String contentHash) {
    }

}
