package com.justjava.humanresource.payroll.service.impl;


import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.payroll.entity.PaySlip;
import com.justjava.humanresource.payroll.entity.PaySlipDTO;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PaySlipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaySlipServiceImpl implements PaySlipService {

    private final PayrollRunRepository payrollRunRepository;
    private final PaySlipRepository paySlipRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;

    /* ============================================================
       GENERATE PAYSLIP (IDEMPOTENT + POSTED ONLY)
       ============================================================ */

    @Override
    @Transactional
    public void generatePaySlip(Long payrollRunId) {

        PayrollRun run = payrollRunRepository
                .findById(payrollRunId)
                .orElseThrow(() ->
                        new IllegalStateException("PayrollRun not found."));

        if (run.getStatus() != PayrollRunStatus.POSTED) {
            throw new IllegalStateException(
                    "Payslip can only be generated for POSTED payroll run.");
        }

        // Prevent duplicate payslip for same run version
        boolean exists =
                paySlipRepository.existsByPayrollRunIdAndVersionNumber(
                        payrollRunId,
                        run.getVersionNumber()
                );

        if (exists) {
            return; // idempotent behavior
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

    /* ============================================================
       GET CURRENT PERIOD PAYSLIPS (OPEN OR LOCKED)
       ============================================================ */

    @Override
    public List<PaySlipDTO> getCurrentPeriodPaySlips(Long companyId) {

        PayrollPeriod current =
                payrollPeriodRepository
                        .findByCompanyIdAndStatusIn(
                                companyId,
                                List.of(
                                        PayrollPeriodStatus.OPEN,
                                        PayrollPeriodStatus.LOCKED
                                )
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No active payroll period found."));

        return paySlipRepository
                .findLatestForCompanyAndPeriod(
                        companyId,
                        current.getPeriodStart(),
                        current.getPeriodEnd()
                )
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /* ============================================================
       GET ALL PAYSLIPS FOR CLOSED PERIODS
       ============================================================ */

    @Override
    public List<PaySlipDTO> getAllClosedPeriodPaySlips(Long companyId) {

        return paySlipRepository
                .findLatestForCompanyByPeriodStatus(companyId,PayrollPeriodStatus.CLOSED)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /* ============================================================
       GET EMPLOYEE PAYSLIPS (ALL CLOSED + CURRENT)
       ============================================================ */

    @Override
    public List<PaySlipDTO> getPaySlipsByEmployee(Long employeeId) {

        return paySlipRepository
                .findLatestByEmployee(employeeId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }
    @Override
    public List<PaySlipDTO> getPaySlipsForPeriod(
            Long companyId,
            Long periodId
    ) {

        PayrollPeriod period = payrollPeriodRepository
                .findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

        // 🔐 Safety: Ensure period belongs to company
        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                    "Period does not belong to specified company.");
        }

        return paySlipRepository
                .findLatestForCompanyAndPeriod(
                        companyId,
                        period.getPeriodStart(),
                        period.getPeriodEnd()
                )
                .stream()
                .map(this::mapToDto)
                .toList();
    }
    @Override
    public List<PaySlipDTO> getEmployeePaySlipsForPeriod(
            Long companyId,
            Long employeeId,
            Long periodId
    ) {

        PayrollPeriod period = payrollPeriodRepository
                .findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                    "Period does not belong to specified company.");
        }

        Employee employee = payrollRunRepository
                .findTopByEmployeeIdAndPayrollDateOrderByVersionNumberDesc(
                        employeeId,
                        period.getPeriodStart()
                )
                .map(PayrollRun::getEmployee)
                .orElseThrow(() ->
                        new IllegalStateException("Employee not found."));

        if (!employee.getDepartment()
                .getCompany()
                .getId()
                .equals(companyId)) {

            throw new IllegalStateException(
                    "Employee does not belong to specified company.");
        }

        return paySlipRepository
                .findLatestByEmployeeAndPeriod(
                        employeeId,
                        period.getPeriodStart(),
                        period.getPeriodEnd()
                )
                .stream()
                .map(this::mapToDto)
                .toList();
    }
    @Override
    public PaySlipDTO getLatestPaySlipForEmployeeForPeriod(
            Long companyId,
            Long employeeId,
            Long periodId
    ) {

        PayrollPeriod period = payrollPeriodRepository
                .findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                    "Period does not belong to specified company.");
        }

        PaySlip slip = paySlipRepository
                .findLatestByEmployeeAndPeriod(
                        employeeId,
                        period.getPeriodStart(),
                        period.getPeriodEnd()
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No payslip found for employee in this period.")
                );

        // 🔐 Company safety check
        if (!slip.getEmployee()
                .getDepartment()
                .getCompany()
                .getId()
                .equals(companyId)) {

            throw new IllegalStateException(
                    "Employee does not belong to specified company.");
        }

        return mapToDto(slip);
    }
    @Override
    public List<PaySlipDTO> getLatestPaySlipsForPeriod(
            Long companyId,
            Long periodId
    ) {

        PayrollPeriod period = payrollPeriodRepository
                .findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                    "Period does not belong to specified company.");
        }

        return paySlipRepository
                .findLatestForCompanyAndPeriod(
                        companyId,
                        period.getPeriodStart(),
                        period.getPeriodEnd()
                )
                .stream()
                .map(this::mapToDto)
                .toList();
    }
    /* ============================================================
       INTERNAL DTO MAPPER
       ============================================================ */

    private PaySlipDTO mapToDto(PaySlip paySlip) {

        Employee employee = paySlip.getEmployee();

        return PaySlipDTO.builder()
                .id(paySlip.getId())
                .employeeId(employee.getId())
                .employeeName(employee.getFullName())
                .payrollRunId(paySlip.getPayrollRun().getId())
                .payDate(paySlip.getPayDate())
                .grossPay(paySlip.getGrossPay())
                .totalDeductions(paySlip.getTotalDeductions())
                .netPay(paySlip.getNetPay())
                .versionNumber(paySlip.getVersionNumber())
                .build();
    }
}