package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.dto.HistoricalPayrollAdjustmentCommand;
import com.justjava.humanresource.payroll.dto.HistoricalPayrollAdjustmentLineCommand;
import com.justjava.humanresource.payroll.dto.HistoricalPayrollPayItemDTO;
import com.justjava.humanresource.payroll.dto.PayrollLineItemDifferenceDTO;
import com.justjava.humanresource.payroll.dto.PayrollOriginalVsAdjustedDTO;
import com.justjava.humanresource.payroll.dto.PayrollVersionHistoryDTO;
import com.justjava.humanresource.payroll.entity.PaySlip;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.enums.PayrollRunType;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.HistoricalPayrollAdjustmentService;
import com.justjava.humanresource.payroll.service.PayrollAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HistoricalPayrollAdjustmentServiceImpl implements HistoricalPayrollAdjustmentService {

    private static final String RESIDUAL_COMPONENT_CODE = "RESIDUAL";
    private static final String RESIDUAL_DESCRIPTION = "Residual Adjustment";

    private final EmployeeRepository employeeRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollLineItemRepository payrollLineItemRepository;
    private final PaySlipRepository paySlipRepository;
    private final PayrollAuditService payrollAuditService;

    @Override
    @Transactional(readOnly = true)
    public PayrollOriginalVsAdjustedDTO previewAdjustment(HistoricalPayrollAdjustmentCommand command) {
        String reason = requireReason(command.getReason());
        PayrollPeriod period = validateClosedPeriod(command.getPeriodId());
        Employee employee = validateEmployeeInCompany(command.getEmployeeId(), period.getCompanyId());
        PayrollRun latest = getLatestPostedRun(employee.getId(), period);
        List<PayrollLineItem> proposedLines = buildProposedLines(latest, command);
        PayrollRun proposed = buildVirtualRun(latest, proposedLines, latest.getVersionNumber() + 1, reason);

        return compareRuns(latest, proposed, proposedLines);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoricalPayrollPayItemDTO> getAdjustablePayItems(Long employeeId, Long periodId) {
        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod"));
        Employee employee = validateEmployeeInCompany(employeeId, period.getCompanyId());
        PayrollRun latest = getLatestPostedRun(employee.getId(), period);

        return payrollLineItemRepository.findByPayrollRunId(latest.getId())
                .stream()
                .sorted(Comparator
                        .comparing(PayrollLineItem::getComponentType)
                        .thenComparing(PayrollLineItem::getComponentCode))
                .map(this::mapPayItem)
                .toList();
    }

    @Override
    @Transactional
    public PayrollVersionHistoryDTO createPostedAmendment(HistoricalPayrollAdjustmentCommand command) {
        String reason = requireReason(command.getReason());
        PayrollPeriod period = validateClosedPeriod(command.getPeriodId());
        Employee employee = validateEmployeeInCompany(command.getEmployeeId(), period.getCompanyId());
        PayrollRun latest = getLatestPostedRun(employee.getId(), period);
        Integer maxVersion = payrollRunRepository
                .findMaxVersionForEmployeeAndPeriod(employee.getId(), period.getPeriodStart(), period.getPeriodEnd());
        int nextVersion = (maxVersion == null ? latest.getVersionNumber() : maxVersion) + 1;

        PayrollRun amendment = new PayrollRun();
        amendment.setEmployee(employee);
        amendment.setPayrollDate(latest.getPayrollDate());
        amendment.setPeriodStart(latest.getPeriodStart());
        amendment.setPeriodEnd(latest.getPeriodEnd());
        amendment.setStatus(PayrollRunStatus.POSTED);
        amendment.setFlowableProcessInstanceId(latest.getFlowableProcessInstanceId());
        amendment.setFlowableBusinessKey(latest.getFlowableBusinessKey());
        amendment.setRunType(PayrollRunType.AMENDMENT);
        amendment.setParentRun(latest);
        amendment.setVersionNumber(nextVersion);
        amendment.setAppliedTaxBandSummary(latest.getAppliedTaxBandSummary());
        amendment.setAppliedPensionSchemeName(latest.getAppliedPensionSchemeName());
        amendment.setPayrollYear(latest.getPayrollYear());
        amendment.setAmendmentReason(reason);
        amendment.setRetroEffectiveDate(latest.getPeriodStart());

        List<PayrollLineItem> proposedLines = buildProposedLines(latest, command);
        applyTotals(amendment, proposedLines);
        copyYtdSnapshot(amendment, latest);

        PayrollRun saved = payrollRunRepository.save(amendment);
        for (PayrollLineItem proposed : proposedLines) {
            payrollLineItemRepository.save(copyLineForRun(proposed, saved, employee));
        }

        createVersionedPaySlip(saved);

        payrollAuditService.log(
                "PayrollRun",
                saved.getId(),
                "HISTORICAL_PAYROLL_VERSION_CREATED",
                "Historical payroll amendment v" + nextVersion
                        + " created for employee " + employee.getId()
                        + " period " + period.getPeriodStart() + " to " + period.getPeriodEnd()
                        + ". Previous run: " + latest.getId()
                        + ". Reason: " + reason
        );

        return mapHistory(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollVersionHistoryDTO> getVersionHistory(Long employeeId, Long periodId) {
        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod"));
        return payrollRunRepository
                .findPostedVersionsForEmployeeAndPeriod(employeeId, period.getPeriodStart(), period.getPeriodEnd())
                .stream()
                .map(this::mapHistory)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollOriginalVsAdjustedDTO compareOriginalToLatest(Long employeeId, Long periodId) {
        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod"));
        PayrollRun original = payrollRunRepository
                .findOriginalPostedRunForEmployeeAndPeriod(employeeId, period.getPeriodStart(), period.getPeriodEnd())
                .orElseThrow(() -> new IllegalStateException("Original posted payroll run not found."));
        PayrollRun latest = getLatestPostedRun(employeeId, period);
        return compareRuns(original, latest, payrollLineItemRepository.findByPayrollRunId(latest.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollOriginalVsAdjustedDTO> compareOriginalToLatestForPeriod(Long companyId, Long periodId) {
        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod"));
        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException("Period does not belong to provided company.");
        }

        return payrollRunRepository
                .findOriginalPostedRunsForCompanyAndPeriod(companyId, period.getPeriodStart(), period.getPeriodEnd())
                .stream()
                .map(original -> compareOriginalToLatest(original.getEmployee().getId(), periodId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollOriginalVsAdjustedDTO compareVersions(
            Long employeeId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer originalVersion,
            Integer adjustedVersion) {

        List<PayrollRun> versions = payrollRunRepository
                .findPostedVersionsForEmployeeAndPeriod(employeeId, periodStart, periodEnd);

        PayrollRun original = versions.stream()
                .filter(run -> run.getVersionNumber().equals(originalVersion))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Original comparison version not found."));
        PayrollRun adjusted = versions.stream()
                .filter(run -> run.getVersionNumber().equals(adjustedVersion))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Adjusted comparison version not found."));

        return compareRuns(original, adjusted, payrollLineItemRepository.findByPayrollRunId(adjusted.getId()));
    }

    private PayrollPeriod validateClosedPeriod(Long periodId) {
        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod"));
        if (period.getStatus() != PayrollPeriodStatus.CLOSED) {
            throw new IllegalStateException("Historical payroll adjustment is only allowed for CLOSED periods.");
        }
        return period;
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("Amendment reason is required.");
        }
        return reason.trim();
    }

    private Employee validateEmployeeInCompany(Long employeeId, Long companyId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee"));
        if (employee.getDepartment() == null
                || employee.getDepartment().getCompany() == null
                || !employee.getDepartment().getCompany().getId().equals(companyId)) {
            throw new IllegalStateException("Employee does not belong to the period company.");
        }
        return employee;
    }

    private PayrollRun getLatestPostedRun(Long employeeId, PayrollPeriod period) {
        return payrollRunRepository
                .findLatestPostedRunForEmployeeAndPeriod(employeeId, period.getPeriodStart(), period.getPeriodEnd())
                .orElseThrow(() -> new IllegalStateException("Latest posted payroll run not found."));
    }

    private List<PayrollLineItem> buildProposedLines(PayrollRun baseRun, HistoricalPayrollAdjustmentCommand command) {
        Map<String, PayrollLineItem> linesByCode = new LinkedHashMap<>();
        for (PayrollLineItem existing : payrollLineItemRepository.findByPayrollRunId(baseRun.getId())) {
            linesByCode.put(existing.getComponentCode(), cloneDetachedLine(existing));
        }

        Set<String> seenCodes = new HashSet<>();
        for (HistoricalPayrollAdjustmentLineCommand change : command.getLines()) {
            PayrollLineItem line = linesByCode.get(change.getComponentCode());
            if (line == null) {
                throw new IllegalStateException("Component " + change.getComponentCode()
                        + " does not exist in the selected payroll version.");
            }
            if (!seenCodes.add(change.getComponentCode())) {
                throw new IllegalStateException("Component " + change.getComponentCode()
                        + " was selected more than once.");
            }

            line.setAmount(requireNonNegative(change.getCorrectedAmount(), change.getComponentCode()));

            linesByCode.put(line.getComponentCode(), line);
        }

        return new ArrayList<>(linesByCode.values());
    }

    private HistoricalPayrollPayItemDTO mapPayItem(PayrollLineItem line) {
        return HistoricalPayrollPayItemDTO.builder()
                .componentCode(line.getComponentCode())
                .description(line.getDescription())
                .componentType(line.getComponentType())
                .currentAmount(line.getAmount())
                .taxable(line.isTaxable())
                .pensionable(line.isPensionable())
                .taxRelief(line.isTaxRelief())
                .partOfGross(line.isPartOfGross())
                .outOfPayroll(line.isOutOfPayroll())
                .build();
    }

    private BigDecimal requireNonNegative(BigDecimal amount, String componentCode) {
        if (amount == null) {
            throw new IllegalStateException("Corrected amount is required for component " + componentCode + ".");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Corrected amount cannot be negative for component " + componentCode + ".");
        }
        return amount;
    }

    private PayrollRun buildVirtualRun(
            PayrollRun base,
            List<PayrollLineItem> proposedLines,
            Integer versionNumber,
            String reason) {
        PayrollRun run = new PayrollRun();
        run.setEmployee(base.getEmployee());
        run.setPayrollDate(base.getPayrollDate());
        run.setPeriodStart(base.getPeriodStart());
        run.setPeriodEnd(base.getPeriodEnd());
        run.setStatus(PayrollRunStatus.POSTED);
        run.setRunType(PayrollRunType.AMENDMENT);
        run.setParentRun(base);
        run.setVersionNumber(versionNumber);
        run.setAmendmentReason(reason);
        applyTotals(run, proposedLines);
        return run;
    }

    private PayrollLineItem cloneDetachedLine(PayrollLineItem source) {
        PayrollLineItem line = new PayrollLineItem();
        line.setEmployee(source.getEmployee());
        line.setComponentCode(source.getComponentCode());
        line.setDescription(source.getDescription());
        line.setAmount(source.getAmount());
        line.setTaxable(source.isTaxable());
        line.setPensionable(source.isPensionable());
        line.setTaxRelief(source.isTaxRelief());
        line.setPartOfGross(source.isPartOfGross());
        line.setOutOfPayroll(source.isOutOfPayroll());
        line.setComponentType(source.getComponentType());
        return line;
    }

    private PayrollLineItem copyLineForRun(PayrollLineItem source, PayrollRun run, Employee employee) {
        PayrollLineItem line = cloneDetachedLine(source);
        line.setPayrollRun(run);
        line.setEmployee(employee);
        return line;
    }

    private void applyTotals(PayrollRun run, List<PayrollLineItem> lines) {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal nonGross = BigDecimal.ZERO;
        BigDecimal grossDifference = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;

        for (PayrollLineItem line : lines) {
            if (isResidualAdjustment(line)) {
                grossDifference = grossDifference.add(line.getAmount());
                continue;
            }
            if (line.isOutOfPayroll()) {
                continue;
            }
            if (line.getComponentType() == PayComponentType.EARNING) {
                if (line.isPartOfGross()) {
                    gross = gross.add(line.getAmount());
                } else {
                    nonGross = nonGross.add(line.getAmount());
                }
            } else if (line.getComponentType() == PayComponentType.DEDUCTION) {
                deductions = deductions.add(line.getAmount());
            }
        }

        run.setGrossPay(gross);
        run.setNonGrossEarnings(nonGross);
        run.setGrossDifference(grossDifference);
        run.setTotalDeductions(deductions);
        run.setNetPay(gross.add(nonGross).subtract(deductions));
    }

    private boolean isResidualAdjustment(PayrollLineItem line) {
        return RESIDUAL_COMPONENT_CODE.equalsIgnoreCase(line.getComponentCode())
                || RESIDUAL_DESCRIPTION.equalsIgnoreCase(line.getDescription());
    }

    private void copyYtdSnapshot(PayrollRun amendment, PayrollRun latest) {
        amendment.setYtdGross(latest.getYtdGross());
        amendment.setYtdTaxable(latest.getYtdTaxable());
        amendment.setYtdDeductions(latest.getYtdDeductions());
        amendment.setYtdNet(latest.getYtdNet());
        amendment.setYtdPaye(latest.getYtdPaye());
    }

    private void createVersionedPaySlip(PayrollRun run) {
        if (paySlipRepository.existsByPayrollRunIdAndVersionNumber(run.getId(), run.getVersionNumber())) {
            return;
        }
        PaySlip slip = new PaySlip();
        slip.setPayrollRun(run);
        slip.setEmployee(run.getEmployee());
        slip.setPayDate(run.getPayrollDate());
        slip.setGrossPay(run.getGrossPay());
        slip.setTotalDeductions(run.getTotalDeductions());
        slip.setNetPay(run.getNetPay());
        slip.setVersionNumber(run.getVersionNumber());
        paySlipRepository.save(slip);
    }

    private PayrollOriginalVsAdjustedDTO compareRuns(
            PayrollRun original,
            PayrollRun adjusted,
            List<PayrollLineItem> adjustedLines) {
        List<PayrollLineItem> originalLines = original.getId() == null
                ? List.of()
                : payrollLineItemRepository.findByPayrollRunId(original.getId());
        List<PayrollLineItem> rightLines = adjusted.getId() == null
                ? adjustedLines
                : payrollLineItemRepository.findByPayrollRunId(adjusted.getId());

        Employee employee = original.getEmployee();
        return PayrollOriginalVsAdjustedDTO.builder()
                .employeeId(employee.getId())
                .employeeNumber(employee.getEmployeeNumber())
                .employeeName(employee.getFullName())
                .periodStart(original.getPeriodStart())
                .periodEnd(original.getPeriodEnd())
                .originalRunId(original.getId())
                .adjustedRunId(adjusted.getId())
                .originalVersion(original.getVersionNumber())
                .adjustedVersion(adjusted.getVersionNumber())
                .originalGrossPay(original.getGrossPay())
                .adjustedGrossPay(adjusted.getGrossPay())
                .grossDifference(adjusted.getGrossPay().subtract(original.getGrossPay()))
                .originalTotalDeductions(original.getTotalDeductions())
                .adjustedTotalDeductions(adjusted.getTotalDeductions())
                .deductionsDifference(adjusted.getTotalDeductions().subtract(original.getTotalDeductions()))
                .originalNetPay(original.getNetPay())
                .adjustedNetPay(adjusted.getNetPay())
                .netDifference(adjusted.getNetPay().subtract(original.getNetPay()))
                .latestAmendmentReason(adjusted.getAmendmentReason())
                .lineDifferences(compareLines(originalLines, rightLines))
                .build();
    }

    private List<PayrollLineItemDifferenceDTO> compareLines(
            List<PayrollLineItem> originalLines,
            List<PayrollLineItem> adjustedLines) {
        Map<String, PayrollLineItem> originals = mapByCode(originalLines);
        Map<String, PayrollLineItem> adjusted = mapByCode(adjustedLines);
        List<String> allCodes = new ArrayList<>();
        allCodes.addAll(originals.keySet());
        for (String code : adjusted.keySet()) {
            if (!allCodes.contains(code)) {
                allCodes.add(code);
            }
        }

        return allCodes.stream()
                .sorted(Comparator.naturalOrder())
                .map(code -> {
                    PayrollLineItem left = originals.get(code);
                    PayrollLineItem right = adjusted.get(code);
                    BigDecimal originalAmount = left != null ? left.getAmount() : BigDecimal.ZERO;
                    BigDecimal adjustedAmount = right != null ? right.getAmount() : BigDecimal.ZERO;
                    PayrollLineItem basis = right != null ? right : left;
                    return PayrollLineItemDifferenceDTO.builder()
                            .componentCode(code)
                            .description(basis.getDescription())
                            .componentType(basis.getComponentType())
                            .originalAmount(originalAmount)
                            .adjustedAmount(adjustedAmount)
                            .differenceAmount(adjustedAmount.subtract(originalAmount))
                            .taxable(basis.isTaxable())
                            .pensionable(basis.isPensionable())
                            .taxRelief(basis.isTaxRelief())
                            .partOfGross(basis.isPartOfGross())
                            .outOfPayroll(basis.isOutOfPayroll())
                            .build();
                })
                .toList();
    }

    private Map<String, PayrollLineItem> mapByCode(List<PayrollLineItem> lines) {
        Map<String, PayrollLineItem> mapped = new LinkedHashMap<>();
        for (PayrollLineItem line : lines) {
            mapped.put(line.getComponentCode(), line);
        }
        return mapped;
    }

    private PayrollVersionHistoryDTO mapHistory(PayrollRun run) {
        Employee employee = run.getEmployee();
        return PayrollVersionHistoryDTO.builder()
                .payrollRunId(run.getId())
                .employeeId(employee.getId())
                .employeeNumber(employee.getEmployeeNumber())
                .employeeName(employee.getFullName())
                .periodStart(run.getPeriodStart())
                .periodEnd(run.getPeriodEnd())
                .versionNumber(run.getVersionNumber())
                .runType(run.getRunType())
                .status(run.getStatus())
                .grossPay(run.getGrossPay())
                .totalDeductions(run.getTotalDeductions())
                .netPay(run.getNetPay())
                .parentRunId(Optional.ofNullable(run.getParentRun()).map(PayrollRun::getId).orElse(null))
                .amendmentReason(run.getAmendmentReason())
                .createdAt(run.getCreatedAt())
                .build();
    }
}
