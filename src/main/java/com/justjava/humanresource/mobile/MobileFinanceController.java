package com.justjava.humanresource.mobile;

import com.justjava.humanresource.payroll.dto.PayrollJournalEntryDTO;
import com.justjava.humanresource.payroll.entity.PaySlipDTO;
import com.justjava.humanresource.payroll.report.dto.PayrollSummaryDTO;
import com.justjava.humanresource.payroll.service.PayrollJournalService;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import com.justjava.humanresource.payroll.service.impl.PaySlipServiceImpl;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/mobile/finance")
public class MobileFinanceController {

    @Autowired
    private PaySlipServiceImpl paySlipService;

    @Autowired
    private FlowableTaskService flowableTaskService;

    @Autowired
    private PayrollJournalService payrollJournalService;

    @Autowired
    private PayrollRunService payrollRunService;

    @GetMapping("")
    public String getFinancePage(Model model) {
        model.addAttribute("title", "Finance");
        model.addAttribute("subTitle", "Financial overview");
        return "mobile/finance";
    }

    @GetMapping("/dashboard")
    public String getFinanceDashboard(Model model) {
        List<HistoricTaskInstance> completedProcess =
                flowableTaskService.getCompletedTaskstaskDefinitionKey("financeOfficer");
        List<FlowableTaskDTO> approvalRequests =
                flowableTaskService.getTasksByTaskDefinition("financeOfficer", "payrollPeriodCloseProcess");
        model.addAttribute("approvalRequests", approvalRequests);
        model.addAttribute("completedProcesses", completedProcess);
        model.addAttribute("title", "Finance Dashboard");
        model.addAttribute("subTitle", "Overview of financial performance");
        return "mobile/finance-dashboard";
    }

    @GetMapping("/lockApproval")
    public String getLockApprovalPage(Model model) {
        List<HistoricTaskInstance> completedProcess =
                flowableTaskService.getCompletedTaskstaskDefinitionKey("financeOfficer");
        List<FlowableTaskDTO> approvalRequests =
                flowableTaskService.getTasksByTaskDefinition("financeOfficer", "payrollPeriodCloseProcess");
        List<PaySlipDTO> paySlips = paySlipService.getCurrentPeriodPaySlips(1L);
        model.addAttribute("approvalRequests", approvalRequests);
        model.addAttribute("paySlips", paySlips);
        model.addAttribute("completedProcesses", completedProcess);
        model.addAttribute("title", "Lock Approval");
        model.addAttribute("subTitle", "Manage payroll lock requests");
        return "mobile/finance-lock-approval";
    }

    @PostMapping("/approve/lock")
    public String approveLock(@RequestParam String taskId) {
        flowableTaskService.completeTask(taskId, Map.of("approved", true));
        return "redirect:/mobile/finance/lockApproval";
    }

    @PostMapping("/reject/lock")
    public String rejectLock(@RequestParam String taskId,
                              @RequestParam(required = false) String reason) {
        flowableTaskService.completeTask(taskId, Map.of("approved", false));
        return "redirect:/mobile/finance/lockApproval";
    }

    @GetMapping("/lockedPeriods")
    public String getLockedPeriodsPage(Model model) {
        List<HistoricTaskInstance> completedProcess =
                flowableTaskService.getCompletedTaskstaskDefinitionKey("financeOfficer");
        model.addAttribute("completedProcesses", completedProcess);

        int reportYear = LocalDate.now().getYear();
        LocalDate ytdStart = LocalDate.of(reportYear, 1, 1);
        LocalDate ytdEnd   = LocalDate.of(reportYear, 12, 31);

        List<PayrollSummaryDTO> ytdSummary =
                payrollRunService.getPayrollSummary(1L, ytdStart, ytdEnd);

        BigDecimal ytdGrossPay = ytdSummary.stream()
                .map(s -> s.getTotalGross() != null ? s.getTotalGross() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ytdNetPay = ytdSummary.stream()
                .map(s -> s.getTotalNet() != null ? s.getTotalNet() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ytdPaye = ytdSummary.stream()
                .map(s -> s.getTotalPaye() != null ? s.getTotalPaye() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ytdPension = ytdSummary.stream()
                .map(s -> s.getTotalPension() != null ? s.getTotalPension() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("ytdGrossPay", ytdGrossPay);
        model.addAttribute("ytdNetPay",   ytdNetPay);
        model.addAttribute("ytdPaye",     ytdPaye);
        model.addAttribute("ytdPension",  ytdPension);
        model.addAttribute("reportYear",  reportYear);
        model.addAttribute("title", "Locked Periods");
        model.addAttribute("subTitle", "All finalised payroll periods");
        return "mobile/finance-locked-periods";
    }

    @GetMapping("/posting")
    public String getPostingPage(Model model) {
        List<PayrollJournalEntryDTO> journalEntries =
                payrollJournalService.getUnexported(1L);
        model.addAttribute("journalEntries", journalEntries);
        model.addAttribute("title", "Journal Postings");
        model.addAttribute("subTitle", "Payroll accounting entries by period");
        return "mobile/finance-posting";
    }

    @GetMapping("/bankDetails")
    public String getBankDetailsPage(Model model) {
        List<PaySlipDTO> bankDetails = paySlipService.getCurrentPeriodPaySlips(1L);
        model.addAttribute("bankDetails", bankDetails);
        model.addAttribute("title", "Bank Details");
        model.addAttribute("subTitle", "Employee bank details for current period");
        return "mobile/finance-bank-details";
    }
}
