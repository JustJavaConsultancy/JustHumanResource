package com.justjava.humanresource.employeeexit.service;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.employeeexit.dto.ExitPayrollImpactLine;
import com.justjava.humanresource.employeeexit.dto.ExitPayrollImpactPreview;
import com.justjava.humanresource.employeeexit.entity.ExitSettlement;
import com.justjava.humanresource.employeeexit.entity.ExitSettlementLine;
import com.justjava.humanresource.employeeexit.entity.ExitSettlementPayrollLineMapping;
import com.justjava.humanresource.employeeexit.entity.ExitSettlementPayrollPosting;
import com.justjava.humanresource.employeeexit.enums.SettlementStatus;
import com.justjava.humanresource.employeeexit.repository.EmployeeExitCaseRepository;
import com.justjava.humanresource.employeeexit.repository.ExitSettlementLineRepository;
import com.justjava.humanresource.employeeexit.repository.ExitSettlementPayrollLineMappingRepository;
import com.justjava.humanresource.employeeexit.repository.ExitSettlementPayrollPostingRepository;
import com.justjava.humanresource.employeeexit.repository.ExitSettlementRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollAccountingChangeDetector;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.enums.PayrollRunType;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@RequiredArgsConstructor
public class ExitPayrollImpactService {
    private final ExitSettlementRepository settlements;
    private final ExitSettlementLineRepository settlementLines;
    private final ExitSettlementPayrollPostingRepository postings;
    private final ExitSettlementPayrollLineMappingRepository mappings;
    private final EmployeeExitCaseRepository exits;
    private final EmployeeRepository employees;
    private final PayrollPeriodRepository periods;
    private final PayrollRunRepository runs;
    private final PayrollLineItemRepository payrollLines;
    private final EmployeeExitActivityService activity;
    private final PayrollAccountingChangeDetector payrollAccountingChangeDetector;

    @Transactional(readOnly = true)
    public ExitPayrollImpactPreview preview(Long settlementId) {
        ExitSettlement settlement = requireApproved(settlementId);
        var exitCase = exits.findById(settlement.getExitCaseId()).orElseThrow();
        List<ExitPayrollImpactLine> lines = settlementLines
                .findBySettlementIdOrderById(settlement.getId())
                .stream()
                .map(line -> new ExitPayrollImpactLine(
                        line.getId(), code(line), line.getDescription(),
                        line.isEarning() ? PayComponentType.EARNING : PayComponentType.DEDUCTION,
                        line.getAmount(), line.isTaxable()))
                .toList();
        return new ExitPayrollImpactPreview(
                settlement.getId(), exitCase.getEmployeeId(),
                settlement.getGrossEarnings(), settlement.getTotalDeductions(),
                settlement.getNetSettlement(), lines);
    }

    @Transactional
    public ExitSettlementPayrollPosting postApprovedSettlement(Long settlementId, Long periodId, Long actor) {
        ExitSettlement settlement = requireApproved(settlementId);
        String postingKey = "EXIT-" + settlement.getExitCaseId() + "-SETTLEMENT-"
                + settlement.getSettlementVersion() + "-" + settlement.getCalculationFingerprint();
        var existing = postings.findByPostingKey(postingKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        var exitCase = exits.findById(settlement.getExitCaseId()).orElseThrow();
        PayrollPeriod period = periods.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll period not found."));
        if (!period.getCompanyId().equals(exitCase.getCompanyId())) {
            throw new IllegalStateException("Payroll period belongs to a different company.");
        }
        PayrollRun base = runs.findLatestPostedRunForEmployeeAndPeriod(
                        exitCase.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd())
                .orElseThrow(() -> new IllegalStateException(
                        "A posted payroll run is required before exit settlement posting."));

        PayrollRun amendment = new PayrollRun();
        BeanUtils.copyProperties(base, amendment, "id", "createdAt", "updatedAt", "version");
        amendment.setParentRun(base);
        amendment.setRunType(PayrollRunType.AMENDMENT);
        amendment.setVersionNumber(Optional.ofNullable(runs.findMaxVersionForEmployeeAndPeriod(
                exitCase.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd()))
                .orElse(base.getVersionNumber()) + 1);
        amendment.setAmendmentReason("Employee exit settlement " + settlement.getId());
        PayrollRun saved = runs.saveAndFlush(amendment);

        for (PayrollLineItem old : payrollLines.findByPayrollRunId(base.getId())) {
            payrollLines.save(copy(old, saved));
        }
        ExitSettlementPayrollPosting posting = new ExitSettlementPayrollPosting();
        posting.setExitCaseId(exitCase.getId());
        posting.setSettlementId(settlement.getId());
        posting.setPayrollRunId(saved.getId());
        posting.setPayrollPeriodId(periodId);
        posting.setPostingKey(postingKey);
        posting.setPostedByEmployeeId(actor);
        posting.setPostedAt(LocalDateTime.now());
        posting = postings.saveAndFlush(posting);

        for (ExitSettlementLine line : settlementLines.findBySettlementIdOrderById(settlement.getId())) {
            PayrollLineItem item = new PayrollLineItem();
            item.setPayrollRun(saved);
            item.setEmployee(employees.findById(exitCase.getEmployeeId()).orElseThrow());
            item.setComponentType(line.isEarning() ? PayComponentType.EARNING : PayComponentType.DEDUCTION);
            item.setComponentCode(code(line));
            item.setDescription(line.getDescription());
            item.setAmount(line.getAmount());
            item.setTaxable(line.isTaxable());
            item.setPensionable(false);
            item.setPartOfGross(line.isEarning());
            item = payrollLines.save(item);

            ExitSettlementPayrollLineMapping mapping = new ExitSettlementPayrollLineMapping();
            mapping.setPostingId(posting.getId());
            mapping.setSettlementLineId(line.getId());
            mapping.setPayrollLineItemId(item.getId());
            mapping.setSourceReference("EXIT_SETTLEMENT_LINE_" + line.getId());
            mappings.save(mapping);
        }

        saved.setGrossPay(base.getGrossPay().add(settlement.getGrossEarnings()));
        saved.setTotalDeductions(base.getTotalDeductions().add(settlement.getTotalDeductions()));
        saved.setNetPay(base.getNetPay().add(settlement.getNetSettlement()));
        runs.save(saved);
        payrollAccountingChangeDetector.detect(
                saved.getId(), period.getCompanyId(), saved.getPeriodStart(), saved.getPeriodEnd());

        settlement.setStatus(SettlementStatus.POSTED);
        settlement.setPayrollRunId(saved.getId());
        settlements.save(settlement);
        activity.record(exitCase.getId(), "SETTLEMENT_POSTED",
                "Settlement posted to payroll run " + saved.getId(), actor, null);
        return posting;
    }

    private ExitSettlement requireApproved(Long id) {
        ExitSettlement settlement = settlements.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found."));
        if (settlement.getStatus() != SettlementStatus.APPROVED
                && settlement.getStatus() != SettlementStatus.POSTED) {
            throw new IllegalStateException("Only an approved settlement can be posted.");
        }
        return settlement;
    }

    private String code(ExitSettlementLine line) {
        return "EXIT_" + line.getLineType().name();
    }

    private PayrollLineItem copy(PayrollLineItem old, PayrollRun run) {
        PayrollLineItem copy = new PayrollLineItem();
        BeanUtils.copyProperties(old, copy, "id", "createdAt", "updatedAt", "version", "payrollRun");
        copy.setPayrollRun(run);
        return copy;
    }
}
