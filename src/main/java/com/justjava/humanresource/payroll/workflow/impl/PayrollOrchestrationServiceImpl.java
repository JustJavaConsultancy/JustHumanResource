package com.justjava.humanresource.payroll.workflow.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.core.exception.InvalidOperationException;
import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.calculation.PayGroupResolutionService;
import com.justjava.humanresource.payroll.calculation.dto.ResolvedPayComponents;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.enums.PayrollRunType;
import com.justjava.humanresource.payroll.repositories.EmployeeAllowanceRepository;
import com.justjava.humanresource.payroll.repositories.PayGroupAllowanceRepository;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import com.justjava.humanresource.payroll.statutory.repositories.PensionSchemeRepository;
import com.justjava.humanresource.payroll.statutory.service.PayeCalculatorService;
import com.justjava.humanresource.payroll.statutory.service.PensionCalculatorService;
import com.justjava.humanresource.payroll.workflow.PayrollOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PayrollOrchestrationServiceImpl implements PayrollOrchestrationService {

    private final PayrollRunRepository payrollRunRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollSetupService payrollSetupService;
    private final PayrollLineItemRepository payrollLineItemRepository;
    private final PayGroupResolutionService payGroupResolutionService;
    private final PayeCalculatorService payeCalculatorService;
    private final PensionSchemeRepository pensionSchemeRepository;
    private final PensionCalculatorService pensionCalculatorService;
    private final EmployeePositionHistoryService employeePositionHistoryService;
    private final PayrollPeriodService payrollPeriodService;



    /* =========================
     * INITIALIZE
     * ========================= */

    @Override
    @Transactional
    public Long initializePayrollRun(
            Long employeeId,
            LocalDate payrollDate,
            String processInstanceId) {

        payrollSetupService.validatePayrollSystemReadiness(payrollDate);
        payrollPeriodService.validatePayrollDate(payrollDate);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeId));

        // 🔎 Get latest version run
        Optional<PayrollRun> existingOpt =
                payrollRunRepository
                        .findTopByEmployeeIdAndPayrollDateOrderByVersionNumberDesc(
                                employeeId,
                                payrollDate
                        );

        if (existingOpt.isPresent()) {

            PayrollRun existing = existingOpt.get();

            if (existing.getStatus() == PayrollRunStatus.POSTED) {

                // Allow amendment only if period open
                if (!payrollPeriodService
                        .isPayrollDateInOpenPeriod(payrollDate)) {

                    throw new IllegalStateException(
                            "Cannot amend payroll. Period is CLOSED."
                    );
                }

                PayrollRun amendment = createAmendmentRun(existing);
                amendment.setFlowableProcessInstanceId(processInstanceId);

                return amendment.getId();
            }

            if (existing.getStatus() == PayrollRunStatus.IN_PROGRESS) {
                return existing.getId();
            }
        }

        // Create fresh ORIGINAL run
        PayrollRun run = new PayrollRun();
        run.setEmployee(employee);
        run.setPayrollDate(payrollDate);
        run.setStatus(PayrollRunStatus.IN_PROGRESS);
        run.setFlowableProcessInstanceId(processInstanceId);
        run.setRunType(PayrollRunType.ORIGINAL);
        run.setVersionNumber(1);
        run.setGrossPay(BigDecimal.ZERO);
        run.setNetPay(BigDecimal.ZERO);

        return payrollRunRepository.save(run).getId();
    }

    /* =========================
     * CALCULATE EARNINGS
     * ========================= */

    @Override
    @Transactional
    public void calculateEarnings(Long payrollRunId) {

        PayrollRun run = getActiveRun(payrollRunId);
        ensureEditable(run);
        ensureStatus(run, PayrollRunStatus.IN_PROGRESS);

        Employee employee = run.getEmployee();
        LocalDate payrollDate = run.getPayrollDate();

    /* ============================================================
       1️⃣ Resolve Employee Position (Retro Safe)
       ============================================================ */

        EmployeePositionHistory position = employeePositionHistoryService.getCurrentPosition(employee.getId());

        JobStep jobStep = position.getJobStep();
        PayGroup payGroup = position.getPayGroup();

    /* ============================================================
       2️⃣ Idempotency – Remove Existing Earnings
       ============================================================ */

        payrollLineItemRepository.deleteByPayrollRunIdAndComponentType(
                payrollRunId,
                PayComponentType.EARNING
        );

        BigDecimal grossPay = BigDecimal.ZERO;

    /* ============================================================
       3️⃣ Basic Salary (From Position History)
       ============================================================ */

        BigDecimal basicSalary = jobStep.getBasicSalary();

        PayrollLineItem basicLine = new PayrollLineItem();
        basicLine.setPayrollRun(run);
        basicLine.setEmployee(employee);
        basicLine.setComponentType(PayComponentType.EARNING);
        basicLine.setComponentCode("BASIC");
        basicLine.setDescription("Basic Salary");
        basicLine.setAmount(basicSalary);
        basicLine.setTaxable(true);

        payrollLineItemRepository.save(basicLine);

        grossPay = grossPay.add(basicSalary);

    /* ============================================================
       4️⃣ Resolve Allowances (Hierarchy + Overrides)
       ============================================================ */

        ResolvedPayComponents resolved =
                payGroupResolutionService.resolve(
                        payGroup,
                        employee,
                        payrollDate
                );

        for (Allowance allowance : resolved.getAllowances()) {

            if (allowance.getAmount() == null
                    || allowance.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            PayrollLineItem line = new PayrollLineItem();
            line.setPayrollRun(run);
            line.setEmployee(employee);
            line.setComponentType(PayComponentType.EARNING);
            line.setComponentCode(allowance.getCode());
            line.setDescription(allowance.getName());
            line.setAmount(allowance.getAmount());
            line.setTaxable(allowance.isTaxable());

            payrollLineItemRepository.save(line);

            grossPay = grossPay.add(allowance.getAmount());
        }

    /* ============================================================
       5️⃣ Persist Gross Snapshot
       ============================================================ */

        run.setGrossPay(grossPay);
        payrollRunRepository.save(run);
    }

    /* =========================
     * APPLY STATUTORY
     * ========================= */

    @Override
    @Transactional
    public void applyStatutoryDeductions(Long payrollRunId) {

        PayrollRun run = getActiveRun(payrollRunId);
        ensureStatus(run, PayrollRunStatus.IN_PROGRESS);

        ensureEditable(run);

        Employee employee = run.getEmployee();
        LocalDate payrollDate = run.getPayrollDate();

    /* ============================================================
       1️⃣ Idempotency: Remove existing deductions
       ============================================================ */

        payrollLineItemRepository.deleteByPayrollRunIdAndComponentType(
                payrollRunId,
                PayComponentType.DEDUCTION
        );

        BigDecimal totalDeductions = BigDecimal.ZERO;

    /* ============================================================
       2️⃣ Aggregate Taxable Earnings
       ============================================================ */

        List<PayrollLineItem> taxableEarnings =
                payrollLineItemRepository
                        .findByPayrollRunIdAndComponentTypeAndTaxableTrue(
                                payrollRunId,
                                PayComponentType.EARNING
                        );

        BigDecimal taxableIncome =
                taxableEarnings.stream()
                        .map(PayrollLineItem::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

    /* ============================================================
       3️⃣ PAYE Calculation
       ============================================================ */

        BigDecimal paye =
                payeCalculatorService.calculateTax(taxableIncome);

        if (paye.compareTo(BigDecimal.ZERO) > 0) {

            PayrollLineItem payeLine = new PayrollLineItem();
            payeLine.setPayrollRun(run);
            payeLine.setEmployee(employee);
            payeLine.setComponentType(PayComponentType.DEDUCTION);
            payeLine.setComponentCode("PAYE");
            payeLine.setDescription("PAYE Tax");
            payeLine.setAmount(paye);
            payeLine.setTaxable(false);

            payrollLineItemRepository.save(payeLine);

            totalDeductions = totalDeductions.add(paye);
        }

    /* ============================================================
       4️⃣ Pension Calculation (Employee Portion Only)
       ============================================================ */
        EmployeePositionHistory position =employeePositionHistoryService.getCurrentPosition(employee.getId());

        JobStep jobStep = position.getJobStep();
        List<PensionScheme> schemes =
                pensionSchemeRepository.findEffectiveSchemes(
                        payrollDate,
                        RecordStatus.ACTIVE
                );

        if (!schemes.isEmpty()) {

            PensionScheme scheme = schemes.get(0); // assume one active scheme

            BigDecimal pensionableAmount =
                    scheme.getPensionableOnBasicOnly()
                            ? jobStep.getBasicSalary()
                            : taxableIncome;

            if (scheme.getPensionableCap() != null) {
                pensionableAmount =
                        pensionableAmount.min(scheme.getPensionableCap());
            }

            BigDecimal employeePension =
                    pensionCalculatorService.calculateEmployeeContribution(
                            pensionableAmount,
                            scheme.getEmployeeRate()
                    );

            if (employeePension.compareTo(BigDecimal.ZERO) > 0) {

                PayrollLineItem pensionLine = new PayrollLineItem();
                pensionLine.setPayrollRun(run);
                pensionLine.setEmployee(employee);
                pensionLine.setComponentType(PayComponentType.DEDUCTION);
                pensionLine.setComponentCode("PENSION");
                pensionLine.setDescription("Pension Contribution");
                pensionLine.setAmount(employeePension);
                pensionLine.setTaxable(false);

                payrollLineItemRepository.save(pensionLine);

                totalDeductions = totalDeductions.add(employeePension);
            }
        }

    /* ============================================================
       5️⃣ Other Resolved Deductions
       ============================================================ */

        ResolvedPayComponents resolved =
                payGroupResolutionService.resolve(
                        position.getPayGroup(),
                        employee,
                        payrollDate
                );

        for (Deduction deduction : resolved.getDeductions()) {

            BigDecimal amount = deduction.getAmount();

            if (amount == null
                    || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            PayrollLineItem line = new PayrollLineItem();
            line.setPayrollRun(run);
            line.setEmployee(employee);
            line.setComponentType(PayComponentType.DEDUCTION);
            line.setComponentCode(deduction.getCode());
            line.setDescription(deduction.getName());
            line.setAmount(amount);
            line.setTaxable(false);

            payrollLineItemRepository.save(line);

            totalDeductions = totalDeductions.add(amount);
        }

    /* ============================================================
       6️⃣ Compute Net Pay
       ============================================================ */

        BigDecimal netPay =
                run.getGrossPay().subtract(totalDeductions);

        run.setTotalDeductions(totalDeductions);
        run.setNetPay(netPay);

        payrollRunRepository.save(run);
    }

    /* =========================
     * FINALIZE
     * ========================= */

    @Override
    @Transactional
    public void finalizePayroll(Long payrollRunId) {

        PayrollRun run = payrollRunRepository
                .findById(payrollRunId)
                .orElseThrow();

        if (run.getStatus() == PayrollRunStatus.POSTED) {
            throw new IllegalStateException(
                    "PayrollRun already POSTED."
            );
        }

        if (run.getGrossPay() == null
                || run.getNetPay() == null) {

            throw new IllegalStateException(
                    "Payroll cannot be posted without full calculation."
            );
        }

        run.setStatus(PayrollRunStatus.POSTED);
        payrollRunRepository.save(run);

    /* ============================================================
       CHECK IF PERIOD CAN BE CLOSED
       ============================================================ */

/*         PayrollPeriod current = payrollPeriodService.getCurrentOpenPeriod();

       long incomplete =
                payrollRunRepository.countByPayrollDateBetweenAndStatusNot(
                        current.getStartDate(),
                        current.getEndDate(),
                        PayrollRunStatus.POSTED
                );

        if (incomplete == 0) {
            // Automatically close and open next
            payrollPeriodService.closeCurrentPeriodAndOpenNext();
        }*/
    }

    /* =========================
     * INTERNAL SAFETY
     * ========================= */

    private PayrollRun getActiveRun(Long payrollRunId) {
        return payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("PayrollRun", payrollRunId));
    }

    private void ensureStatus(PayrollRun run, PayrollRunStatus expected) {
        if (!run.getStatus().equals(expected)) {
            throw new InvalidOperationException(
                    "Invalid payroll state transition. Current status: "
                            + run.getStatus()
            );
        }
    }
    private void ensureEditable(PayrollRun run) {
        if (run.getStatus() == PayrollRunStatus.POSTED) {
            throw new IllegalStateException(
                    "PayrollRun " + run.getId() +
                            " is POSTED and cannot be modified."
            );
        }
    }

    private PayrollRun createAmendmentRun(PayrollRun original) {

        PayrollRun amendment = new PayrollRun();

        amendment.setEmployee(original.getEmployee());
        amendment.setPayrollDate(original.getPayrollDate());
        amendment.setStatus(PayrollRunStatus.IN_PROGRESS);
        amendment.setRunType(PayrollRunType.AMENDMENT);
        amendment.setParentRun(original);

        amendment.setVersionNumber(original.getVersionNumber() + 1);

        amendment.setGrossPay(BigDecimal.ZERO);
        amendment.setNetPay(BigDecimal.ZERO);

        return payrollRunRepository.save(amendment);
    }

}
