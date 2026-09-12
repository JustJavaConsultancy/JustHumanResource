package com.justjava.humanresource.employeeexit.controller;

import com.justjava.humanresource.employeeexit.dto.ExitDashboardSummary;
import com.justjava.humanresource.employeeexit.dto.ExitReportFilter;
import com.justjava.humanresource.employeeexit.service.EmployeeExitAuthorizationService;
import com.justjava.humanresource.employeeexit.service.EmployeeExitReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employee-exits/reports")
@RequiredArgsConstructor
public class EmployeeExitReportApiController {
    private final EmployeeExitReportService reports;
    private final EmployeeExitAuthorizationService authorization;

    @GetMapping("/summary")
    public ExitDashboardSummary summary(@ModelAttribute ExitReportFilter filter) {
        authorization.require(authorization.canViewReports(), "Exit report access is required.");
        return reports.summary(filter);
    }
}
