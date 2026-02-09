package com.justjava.humanresource.payroll.workflow;


import java.time.LocalDate;

public interface PayrollOrchestrationService {

    Long initializePayrollRun(
            Long employeeId,
            LocalDate payrollDate,
            String processInstanceId
    );

    void calculateEarnings(Long payrollRunId);

    void applyStatutoryDeductions(Long payrollRunId);

    void finalizePayroll(Long payrollRunId);
}
