package com.justjava.humanresource.finance;

import com.justjava.humanresource.payroll.dto.PayrollJournalEntryDTO;
import com.justjava.humanresource.payroll.entity.PaySlipDTO;
import com.justjava.humanresource.payroll.service.PayrollJournalService;
import com.justjava.humanresource.payroll.service.impl.PaySlipServiceImpl;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class FinanceController {
    @Autowired
    PaySlipServiceImpl paySlipService;

    @Autowired
    FlowableTaskService flowableTaskService;

    @Autowired
    PayrollJournalService payrollJournalService;

    @GetMapping("/finance")
    public String getFinancePage(Model model) {
        model.addAttribute("title","Finance Management");
        model.addAttribute("subTitle","Manage financial operations, budgeting, and reporting");
        return "finance/finance";
    }
    @GetMapping("/finance/dashboard")
    public String getFinanceDashboard(Model model) {
        List<HistoricTaskInstance> completedProcess =  flowableTaskService.getCompletedTaskstaskDefinitionKey("FormTask_15");
        List<FlowableTaskDTO> approvalRequests = flowableTaskService.getTasksByTaskDefinition("FormTask_15", "payrollPeriodCloseProcess");
        List<PaySlipDTO> paySlips = paySlipService.getCurrentPeriodPaySlips(1L);
        System.out.println("Current period payslips:" + paySlips.size());
        System.out.println("Completed process:" + completedProcess.size());
        paySlips.forEach(
                paySlip -> System.out.println(paySlip)
        );
        System.out.println("Lock approval requests:" + approvalRequests.size());

        approvalRequests.forEach(
                request -> System.out.println(request)
        );
        completedProcess.forEach(
                process -> System.out.println(process.getProcessVariables()+ " - " + process.getEndTime())
        );
        List<PayrollJournalEntryDTO> journalEntries = payrollJournalService.getUnexported(1L);
        journalEntries.forEach(
                entry -> System.out.println(entry)        );


        List<PaySlipDTO> payslipBankDetails = paySlipService.getCurrentPeriodPaySlips(1L);
        payslipBankDetails.forEach(
                paySlip -> System.out.println(paySlip)
        );
        model.addAttribute("bankDetails", payslipBankDetails);
        model.addAttribute("journalEntries", journalEntries);
        model.addAttribute("approvalRequests", approvalRequests);
        model.addAttribute("paySlips", paySlips);
        model.addAttribute("completedProcesses", completedProcess);
        model.addAttribute("title", "Finance Dashboard");
        model.addAttribute("subTitle", "Overview of financial performance and key metrics");
        return "finance/dashboard";
    }
    @GetMapping("/finance/lockApproval")
    public String getLockApprovalPage(Model model) {
        List<HistoricTaskInstance> completedProcess =  flowableTaskService.getCompletedTaskstaskDefinitionKey("FormTask_15");
        List<FlowableTaskDTO> approvalRequests = flowableTaskService.getTasksByTaskDefinition("FormTask_15", "payrollPeriodCloseProcess");
        List<PaySlipDTO> paySlips = paySlipService.getCurrentPeriodPaySlips(1L);
        System.out.println("Current period payslips:" + paySlips.size());
        System.out.println("Completed process:" + completedProcess.size());
        paySlips.forEach(
                paySlip -> System.out.println(paySlip)
        );
        System.out.println("Lock approval requests:" + approvalRequests.size());

        approvalRequests.forEach(
                request -> System.out.println(request)
        );
        completedProcess.forEach(
                process -> System.out.println(process.getProcessVariables()+ " - " + process.getEndTime())
        );
        model.addAttribute("approvalRequests", approvalRequests);
        model.addAttribute("paySlips", paySlips);
        model.addAttribute("completedProcesses", completedProcess);
        model.addAttribute("title", "Lock Approval");
        model.addAttribute("subTitle", "Manage lock approval processes and requests");
        return "finance/lockApproval";
    }
    @PostMapping("/approve/lock")
    public String approveLock(String taskId) {
        flowableTaskService.completeTask(taskId, Map.of("approved", true));
        return "redirect:/finance/lockApproval";
    }
    @GetMapping("/finance/lockedPeriods")
    public String getLockedPeriodsPage(Model model) {
        List<HistoricTaskInstance> completedProcess =  flowableTaskService.getCompletedTaskstaskDefinitionKey("FormTask_15");
        completedProcess.forEach(
                process -> System.out.println(process)
        );
        model.addAttribute("completedProcesses", completedProcess);
        model.addAttribute("title", "Locked Periods");
        model.addAttribute("subTitle", "All finalised payroll periods");
        return "finance/lockedPeriods";
    }
    @GetMapping("/finance/posting")
    public String getPostingPage(Model model) {
        List<PayrollJournalEntryDTO> journalEntries = payrollJournalService.getUnexported(1L);
        journalEntries.forEach(
                entry -> System.out.println(entry)        );

        model.addAttribute("journalEntries", journalEntries);
        model.addAttribute("title", "Financial Posting");
        model.addAttribute("subTitle", "Manage financial postings and journal entries");
        return "finance/posting";
    }
    @GetMapping("/finance/bankDetails")
    public String getBankDetailsPage(Model model) {
        List<PaySlipDTO> payslipBankDetails = paySlipService.getCurrentPeriodPaySlips(1L);
        payslipBankDetails.forEach(
                paySlip -> System.out.println(paySlip)
        );
        model.addAttribute("bankDetails", payslipBankDetails);
        model.addAttribute("title", "Bank Details");
        model.addAttribute("subTitle", "Manage bank details and transactions");
        return "finance/bankDetails";
    }
}
