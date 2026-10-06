package com.justjava.humanresource.loan.controller;

import com.justjava.humanresource.loan.dto.FinanceLoanDashboardResponse;
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

/** Finance monitoring and the Finance approval stage. Finance/admin access is enforced in the services. */
@RestController
@RequestMapping("/api/finance/loans")
@RequiredArgsConstructor
public class FinanceLoanController {

    private final LoanReportService reportService;
    private final EmployeeLoanApplicationService applicationService;
    private final LoanApprovalService approvalService;

    @GetMapping("/dashboard")
    public FinanceLoanDashboardResponse dashboard() {
        return reportService.getFinanceDashboard();
    }

    @GetMapping
    public List<LoanApplicationSummaryResponse> list(@RequestParam(required = false) LoanApplicationStatus status) {
        return reportService.listApplicationsForFinance(status);
    }

    @GetMapping("/{id}")
    public LoanApplicationDetailResponse detail(@PathVariable Long id) {
        return applicationService.getFinanceDetail(id);
    }

    @GetMapping("/tasks")
    public List<LoanApprovalTaskResponse> tasks() {
        return approvalService.listFinanceTasks();
    }

    @PostMapping("/approvals/approve")
    public void approve(@Valid @RequestBody LoanApprovalActionCommand c) {
        approvalService.approveFinance(c.getTaskId(), c.getComment());
    }

    @PostMapping("/approvals/reject")
    public void reject(@Valid @RequestBody LoanApprovalActionCommand c) {
        approvalService.rejectFinance(c.getTaskId(), c.getComment());
    }

    @PostMapping("/approvals/return")
    public void returnForCorrection(@Valid @RequestBody LoanApprovalActionCommand c) {
        approvalService.returnFinance(c.getTaskId(), c.getComment());
    }

    @GetMapping("/missed-deductions")
    public List<LoanMissedDeductionResponse> missedDeductions() {
        return reportService.listMissedDeductions();
    }
}
