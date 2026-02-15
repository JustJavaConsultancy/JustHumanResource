package com.justjava.humanresource.payroll.service.impl;


import com.justjava.humanresource.payroll.entity.PaySlip;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PaySlipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
        //slip.setNetPay(gross.subtract(deductions));
        slip.setNetPay(run.getNetPay());
        slip.setEmployee(run.getEmployee());

        paySlipRepository.save(slip);
    }
}
