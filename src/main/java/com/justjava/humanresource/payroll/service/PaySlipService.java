package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.entity.PaySlipDTO;

import java.time.YearMonth;
import java.util.List;

public interface PaySlipService {

    void generatePaySlip(Long payrollRunId);
    List<PaySlipDTO> getPaySlipsByEmployee(Long employeeId);

    List<PaySlipDTO> getPaySlipsForPeriod(YearMonth period);

    List<PaySlipDTO> getEmployeePaySlipsForPeriod(Long employeeId,YearMonth period);
    public PaySlipDTO getLatestPaySlipForEmployeeForPeriod(Long employeeId,YearMonth period);
    public List<PaySlipDTO> getLatestPaySlipsForPeriod(YearMonth period);
}
