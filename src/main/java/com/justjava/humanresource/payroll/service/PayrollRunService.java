package com.justjava.humanresource.payroll.service;


import com.justjava.humanresource.payroll.dto.PayrollRunDTO;
import com.justjava.humanresource.payroll.report.dto.*;

import java.time.LocalDate;
import java.util.List;

public interface PayrollRunService {

    PayrollRunDTO getPayrollRun(Long payrollRunId);

    PayrollRunDTO getEmployeePayrollRun(Long employeeId,Long companyId);

    List<PayrollRunDTO> getPayrollRunsForPeriod(
            Long companyId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
    public List<PayrollRunDTO> getCurrentPeriodPayrollRuns(Long companyId);
    public List<PayrollSummaryDTO> getPayrollSummary(
            Long companyId,
            LocalDate start,
            LocalDate end);
    public List<ComponentBreakdownDTO> getEarningsBreakdown(
            Long companyId,
            LocalDate start,
            LocalDate end);
    public List<ComponentBreakdownDTO> getDeductionBreakdown(
            Long companyId,
            LocalDate start,
            LocalDate end);
    public List<ComponentTrendDTO> getComponentTrend(Long companyId);
    public List<PayeReportDTO> getPayeReport(
            Long companyId,
            LocalDate start,
            LocalDate end);
    public List<PensionReportDTO> getPensionReport(
            Long companyId,
            LocalDate start,
            LocalDate end);
}