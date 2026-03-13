package com.justjava.humanresource.payroll.service.impl;


import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeeBankDetail;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PaySlipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaySlipServiceImpl implements PaySlipService {

    private final PayrollRunRepository payrollRunRepository;
    private final PaySlipRepository paySlipRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollLineItemRepository payrollLineItemRepository;

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

        boolean exists =
                paySlipRepository.existsByPayrollRunIdAndVersionNumber(
                        payrollRunId,
                        run.getVersionNumber()
                );

        if (exists) {
            return; // idempotent
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
                        .orElse(null);

        if(current==null)
            return new ArrayList<>();

        System.out.println( " The Period Here====="+current);
        List<PaySlipDTO> currentPaySlips=paySlipRepository
                .findLatestForCompanyAndPeriod(
                        companyId,
                        current.getPeriodStart(),
                        current.getPeriodEnd()
                )
                .stream()
                .map(this::mapToDto)
                .toList();
        System.out.println( " The Payslip Size Around here==="+currentPaySlips.size());
        return currentPaySlips;
    }
    @Override
    public PayrollRun getEmployeeCurrentPayrollRun(Long companyId, Long employeeId) {
        PayrollPeriod current =
                payrollPeriodRepository
                        .findByCompanyIdAndStatusIn(
                                companyId,
                                List.of(
                                        PayrollPeriodStatus.OPEN,
                                        PayrollPeriodStatus.LOCKED
                                )
                        )
                        .orElse(null);
        return payrollRunRepository
                .findTopByEmployeeIdAndPeriodEndOrderByVersionNumberDesc(
                        employeeId,
                        current.getPeriodEnd()
                )
                .orElse(null);
    }
    @Override
    public List<PayrollRun> getCurrentPeriodPayrollRuns(Long companyId) {


        PayrollPeriod current =
                payrollPeriodRepository
                        .findByCompanyIdAndStatusIn(
                                companyId,
                                List.of(
                                        PayrollPeriodStatus.OPEN,
                                        PayrollPeriodStatus.LOCKED
                                )
                        )
                        .orElse(null);

        if(current==null)
            return new ArrayList<>();

        System.out.println( " The Period Here====="+current);
        List<PayrollRun> currentPayrollRuns=payrollRunRepository
                .findByEmployee_Department_Company_IdAndPayrollDateBetween(
                        companyId,
                        current.getPeriodStart(),
                        current.getPeriodEnd()
                );
        System.out.println( " The Payroll Run Size Around here==="+currentPayrollRuns.size());
        return currentPayrollRuns;
    }

    @Override
    public PaySlipDTO getCurrentPeriodPaySlipForEmployee(Long companyId, Long employeeId) {

        PayrollPeriod current =
                payrollPeriodRepository
                        .findByCompanyIdAndStatusIn(
                                companyId,
                                List.of(
                                        PayrollPeriodStatus.OPEN,
                                        PayrollPeriodStatus.LOCKED
                                )
                        )
                        .orElse(null);
/*                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No active payroll period found."));*/

        PaySlip slip = paySlipRepository
                .findLatestByEmployeeAndPeriod(
                        employeeId,
                        current.getPeriodStart(),
                        current.getPeriodEnd()
                )
                .orElse(null);
/*                .orElseThrow(() ->
                        new IllegalStateException(
                                "No payslip found for employee in current period.")
                );*/

/*        if (!slip.getEmployee()
                .getDepartment()
                .getCompany()
                .getId()
                .equals(companyId)) {

            throw new IllegalStateException(
                    "Employee does not belong to specified company.");
        }*/

        if(slip==null)
            return null;

        return mapToDto(slip);
    }

    /* ============================================================
       GET ALL PAYSLIPS FOR CLOSED PERIODS
       ============================================================ */

    @Override
    public List<PaySlipDTO> getAllClosedPeriodPaySlips(Long companyId) {
        System.out.println("Fetching all closed period payslips for companyId: " + companyId);
        return paySlipRepository
                .findLatestForCompanyByPeriodStatus(companyId,PayrollPeriodStatus.CLOSED)
                .stream()
                .map(this::mapToDto)
                .toList();

    }
    @Override
    public PaySlipDTO getLatestClosedPeriodPaySlipForEmployee(Long companyId, Long employeeId) {

        PaySlip slip = paySlipRepository
                .findLatestForCompanyByPeriodStatus(companyId,PayrollPeriodStatus.CLOSED)
                .stream()
                .filter(s -> s.getEmployee().getId().equals(employeeId))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No payslip found for employee in closed periods.")
                );
        return mapToDto(slip);
    }

    /* ============================================================
       GET EMPLOYEE PAYSLIPS (ALL CLOSED + CURRENT)
       ============================================================ */

    @Override
    public List<PaySlipDTO> getPaySlipsByEmployee(Long employeeId) {

        return paySlipRepository
                .findLatestByEmployee(employeeId)
                .stream()
                .filter(ps-> ps.getPayrollRun().getStatus() == PayrollRunStatus.POSTED)
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
                .findTopByEmployeeIdAndPeriodEndOrderByVersionNumberDesc(
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

    @Override
    public boolean existsForPayrollRun(Long payrollRunId) {
        return paySlipRepository.existsByPayrollRunId(payrollRunId);
    }
    /* ============================================================
       INTERNAL DTO MAPPER
       ============================================================ */
    private PaySlipDTO mapToDto(PaySlip paySlip) {

        PayrollRun run = paySlip.getPayrollRun();

        List<PayrollLineItem> lines =
                payrollLineItemRepository
                        .findByPayrollRunId(run.getId());

        BigDecimal basicSalary = BigDecimal.ZERO;

        List<PaySlipLineDTO> allowances = new ArrayList<>();
        List<PaySlipLineDTO> deductions = new ArrayList<>();

        for (PayrollLineItem line : lines) {

            if ("BASIC".equals(line.getComponentCode())) {
                basicSalary = line.getAmount();
                continue;
            }

            PaySlipLineDTO dto = PaySlipLineDTO.builder()
                    .code(line.getComponentCode())
                    .description(line.getDescription())
                    .amount(line.getAmount())
                    .taxable(line.isTaxable())
                    .build();

            if (line.getComponentType() == PayComponentType.EARNING) {
                allowances.add(dto);
            } else {
                deductions.add(dto);
            }
        }

        List<EmployeeBankDetail> employeeBankDetails= paySlip.getEmployee().getBankDetails();
        EmployeeBankDetail employeeBankDetail=null;
        if(employeeBankDetails!=null && !employeeBankDetails.isEmpty()){
            employeeBankDetail=employeeBankDetails.get(0);
        }

        return PaySlipDTO.builder()
                .id(paySlip.getId())
                .employeeId(paySlip.getEmployee().getId())
                .employeeName(paySlip.getEmployee().getFullName())
                .payrollRunId(run.getId())
                .payDate(paySlip.getPayDate())
                .versionNumber(paySlip.getVersionNumber())
                .bankName(employeeBankDetail!=null?employeeBankDetail.getBankName():null)
                .bankAccountNumber(employeeBankDetail!=null?employeeBankDetail.getAccountNumber():null)
                .basicSalary(basicSalary)
                .grossPay(paySlip.getGrossPay())
                .totalDeductions(paySlip.getTotalDeductions())
                .netPay(paySlip.getNetPay())

                .allowances(allowances)
                .deductions(deductions)

                .appliedTaxBandSummary(run.getAppliedTaxBandSummary())
                .appliedPensionSchemeName(run.getAppliedPensionSchemeName())

                .build();
    }
}