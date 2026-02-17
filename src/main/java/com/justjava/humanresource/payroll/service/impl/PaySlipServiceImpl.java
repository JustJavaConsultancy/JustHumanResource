package com.justjava.humanresource.payroll.service.impl;


import com.justjava.humanresource.payroll.entity.PaySlip;
import com.justjava.humanresource.payroll.entity.PaySlipDTO;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PaySlipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaySlipServiceImpl implements PaySlipService {

    private final PayrollRunRepository payrollRunRepository;
    private final PaySlipRepository paySlipRepository;

    @Override
    public void generatePaySlip(Long payrollRunId) {

        PayrollRun run = payrollRunRepository
                .findById(payrollRunId)
                .orElseThrow();

        // In real impl, aggregate PayrollLineItem
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;

        PaySlip slip = new PaySlip();
        slip.setPayrollRun(run);
        slip.setPayDate(run.getPayrollDate());
        slip.setGrossPay(run.getGrossPay());
        slip.setTotalDeductions(deductions);
        slip.setVersionNumber(run.getVersionNumber());
        //slip.setNetPay(gross.subtract(deductions));
        slip.setNetPay(run.getNetPay());
        slip.setEmployee(run.getEmployee());

        paySlipRepository.save(slip);
    }
    /* ============================================================
       GET ALL FOR EMPLOYEE
       ============================================================ */

    @Override
    public List<PaySlipDTO> getPaySlipsByEmployee(Long employeeId) {

        return paySlipRepository
                .findByEmployee_Id(employeeId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /* ============================================================
       GET ALL FOR PERIOD
       ============================================================ */

    @Override
    public List<PaySlipDTO> getPaySlipsForPeriod(YearMonth period) {

        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();

        return paySlipRepository
                .findByPayDateBetween(start, end)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /* ============================================================
       GET EMPLOYEE FOR PERIOD
       ============================================================ */

    @Override
    public List<PaySlipDTO> getEmployeePaySlipsForPeriod(
            Long employeeId,
            YearMonth period
    ) {

        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();

        return paySlipRepository
                .findByEmployee_IdAndPayDateBetween(
                        employeeId,
                        start,
                        end
                )
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /* ============================================================
       INTERNAL MAPPER
       ============================================================ */

    private PaySlipDTO mapToDto(PaySlip paySlip) {

        return PaySlipDTO.builder()
                .id(paySlip.getId())
                .employeeId(paySlip.getEmployee().getId())
                .payrollRunId(paySlip.getPayrollRun().getId())
                .payDate(paySlip.getPayDate())
                .grossPay(paySlip.getGrossPay())
                .totalDeductions(paySlip.getTotalDeductions())
                .netPay(paySlip.getNetPay())
                .build();
    }
}