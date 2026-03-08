package com.justjava.humanresource.payroll.service;


import com.justjava.humanresource.payroll.dto.PayrollRunDTO;

import java.time.LocalDate;
import java.util.List;

public interface PayrollRunService {

    PayrollRunDTO getPayrollRun(Long payrollRunId);

    PayrollRunDTO getEmployeePayrollRun(Long employeeId, LocalDate payrollDate);

    List<PayrollRunDTO> getPayrollRunsForPeriod(
            Long companyId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
    public List<PayrollRunDTO> getCurrentPeriodPayrollRuns(Long companyId);
}