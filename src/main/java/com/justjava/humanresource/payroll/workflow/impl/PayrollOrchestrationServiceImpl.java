package com.justjava.humanresource.payroll.workflow.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;

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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    /* ============================================================
       INITIALIZE
       ============================================================ */

    @Override
    @Transactional
    public Long initializePayrollRun(
            Long employeeId,
            LocalDate payrollDate,
            String processInstanceId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeId));

        Long companyId = employee
                .getDepartment()
                .getCompany()
                .getId();

        payrollSetupService.validatePayrollSystemReadiness(payrollDate);
        payrollPeriodService.validatePayrollDate(companyId, payrollDate);

        Optional<PayrollRun> existingOpt =
                payrollRunRepository
                        .findTopByEmployeeIdAndPayrollDateOrderByVersionNumberDesc(
                                employeeId,
                                payrollDate
                        );

        if (existingOpt.isPresent()) {

            PayrollRun existing = existingOpt.get();

            if (existing.getStatus() == PayrollRunStatus.POSTED) {

                if (!payrollPeriodService
                        .isPayrollDateInOpenPeriod(companyId, payrollDate)) {

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

        PayrollRun run = new PayrollRun();
        run.setEmployee(employee);
        run.setPayrollDate(payrollDate);
        run.setStatus(PayrollRunStatus.IN_PROGRESS);
        run.setFlowableProcessInstanceId(processInstanceId);
        run.setRunType(PayrollRunType.ORIGINAL);
        run.setVersionNumber(1);
        run.setGrossPay(BigDecimal.ZERO);
        run.setNetPay(BigDecimal.ZERO);

        PayrollPeriod open =
                payrollPeriodService.getOpenPeriod(companyId);

        run.setPeriodStart(open.getPeriodStart());
        run.setPeriodEnd(open.getPeriodEnd());

        return payrollRunRepository.save(run).getId();
    }

    /* ============================================================
       CALCULATE EARNINGS
       ============================================================ */

    @Override
    @Transactional
    public void calculateEarnings(Long payrollRunId) {

        PayrollRun run = getEditableRun(payrollRunId);

        Employee employee = run.getEmployee();
        LocalDate payrollDate = run.getPayrollDate();

        EmployeePositionHistory position =
                employeePositionHistoryService
                        .getCurrentPosition(employee.getId());

        JobStep jobStep = position.getJobStep();
        PayGroup payGroup = position.getPayGroup();

        payrollLineItemRepository
                .deleteByPayrollRunIdAndComponentType(
                        payrollRunId,
                        PayComponentType.EARNING
                );

        BigDecimal grossPay = BigDecimal.ZERO;
        BigDecimal basicSalary = jobStep.getBasicSalary();

        grossPay = grossPay.add(basicSalary);

        saveLine(run, employee, "BASIC",
                "Basic Salary",
                basicSalary,
                true,
                PayComponentType.EARNING);

        BigDecimal taxableIncome = basicSalary;

        ResolvedPayComponents resolved =
                payGroupResolutionService.resolve(
                        payGroup,
                        employee,
                        payrollDate
                );

        for (Allowance allowance : resolved.getAllowances()) {

            BigDecimal amount = computeAllowanceAmount(
                    allowance,
                    basicSalary,
                    grossPay,
                    taxableIncome
            );

            if (amount == null
                    || amount.compareTo(BigDecimal.ZERO) <= 0)
                continue;

            if (allowance.isProratable()) {
                amount = applyProration(
                        amount,
                        run.getPeriodStart(),
                        run.getPeriodEnd()
                );
            }

            amount = amount.setScale(2, RoundingMode.HALF_UP);

            saveLine(run, employee,
                    allowance.getCode(),
                    allowance.getName(),
                    amount,
                    allowance.isTaxable(),
                    PayComponentType.EARNING);

            grossPay = grossPay.add(amount);

            if (allowance.isTaxable())
                taxableIncome = taxableIncome.add(amount);
        }

        run.setGrossPay(grossPay.setScale(2, RoundingMode.HALF_UP));
        payrollRunRepository.save(run);
    }

    /* ============================================================
       APPLY DEDUCTIONS
       ============================================================ */

    @Override
    @Transactional
    public void applyStatutoryDeductions(Long payrollRunId) {

        PayrollRun run = getEditableRun(payrollRunId);
        Employee employee = run.getEmployee();

        payrollLineItemRepository
                .deleteByPayrollRunIdAndComponentType(
                        payrollRunId,
                        PayComponentType.DEDUCTION
                );

        BigDecimal totalDeductions = BigDecimal.ZERO;

        List<PayrollLineItem> taxableLines =
                payrollLineItemRepository
                        .findByPayrollRunIdAndComponentTypeAndTaxableTrue(
                                payrollRunId,
                                PayComponentType.EARNING
                        );

        BigDecimal taxableIncome =
                taxableLines.stream()
                        .map(PayrollLineItem::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paye =
                payeCalculatorService.calculateTax(taxableIncome)
                        .setScale(2, RoundingMode.HALF_UP);

        if (paye.compareTo(BigDecimal.ZERO) > 0) {
            saveLine(run, employee, "PAYE",
                    "PAYE Tax",
                    paye,
                    false,
                    PayComponentType.DEDUCTION);
            totalDeductions = totalDeductions.add(paye);
        }

        run.setTotalDeductions(totalDeductions);
        run.setNetPay(
                run.getGrossPay()
                        .subtract(totalDeductions)
                        .setScale(2, RoundingMode.HALF_UP)
        );

        payrollRunRepository.save(run);
    }

    /* ============================================================
       FINALIZE
       ============================================================ */

    @Override
    @Transactional
    public void finalizePayroll(Long payrollRunId) {

        PayrollRun run = payrollRunRepository.findById(payrollRunId)
                .orElseThrow();

        if (run.getStatus() == PayrollRunStatus.POSTED)
            throw new IllegalStateException("Already POSTED.");

        run.setStatus(PayrollRunStatus.POSTED);
        payrollRunRepository.save(run);
    }

    /* ============================================================
       INTERNAL UTILITIES
       ============================================================ */

    private PayrollRun getEditableRun(Long id) {

        PayrollRun run = payrollRunRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("PayrollRun", id));

        if (run.getStatus() == PayrollRunStatus.POSTED)
            throw new IllegalStateException(
                    "POSTED payroll cannot be modified.");

        return run;
    }

    private void saveLine(
            PayrollRun run,
            Employee employee,
            String code,
            String description,
            BigDecimal amount,
            boolean taxable,
            PayComponentType type) {

        PayrollLineItem line = new PayrollLineItem();
        line.setPayrollRun(run);
        line.setEmployee(employee);
        line.setComponentCode(code);
        line.setDescription(description);
        line.setAmount(amount);
        line.setTaxable(taxable);
        line.setComponentType(type);

        payrollLineItemRepository.save(line);
    }

    private BigDecimal computeAllowanceAmount(
            Allowance allowance,
            BigDecimal basic,
            BigDecimal gross,
            BigDecimal taxable) {

        switch (allowance.getCalculationType()) {

            case FIXED_AMOUNT:
                return allowance.getAmount();

            case PERCENTAGE_OF_BASIC:
                return basic.multiply(allowance.getPercentageRate())
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            case PERCENTAGE_OF_GROSS:
                return gross.multiply(allowance.getPercentageRate())
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            case PERCENTAGE_OF_TAXABLE:
                return taxable.multiply(allowance.getPercentageRate())
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            case FORMULA:
                return evaluateFormula(
                        allowance.getFormulaExpression(),
                        basic,
                        gross,
                        taxable
                );

            default:
                throw new IllegalStateException("Unsupported type");
        }
    }

    private BigDecimal evaluateFormula(
            String expression,
            BigDecimal basic,
            BigDecimal gross,
            BigDecimal taxable) {

        try {
            ScriptEngine engine =
                    new ScriptEngineManager()
                            .getEngineByName("JavaScript");

            engine.put("BASIC", basic);
            engine.put("GROSS", gross);
            engine.put("TAXABLE", taxable);

            Object result = engine.eval(expression);
            return new BigDecimal(result.toString());

        } catch (Exception e) {
            throw new IllegalStateException("Invalid formula: " + expression);
        }
    }

    private BigDecimal applyProration(
            BigDecimal amount,
            LocalDate periodStart,
            LocalDate periodEnd) {

        long totalDays =
                ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;

        if (totalDays <= 0) return amount;

        return amount
                .divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(totalDays));
    }

    private PayrollRun createAmendmentRun(PayrollRun original) {

        PayrollRun amendment = new PayrollRun();
        amendment.setEmployee(original.getEmployee());
        amendment.setPayrollDate(original.getPayrollDate());
        amendment.setStatus(PayrollRunStatus.IN_PROGRESS);
        amendment.setRunType(PayrollRunType.AMENDMENT);
        amendment.setParentRun(original);
        amendment.setVersionNumber(original.getVersionNumber() + 1);
        amendment.setPeriodStart(original.getPeriodStart());
        amendment.setPeriodEnd(original.getPeriodEnd());
        amendment.setGrossPay(BigDecimal.ZERO);
        amendment.setNetPay(BigDecimal.ZERO);

        return payrollRunRepository.save(amendment);
    }
}