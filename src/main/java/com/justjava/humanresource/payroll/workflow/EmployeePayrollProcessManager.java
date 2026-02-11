package com.justjava.humanresource.payroll.workflow;

public interface EmployeePayrollProcessManager {

    void ensureProcessStarted(Long employeeId);
}
