package com.justjava.humanresource.payroll.service;


import java.time.LocalDate;

public interface PayrollChangeOrchestrator{
    public void recalculateForEmployee(
            Long employeeId, LocalDate effectiveDate);
    public void recalculateForPayGroup(
            Long payGroupId,LocalDate effectiveDate);
}
