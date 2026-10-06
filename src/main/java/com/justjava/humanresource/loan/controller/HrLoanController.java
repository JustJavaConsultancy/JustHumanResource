package com.justjava.humanresource.loan.controller;

import com.justjava.humanresource.loan.dto.HrLoanDashboardResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationDetailResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationSummaryResponse;
import com.justjava.humanresource.loan.dto.LoanApprovalActionCommand;
import com.justjava.humanresource.loan.dto.LoanApprovalTaskResponse;
import com.justjava.humanresource.loan.dto.LoanMissedDeductionResponse;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.service.EmployeeLoanApplicationService;
import com.justjava.humanresource.loan.service.LoanApprovalService;
import com.justjava.humanresource.loan.service.LoanReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HR monitoring and the HR approval stage. HR/admin access is enforced in the services. */
@RestController
@RequestMapping("/api/hr/loans")
@RequiredArgsConstructor
public class HrLoanController {

    private final LoanReportService reportService;
    private final EmployeeLoanApplicationService applicationService;
    private final LoanApprovalService approvalService;

    @GetMapping("/dashboard")
    public HrLoanDashboardResponse dashboard() {
        return reportService.getHrDashboard();
    }

    @GetMapping
    public List<LoanApplicationSummaryResponse> list(@RequestParam(required = false) LoanApplicationStatus status) {
        return reportService.listApplicationsForHr(status);
    }

    @GetMapping("/{id}")
    public LoanApplicationDetailResponse detail(@PathVariable Long id) {
        return applicationService.getHrDetail(id);
    }

    @GetMapping("/tasks")
    public List<LoanApprovalTaskResponse> tasks() {
        return approvalService.listHrTasks();
    }

    @PostMapping("/approvals/approve")
    public void approve(@Valid @RequestBody LoanApprovalActionCommand c) {
        approvalService.approveHr(c.getTaskId(), c.getComment());
    }

    @PostMapping("/approvals/reject")
    public void reject(@Valid @RequestBody LoanApprovalActionCommand c) {
        approvalService.rejectHr(c.getTaskId(), c.getComment());
    }

    @PostMapping("/approvals/return")
    public void returnForCorrection(@Valid @RequestBody LoanApprovalActionCommand c) {
        approvalService.returnHr(c.getTaskId(), c.getComment());
    }

    @GetMapping("/missed-deductions")
    public List<LoanMissedDeductionResponse> missedDeductions() {
        return reportService.listMissedDeductions();
    }
}
