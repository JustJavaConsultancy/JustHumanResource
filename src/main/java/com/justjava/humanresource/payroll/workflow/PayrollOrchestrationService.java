package com.justjava.humanresource.payroll.workflow;


import java.time.LocalDate;

public interface PayrollOrchestrationService {

    /**
     * Initialises (or resumes) a payroll run for one employee.
     *
     * @param employeeId        target employee
     * @param payrollDate       the payroll date — always the current open period's end date
     * @param retroEffectiveDate the original salary/allowance change effective date;
     *                          {@code null} for non-retro runs; when this falls before
     *                          the open period's start, catch-up RETRO_ADJ line items
     *                          are added during earnings calculation
     * @param processInstanceId Flowable process instance driving this run
     * @return the {@code PayrollRun} ID to use in subsequent delegate calls
     */
    Long initializePayrollRun(
            Long employeeId,
            LocalDate payrollDate,
            LocalDate retroEffectiveDate,
            String processInstanceId
    );

    void calculateEarnings(Long payrollRunId);

    void applyStatutoryDeductions(Long payrollRunId);

    void finalizePayroll(Long payrollRunId);
    public void applyOtherDeductions(Long payrollRunId);
}
