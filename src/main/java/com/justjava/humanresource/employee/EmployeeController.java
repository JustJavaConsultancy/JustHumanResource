package com.justjava.humanresource.employee;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.dto.EmployeeDocumentDTO;
import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeeDocument;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.service.EmployeeUploadService;
import com.justjava.humanresource.hr.service.JobHrEmployeeAccessService;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.hr.service.EmployeeDocumentService;
import com.justjava.humanresource.hr.service.impl.EmployeeUploadServiceImpl.DuplicateEmailUploadException;
import com.justjava.humanresource.kpi.dto.AppraisalTaskViewDTO;
import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.service.AppraisalService;
import com.justjava.humanresource.kpi.service.KpiAssignmentService;
import com.justjava.humanresource.kpi.service.KpiDefinitionService;
import com.justjava.humanresource.kpi.service.KpiMeasurementService;
import com.justjava.humanresource.onboarding.dto.StartEmployeeOnboardingCommand;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService.DuplicateEmailException;
import com.justjava.humanresource.payroll.dto.PayrollRunDTO;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.service.PaySlipService;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.justjava.humanresource.payroll.dto.FutureEmployeeAllowanceDTO;

import java.util.Optional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class EmployeeController {

    @Autowired AuthenticationManager authenticationManager;
    @Autowired private SetupService setupService;
    @Autowired private EmployeeOnboardingService employeeOnboardingService;
    @Autowired private KpiAssignmentService kpiAssignmentService;
    @Autowired KpiMeasurementService kpiMeasurementService;
    @Autowired AppraisalService appraisalService;
    @Autowired private PayrollRunService payrollRunService;
    @Autowired private FlowableTaskService flowableTaskService;
    @Autowired private KpiDefinitionService kpiDefinitionService;
    @Autowired private EmployeeService employeeService;
    @Autowired private PayrollSetupService payrollSetupService;
    @Autowired private EmployeeDocumentService documentService;
    @Autowired private EmployeeUploadService employeeUploadService;
    @Autowired private JobHrEmployeeAccessService jobHrEmployeeAccessService;
    @Autowired PaySlipService paySlipService;

    // ─────────────────────────────────────────────────────────────────────────
    //  EMPLOYEES PAGE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/employees")
    public String getEmployees(Model model) {
        List<Department>         departments = setupService.getAllDepartments();
        List<PayGroup>           payGroups   = payrollSetupService.getAllPayGroups();
        List<JobGradeResponseDTO> jobGrades  = setupService.getAllJobGrades();
        jobGrades.forEach(g ->
                System.out.println("Job Grade: " + g.getSteps() + ", Description: " + g.getId()));

        List<Employee> employees = employeeOnboardingService.getAllOnboardings().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
        employees = jobHrEmployeeAccessService.filterEmployeesByScope(employees);

        employees.forEach(e ->
                System.out.println("Employee: " + e.getFirstName() + " " + e.getEmploymentStatus()
                        + ", Department: " + e.getDepartment().getName()));

        List<Deduction>  deductions = payrollSetupService.getActiveDeductions();
        List<Allowance>  allowances = payrollSetupService.getActiveAllowances();
        List<TaxRelief>  taxReliefs = payrollSetupService.getActiveTaxReliefs();

        model.addAttribute("deductions",  deductions);
        model.addAttribute("allowances",  allowances);
        model.addAttribute("taxReliefs",  taxReliefs);
        model.addAttribute("employees",   employees);
        model.addAttribute("jobGrades",   jobGrades);
        model.addAttribute("departments", departments);
        model.addAttribute("payGroups",   payGroups);
        model.addAttribute("title",       "Employee Management");
        model.addAttribute("subTitle",    "Manage employee records, payroll, and performance data");
        model.addAttribute("isRestrictedHr", authenticationManager.isRestrictedHr());
        return "employees/main";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ONBOARDING / CREATE (single employee)
    //  Returns a redirect on success, or a 409 JSON response on duplicate email
    //  so the front-end modal can display the error without a full page reload.
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/onboarding")
    public Object startOnboarding(
            StartEmployeeOnboardingCommand command,
            @RequestParam(defaultValue = "humanResource") String initiatedBy,
            @RequestParam(required = false) String accountName,
            @RequestParam(required = false) String bankName,
            @RequestParam(required = false) String accountNumber) {
        jobHrEmployeeAccessService.assertCanUseJobStep(command.getJobStepId());

        try {
            EmployeeOnboardingResponseDTO dto =
                    employeeOnboardingService.startOnboarding(command, initiatedBy);

            employeeService.changeEmploymentStatus(dto.getEmployeeId(), EmploymentStatus.ACTIVE, LocalDate.now());

            boolean hasBankData = accountName != null && !accountName.isBlank()
                    && bankName != null && !bankName.isBlank()
                    && accountNumber != null && !accountNumber.isBlank();

            if (hasBankData) {
                EmployeeDTO bankDto = new EmployeeDTO();
                bankDto.setAccountName(accountName.trim());
                bankDto.setBankName(bankName.trim());
                bankDto.setAccountNumber(accountNumber.trim());
                employeeService.updateBankDetails(dto.getEmployeeId(), bankDto);
            }

            // Standard form submit — redirect on success
            return "redirect:/employees";

        } catch (DuplicateEmailException ex) {
            // The modal submitted via fetch() / AJAX — return JSON so the UI
            // can show the error inline without losing the form data.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error",   "DUPLICATE_EMAIL");
            body.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UPDATE EMPLOYEE
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/employees/{id}")
    @ResponseBody
    public ResponseEntity<?> updateEmployee(@PathVariable Long id,
                                            @RequestBody EmployeeDTO incomingEmployee) {
        jobHrEmployeeAccessService.assertCanAccessEmployee(id);
        jobHrEmployeeAccessService.assertCanUseJobStep(incomingEmployee.getJobStepId());
        try {
            System.out.println("Received update for employee ID: " + id + " with data: " + incomingEmployee);
            employeeOnboardingService.updateEmployee(id, incomingEmployee);
            System.out.println("Employee updated successfully for ID: " + id);
            return ResponseEntity.ok().build();
        } catch (DuplicateEmailException ex) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error",   "DUPLICATE_EMAIL");
            body.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
    }

    @PostMapping("/employees/{id}/suspend")
    public String suspendEmployee(@PathVariable Long id,
                                  @RequestParam("fromDate") LocalDate fromDate,
                                  @RequestParam(value = "toDate", required = false) LocalDate toDate) {
        jobHrEmployeeAccessService.assertCanAccessEmployee(id);
        employeeService.suspendEmployee(id, fromDate, toDate);
        return "redirect:/employees";
    }

    // ─────────────────────────────────────────────────────────────────────────
//  TOGGLE RESTRICTED VISIBILITY
// ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/employees/{id}/toggle-visibility")
    @ResponseBody
    public ResponseEntity<?> toggleVisibility(@PathVariable Long id) {
        // Only regular HR and admin can call this — restrictedHr cannot
        if (authenticationManager.isRestrictedHr()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
        }
        Employee employee = employeeService.toggleRestrictedVisibility(id);
        return ResponseEntity.ok(Map.of("restrictedVisibility", employee.isRestrictedVisibility()));
    }


    // ─────────────────────────────────────────────────────────────────────────
    //  PAY ITEMS
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/employees/{id}/pay-items")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEmployeePayItems(@PathVariable Long id) {
        jobHrEmployeeAccessService.assertCanAccessEmployee(id);
        List<EmployeeAllowanceResponse>  allowances = payrollSetupService.getAllowancesForEmployee(id);
        List<EmployeeDeductionResponse>  deductions = payrollSetupService.getDeductionsForEmployee(id);
        List<EmployeeTaxReliefResponse>  taxReliefs = payrollSetupService.getTaxReliefsForEmployee(id);
        allowances.forEach(a -> System.out.println("Allowance for employee ID " + id + ": " + a));
        System.out.println("Deductions for employee ID " + id + ": " + deductions);
        return ResponseEntity.ok(Map.of(
                "allowances", allowances,
                "deductions", deductions,
                "taxReliefs", taxReliefs
        ));
    }

    @PostMapping("/setup/employee/{employeeId}/allowances")
    @ResponseBody
    public ResponseEntity<List<EmployeeAllowanceResponse>> attachAllowancesToEmployee(
            @PathVariable Long employeeId,
            @RequestBody List<AllowanceAttachmentRequest> requests) {
        jobHrEmployeeAccessService.assertCanAccessEmployee(employeeId);

        List<Long> submittedIds = requests.stream()
                .map(AllowanceAttachmentRequest::getAllowanceId).toList();
        payrollSetupService.deactivateRemovedAllowancesFromEmployee(employeeId, submittedIds);
        return ResponseEntity.ok(payrollSetupService.addAllowancesToEmployee(employeeId, requests));
    }

    @PostMapping("/setup/employee/{employeeId}/deductions")
    @ResponseBody
    public ResponseEntity<List<EmployeeDeductionResponse>> attachDeductionsToEmployee(
            @PathVariable Long employeeId,
            @RequestBody List<DeductionAttachmentRequest> requests) {
        jobHrEmployeeAccessService.assertCanAccessEmployee(employeeId);
        System.out.println("Received request to attach deductions to employee ID: " + employeeId);
        List<Long> submittedIds = requests.stream()
                .map(DeductionAttachmentRequest::getDeductionId).toList();
        payrollSetupService.deactivateRemovedDeductionsFromEmployee(employeeId, submittedIds);
        return ResponseEntity.ok(payrollSetupService.addDeductionsToEmployee(employeeId, requests));
    }

    @PostMapping("/setup/employee/{employeeId}/taxreliefs")
    @ResponseBody
    public ResponseEntity<List<EmployeeTaxReliefResponse>> attachTaxReliefsToEmployee(
            @PathVariable Long employeeId,
            @RequestBody List<TaxReliefAttachmentRequest> requests) {
        jobHrEmployeeAccessService.assertCanAccessEmployee(employeeId);
        System.out.println("Received request to attach tax reliefs to employee ID: " + employeeId);
        List<Long> submittedIds = requests.stream()
                .map(TaxReliefAttachmentRequest::getTaxReliefId).toList();
        payrollSetupService.deactivateRemovedTaxReliefsFromEmployee(employeeId, submittedIds);
        return ResponseEntity.ok(payrollSetupService.addTaxReliefsToEmployee(employeeId, requests));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BULK CSV UPLOAD
    //  Returns 200 on success, 409 with a structured JSON body on duplicate
    //  emails (the UI can display the full conflict list to the user).
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/employees/upload-csv")
    @ResponseBody
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file) {
        try {
            employeeUploadService.uploadEmployees(file);
            return ResponseEntity.ok("Employees uploaded successfully");

        } catch (DuplicateEmailUploadException ex) {
            // Return the full list of conflicts — the front-end should display
            // every entry so the user can fix the CSV in one go.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error",            "DUPLICATE_EMAIL");
            body.put("message",          ex.getMessage());
            body.put("conflictingEmails", ex.getConflictingEmails());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EMPLOYEE DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/employee/dashboard")
    public String getEmployeeDashboard(Model model) {
        String   email         = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee      = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        PayrollRunDTO currentPayrollRun =
                payrollRunService.getEmployeePayrollRun(loginEmployee.getId(), 1L);

        System.out.println("Latest Pay Slip: " + currentPayrollRun);
        System.out.println("Logged in employee: " + loginEmployee);

        List<PaySlipDTO> previousPaySlip =
                paySlipService.getPaySlipsByEmployee(loginEmployee.getId());

        List<HistoricProcessInstance> completedProcesses =
                flowableTaskService.getCompletedProcessInstancesForAssignee("employeeAppraisalProcess");

        List<FlowableTaskDTO> tasks = flowableTaskService.getTasksForAssignee(
                String.valueOf(loginEmployee.getId()), "employeeAppraisalProcess");

        List<AppraisalTaskViewDTO> enrichedAppraisals = new ArrayList<>();
        for (FlowableTaskDTO task : tasks) {
            Map<String, Object> variables = task.getVariables();
            if (variables.containsKey("appraisalId")) {
                Long appraisalId = Long.valueOf(variables.get("appraisalId").toString());
                Optional<EmployeeAppraisal> appraisalOpt = appraisalService.findAppraisalById(appraisalId);
                if (appraisalOpt.isEmpty()) {
                    System.out.println("Skipping orphaned task, appraisalId not found: " + appraisalId);
                    continue;
                }
                enrichedAppraisals.add(new AppraisalTaskViewDTO(task, appraisalOpt.get()));
            }
        }
        enrichedAppraisals.forEach(a ->
                System.out.println("Task: " + a.getTask() + ", Appraisal: " + a.getAppraisal()));

        List<EmployeeAppraisal> employeeAppraisals =
                appraisalService.findAppraisalByEmployeeID(loginEmployee.getId());

        List<FutureEmployeeAllowanceDTO> futureAllowances = List.of();
        try {
            futureAllowances = payrollSetupService.getFutureAllowancesForEmployee(loginEmployee.getId());
        } catch (Exception ignored) {}

        model.addAttribute("tasks",              tasks);
        model.addAttribute("employeeAppraisals", employeeAppraisals);
        model.addAttribute("appraisalMap",
                employeeAppraisals.stream()
                        .collect(Collectors.toMap(EmployeeAppraisal::getId, ea -> ea)));
        model.addAttribute("previousPaySlip",  previousPaySlip);
        model.addAttribute("futureAllowances", futureAllowances);
        model.addAttribute("employee",         employee);
        model.addAttribute("latestPaySlip",    currentPayrollRun);
        model.addAttribute("title",            "Employee Dashboard");
        model.addAttribute("subTitle",
                "View your profile, performance metrics, and payroll information");
        return "employees/dashboard";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EMPLOYEE PROFILE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("employee/profile")
    public String getEmployeeProfile(Model model) {
        String   email         = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee      = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        PayrollRun currentPayrollRun =
                paySlipService.getEmployeeCurrentPayrollRun(1L, loginEmployee.getId());

        model.addAttribute("employee",     employee);
        model.addAttribute("latestPaySlip", currentPayrollRun);
        model.addAttribute("title",        "Employee Profile");
        model.addAttribute("subTitle",
                "View and update your personal information, job details, and performance data");
        return "employees/profile";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MISC EMPLOYEE PAGES
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("employee/promotions")
    public String getPromotions(Model model) {
        model.addAttribute("title",    "Promotions");
        model.addAttribute("subTitle", "View your promotion history and upcoming opportunities");
        return "employees/promotions";
    }

    @GetMapping("employee/payroll")
    public String getPayroll(Model model) {
        String   email         = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        PayrollRunDTO latestPaySlip =
                payrollRunService.getEmployeePayrollRun(loginEmployee.getId(), 1L);
        List<PaySlipDTO> previousPaySlip =
                paySlipService.getPaySlipsByEmployee(loginEmployee.getId());
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());

        List<FutureEmployeeAllowanceDTO> futureAllowances = List.of();
        try {
            futureAllowances = payrollSetupService.getFutureAllowancesForEmployee(loginEmployee.getId());
        } catch (Exception ignored) {}

        model.addAttribute("previousPaySlip",  previousPaySlip);
        model.addAttribute("latestPaySlip",    latestPaySlip);
        model.addAttribute("employee",         employee);
        model.addAttribute("futureAllowances", futureAllowances);
        model.addAttribute("title",            "Payroll");
        model.addAttribute("subTitle",         "View your pay stubs, tax information, and benefits");
        return "employees/payroll";
    }

    @GetMapping("employee/leave")
    public String getLeave(Model model) {
        model.addAttribute("title",    "Leave Management");
        model.addAttribute("subTitle",
                "View your leave balance, request time off, and track your leave history");
        return "employees/leave";
    }

    @GetMapping("employee/performance")
    public String getPerformance(Model model) {
        String   email         = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);

        List<HistoricProcessInstance> completedProcesses =
                flowableTaskService.getCompletedProcessInstancesForAssignee("employeeAppraisalProcess");

        List<FlowableTaskDTO> tasks = flowableTaskService.getTasksForAssignee(
                String.valueOf(loginEmployee.getId()), "employeeAppraisalProcess");

        List<AppraisalTaskViewDTO> enrichedAppraisals = new ArrayList<>();
        for (FlowableTaskDTO task : tasks) {
            Map<String, Object> variables = task.getVariables();
            if (variables.containsKey("appraisalId")) {
                Long appraisalId = Long.valueOf(variables.get("appraisalId").toString());
                Optional<EmployeeAppraisal> appraisalOpt = appraisalService.findAppraisalById(appraisalId);
                if (appraisalOpt.isEmpty()) {
                    System.out.println("Skipping orphaned task, appraisalId not found: " + appraisalId);
                    continue;
                }
                enrichedAppraisals.add(new AppraisalTaskViewDTO(task, appraisalOpt.get()));
            }
        }
        enrichedAppraisals.forEach(a ->
                System.out.println("Task: " + a.getTask() + ", Appraisal: " + a.getAppraisal()));

        List<EmployeeAppraisal> employeeAppraisals =
                appraisalService.findAppraisalByEmployeeID(loginEmployee.getId());

        model.addAttribute("tasks",              tasks);
        model.addAttribute("employeeAppraisals", employeeAppraisals);
        model.addAttribute("appraisalMap",
                employeeAppraisals.stream()
                        .collect(Collectors.toMap(EmployeeAppraisal::getId, ea -> ea)));
        model.addAttribute("title",    "Performance Management");
        model.addAttribute("subTitle",
                "View your performance reviews, set goals, and track your progress");
        return "employees/kpi";
    }

    @PostMapping("/employee/self-review")
    public String submitSelfReview(@RequestParam String taskId,
                                   @RequestParam Map<String, Object> formParams) {
        System.out.println("Finalizing appraisal with ID: " + taskId
                + " and form parameters: " + formParams);
        formParams.put("selfComplete", true);
        flowableTaskService.completeTask(taskId, formParams);
        return "redirect:/employee/performance";
    }

    @GetMapping("employee/documents")
    public String getDocuments(Model model) {
        String   email         = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        model.addAttribute("employee", loginEmployee);
        model.addAttribute("title",    "Document Management");
        model.addAttribute("subTitle",
                "View and manage your important documents such as contracts and certifications");
        return "employees/documents";
    }

    @PostMapping("/add-emergency-contact")
    public String saveEmployee(EmployeeDTO dto) {
        employeeService.updateEmployee(dto.getId(), dto);
        return "redirect:/employee/profile";
    }

    @PostMapping("/update-personal-info")
    public String updatePersonalInfo(@ModelAttribute EmployeeDTO dto) {
        employeeService.updatePersonalInfo(dto.getId(), dto);
        return "redirect:/employee/profile";
    }

    @PostMapping("/update/bank-info")
    public String updateBankInfo(@ModelAttribute EmployeeDTO dto) {
        employeeService.updateBankDetails(dto.getId(), dto);
        return "redirect:/employee/profile";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EMPLOYEE DOCUMENTS
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/employees/{id}/documents/upload")
    @ResponseBody
    public ResponseEntity<?> uploadDoc(@PathVariable Long id,
                                       @RequestParam("documentName") String name,
                                       @RequestParam("file") MultipartFile file) {
        jobHrEmployeeAccessService.assertCanAccessEmployee(id);
        try {
            documentService.uploadDocument(id, name, file);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/employees/{id}/documents")
    @ResponseBody
    public List<EmployeeDocumentDTO> listDocs(@PathVariable Long id) {
        jobHrEmployeeAccessService.assertCanAccessEmployee(id);
        return documentService.getEmployeeDocuments(id);
    }

    @GetMapping("/documents/view/{docId}")
    public ResponseEntity<byte[]> viewDoc(@PathVariable Long docId) {
        EmployeeDocument doc = documentService.getDocumentFile(docId);
        if (doc.getEmployee() != null) {
            jobHrEmployeeAccessService.assertCanAccessEmployee(doc.getEmployee().getId());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getFileName() + "\"")
                .body(doc.getFileData());
    }

    @GetMapping("/documents/download/{docId}")
    public ResponseEntity<byte[]> downloadDoc(@PathVariable Long docId) {
        EmployeeDocument doc = documentService.getDocumentFile(docId);
        if (doc.getEmployee() != null) {
            jobHrEmployeeAccessService.assertCanAccessEmployee(doc.getEmployee().getId());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                .body(doc.getFileData());
    }

    @DeleteMapping("/documents/{docId}")
    @ResponseBody
    public ResponseEntity<?> deleteDoc(@PathVariable Long docId) {
        EmployeeDocument doc = documentService.getDocumentFile(docId);
        if (doc.getEmployee() != null) {
            jobHrEmployeeAccessService.assertCanAccessEmployee(doc.getEmployee().getId());
        }
        documentService.deleteDocument(docId);
        return ResponseEntity.ok().build();
    }
}
