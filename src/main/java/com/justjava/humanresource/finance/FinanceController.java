package com.justjava.humanresource.finance;

import com.justjava.humanresource.payroll.dto.PayrollJournalEntryDTO;
import com.justjava.humanresource.payroll.entity.PayrollAccountingSettings;
import com.justjava.humanresource.payroll.entity.PaySlipDTO;
import com.justjava.humanresource.payroll.report.dto.PayrollSummaryDTO;
import com.justjava.humanresource.payroll.repositories.PayrollAccountingSettingsRepository;
import com.justjava.humanresource.payroll.service.PayrollJournalService;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import com.justjava.humanresource.payroll.service.impl.PaySlipServiceImpl;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Autowired
    PayrollAccountingSettingsRepository accountingSettingsRepository;

    @Autowired
    PayrollRunService payrollRunService;

    @GetMapping("/finance")
    public String getFinancePage(Model model) {
        model.addAttribute("title","Finance Management");
        model.addAttribute("subTitle","Manage financial operations, budgeting, and reporting");
        return "finance/finance";
    }
    @GetMapping("/finance/dashboard")
    public String getFinanceDashboard(Model model) {
        List<HistoricTaskInstance> completedProcess =  flowableTaskService.getCompletedTaskstaskDefinitionKey("financeOfficer");
        List<FlowableTaskDTO> approvalRequests = flowableTaskService.getTasksByTaskDefinition("financeOfficer", "payrollPeriodCloseProcess");
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
        List<HistoricTaskInstance> completedProcess =  flowableTaskService.getCompletedTaskstaskDefinitionKey("financeOfficer");
        List<FlowableTaskDTO> approvalRequests = flowableTaskService.getTasksByTaskDefinition("financeOfficer", "payrollPeriodCloseProcess");
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
    @PostMapping("/reject/lock")
    public String rejectLock(String taskId) {
        flowableTaskService.completeTask(taskId, Map.of("approved", false));
        return "redirect:/finance/lockApproval";
    }
    @GetMapping("/finance/lockedPeriods")
    public String getLockedPeriodsPage(Model model) {
        List<HistoricTaskInstance> completedProcess =  flowableTaskService.getCompletedTaskstaskDefinitionKey("financeOfficer");
        completedProcess.forEach(
                process -> System.out.println(process)
        );
        model.addAttribute("completedProcesses", completedProcess);

        // ── YTD (Jan 1 – Dec 31) payroll summary for the "YTD Summary" card ──────
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

    @GetMapping("/finance/accounting-settings")
    public String getAccountingSettings(Model model) {
        PayrollAccountingSettings settings = accountingSettingsRepository.findByCompanyId(1L)
                .orElseGet(() -> {
                    PayrollAccountingSettings defaults = new PayrollAccountingSettings();
                    defaults.setCompanyId(1L);
                    return accountingSettingsRepository.save(defaults);
                });

        model.addAttribute("settings", settings);
        model.addAttribute("title", "Payroll Accounting Settings");
        model.addAttribute("subTitle", "Configure default payroll ledger accounts");
        return "finance/accounting-settings";
    }

    @PostMapping("/finance/accounting-settings")
    public String updateAccountingSettings(PayrollAccountingSettings incoming) {
        PayrollAccountingSettings settings = accountingSettingsRepository.findByCompanyId(1L)
                .orElseGet(PayrollAccountingSettings::new);

        settings.setCompanyId(1L);
        settings.setSuspenseAccountCode(incoming.getSuspenseAccountCode());
        settings.setSuspenseAccountName(incoming.getSuspenseAccountName());
        settings.setSalaryPayableAccountCode(incoming.getSalaryPayableAccountCode());
        settings.setSalaryPayableAccountName(incoming.getSalaryPayableAccountName());
        settings.setBankAccountCode(incoming.getBankAccountCode());
        settings.setBankAccountName(incoming.getBankAccountName());
        settings.setDefaultEarningExpenseAccountCode(incoming.getDefaultEarningExpenseAccountCode());
        settings.setDefaultEarningExpenseAccountName(incoming.getDefaultEarningExpenseAccountName());
        settings.setDefaultDeductionPayableAccountCode(incoming.getDefaultDeductionPayableAccountCode());
        settings.setDefaultDeductionPayableAccountName(incoming.getDefaultDeductionPayableAccountName());
        settings.setPayePayableAccountCode(incoming.getPayePayableAccountCode());
        settings.setPayePayableAccountName(incoming.getPayePayableAccountName());
        settings.setPensionPayableAccountCode(incoming.getPensionPayableAccountCode());
        settings.setPensionPayableAccountName(incoming.getPensionPayableAccountName());
        settings.setEmployerPensionExpenseAccountCode(incoming.getEmployerPensionExpenseAccountCode());
        settings.setEmployerPensionExpenseAccountName(incoming.getEmployerPensionExpenseAccountName());
        settings.setEmployerPensionPayableAccountCode(incoming.getEmployerPensionPayableAccountCode());
        settings.setEmployerPensionPayableAccountName(incoming.getEmployerPensionPayableAccountName());
        settings.setResidualAdjustmentExpenseAccountCode(incoming.getResidualAdjustmentExpenseAccountCode());
        settings.setResidualAdjustmentExpenseAccountName(incoming.getResidualAdjustmentExpenseAccountName());
        settings.setResidualClearingAccountCode(incoming.getResidualClearingAccountCode());
        settings.setResidualClearingAccountName(incoming.getResidualClearingAccountName());
        accountingSettingsRepository.save(settings);

        return "redirect:/finance/accounting-settings";
    }

    @GetMapping("/finance/journals/export")
    public ResponseEntity<ByteArrayResource> exportJournalCsv(@RequestParam Long periodId) {
        byte[] csv = payrollJournalService.exportPeriodCsv(1L, periodId, "finance");
        String filename = "payroll-journal-period-" + periodId + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(csv.length)
                .body(new ByteArrayResource(csv));
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
