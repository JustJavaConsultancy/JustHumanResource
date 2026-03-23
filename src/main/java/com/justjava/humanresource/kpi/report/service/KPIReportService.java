package com.justjava.humanresource.kpi.report.service;

import com.justjava.humanresource.kpi.report.dto.*;

import java.time.YearMonth;
import java.util.List;

public interface KPIReportService {

    List<EmployeeKpiScorecardDTO> getEmployeeScorecard(
            Long employeeId,
            YearMonth period
    );

    List<DepartmentKpiReportDTO> getDepartmentKpiReport(
            YearMonth period
    );

    List<AppraisalSummaryDTO> getAppraisalSummary(
            Long cycleId
    );
    List<EmployeeKpiScorecardDTO> getAllEmployeesScorecard(YearMonth period);
    List<TopPerformerDTO> getTopPerformersByKpi(
            YearMonth period,
            int limit
    );

    List<TopPerformerDTO> getTopPerformersByAppraisal(
            Long cycleId,
            int limit
    );
    KpiDashboardDTO getKpiDashboard(
            YearMonth period,
            int topLimit,
            int bottomLimit
    );
}