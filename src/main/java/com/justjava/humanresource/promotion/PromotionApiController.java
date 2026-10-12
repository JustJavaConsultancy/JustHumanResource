package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.promotion.dto.*;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.entity.PromotionAttachment;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import com.justjava.humanresource.promotion.enums.PromotionPayrollHandoffStatus;
import com.justjava.humanresource.promotion.service.PromotionAttachmentService;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionConfigurationService;
import com.justjava.humanresource.promotion.service.PromotionEligibilityService;
import com.justjava.humanresource.promotion.service.PromotionPayrollHandoffService;
import com.justjava.humanresource.promotion.service.PromotionReportService;
import com.justjava.humanresource.promotion.service.PromotionService;
import com.justjava.humanresource.promotion.service.PromotionWorkflowService;
import com.justjava.humanresource.promotion.service.PromotionApprovalReassignmentService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@PreAuthorize("@promotionSecurity.canAccessPromotionWorkArea()")
@RequiredArgsConstructor
public class PromotionApiController {

    private final PromotionService promotionService;
    private final PromotionWorkflowService promotionWorkflowService;
    private final PromotionAttachmentService promotionAttachmentService;
    private final PromotionConfigurationService promotionConfigurationService;
    private final PromotionEligibilityService promotionEligibilityService;
    private final PromotionReportService promotionReportService;
    private final PromotionPayrollHandoffService promotionPayrollHandoffService;
    private final PromotionAuthorizationService promotionAuthorizationService;
    private final PromotionApprovalReassignmentService promotionApprovalReassignmentService;
    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;

    @GetMapping("/dashboard")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public PromotionDashboardResponse dashboard() {
        return promotionService.dashboard();
    }

    @GetMapping("/my/dashboard")
    public PromotionDashboardResponse myDashboard() {
        return promotionService.myDashboard();
    }

    @GetMapping
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public List<PromotionSummaryResponse> list(@RequestParam(required = false) PromotionStatus status) {
        return promotionService.list(status);
    }

    @GetMapping("/my")
    public List<PromotionSummaryResponse> myPromotions(@RequestParam(required = false) PromotionStatus status) {
        return promotionService.myPromotions(status);
    }

    @PostMapping
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse create(@Valid @RequestBody PromotionRequestCommand command) {
        return promotionService.createDraft(command);
    }

    @PostMapping("/salary-impact")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public PromotionSalaryImpactResponse salaryImpact(@Valid @RequestBody PromotionRequestCommand command) {
        return promotionService.salaryImpact(command);
    }

    @GetMapping("/{id}/approval-route-preview")
    public PromotionApprovalRoutePreviewResponse approvalRoutePreview(@PathVariable Long id) {
        return promotionService.approvalRoutePreview(id);
    }

    @GetMapping("/reports/status-summary")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public List<PromotionStatusReportResponse> statusReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return promotionReportService.statusSummary(startDate, endDate);
    }

    @GetMapping("/reports/department-summary")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public List<PromotionDepartmentReportResponse> departmentReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return promotionReportService.departmentSummary(startDate, endDate);
    }

    @GetMapping("/reports/salary-impact")
    @PreAuthorize("@promotionSecurity.canViewPromotionSalaryData()")
    public PromotionSalaryImpactReportResponse salaryImpactReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return promotionReportService.salaryImpact(startDate, endDate);
    }

    @GetMapping("/reports/approval-duration")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public PromotionApprovalDurationReportResponse approvalDurationReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return promotionReportService.approvalDuration(startDate, endDate);
    }

    @GetMapping("/reports/details")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public PromotionReportDetailPageResponse reportDetails(
            @RequestParam(required = false) PromotionStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) LocalDate approvalStartDate,
            @RequestParam(required = false) LocalDate approvalEndDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return promotionReportService.details(
                status, departmentId, startDate, endDate, approvalStartDate, approvalEndDate, page, size);
    }

    @GetMapping("/payroll-handoffs")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public List<PromotionPayrollHandoffResponse> payrollHandoffs(
            @RequestParam(required = false) PromotionPayrollHandoffStatus status) {
        return promotionPayrollHandoffService.list(status);
    }

    @PostMapping("/payroll-handoffs/{handoffId}/acknowledge")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public PromotionPayrollHandoffResponse acknowledgePayrollHandoff(
            @PathVariable Long handoffId,
            @RequestBody(required = false) PromotionDecisionCommand command) {
        return promotionPayrollHandoffService.acknowledge(handoffId, command);
    }

    @PostMapping("/payroll-handoffs/{handoffId}/apply")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public PromotionPayrollHandoffResponse applyPayrollHandoff(
            @PathVariable Long handoffId,
            @RequestBody(required = false) PromotionDecisionCommand command) {
        return promotionPayrollHandoffService.apply(handoffId, command);
    }

    @PostMapping("/payroll-handoffs/{handoffId}/cancel")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public PromotionPayrollHandoffResponse cancelPayrollHandoff(
            @PathVariable Long handoffId,
            @RequestBody(required = false) PromotionDecisionCommand command) {
        return promotionPayrollHandoffService.cancel(handoffId, command);
    }

    @GetMapping(value = "/reports/status-summary.csv", produces = "text/csv")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public ResponseEntity<String> statusReportCsv(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return csv("promotion-status-summary.csv", promotionReportService.statusSummaryCsv(startDate, endDate));
    }

    @GetMapping(value = "/reports/department-summary.csv", produces = "text/csv")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public ResponseEntity<String> departmentReportCsv(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return csv("promotion-department-summary.csv", promotionReportService.departmentSummaryCsv(startDate, endDate));
    }

    @GetMapping(value = "/reports/salary-impact.csv", produces = "text/csv")
    @PreAuthorize("@promotionSecurity.canViewPromotionSalaryData()")
    public ResponseEntity<String> salaryImpactReportCsv(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return csv("promotion-salary-impact.csv", promotionReportService.salaryImpactCsv(startDate, endDate));
    }

    @GetMapping(value = "/reports/approval-duration.csv", produces = "text/csv")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public ResponseEntity<String> approvalDurationReportCsv(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return csv("promotion-approval-duration.csv", promotionReportService.approvalDurationCsv(startDate, endDate));
    }

    @GetMapping("/{id}")
    public PromotionDetailResponse detail(@PathVariable Long id) {
        return promotionService.getDetail(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse update(@PathVariable Long id,
                                          @Valid @RequestBody PromotionRequestCommand command) {
        return promotionService.updateDraft(id, command);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse submit(@PathVariable Long id) {
        return promotionService.submit(id);
    }

    @PostMapping("/{id}/approve")
    public PromotionDetailResponse approve(@PathVariable Long id,
                                           @RequestBody(required = false) PromotionDecisionCommand command) {
        return promotionService.approve(id, command);
    }

    @PostMapping("/{id}/reject")
    public PromotionDetailResponse reject(@PathVariable Long id,
                                          @RequestBody PromotionDecisionCommand command) {
        return promotionService.reject(id, command);
    }

    @PostMapping("/{id}/return")
    public PromotionDetailResponse returnForCorrection(@PathVariable Long id,
                                                       @RequestBody PromotionDecisionCommand command) {
        return promotionService.returnForCorrection(id, command);
    }

    @PostMapping("/{id}/eligibility-checks/{checkId}/override")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse overrideEligibilityCheck(
            @PathVariable Long id,
            @PathVariable Long checkId,
            @Valid @RequestBody PromotionEligibilityOverrideCommand command) {
        promotionEligibilityService.override(id, checkId, command);
        return promotionService.getDetail(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse cancel(@PathVariable Long id,
                                          @RequestBody(required = false) PromotionDecisionCommand command) {
        return promotionService.cancel(id, command);
    }

    @GetMapping("/{id}/attachments")
    public List<PromotionAttachmentResponse> attachments(@PathVariable Long id) {
        return promotionAttachmentService.list(id);
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionAttachmentResponse uploadAttachment(@PathVariable Long id,
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam(required = false) PromotionAttachmentType documentType,
                                                       @RequestParam(defaultValue = "false") boolean required) {
        return promotionAttachmentService.upload(id, file, documentType, required);
    }

    @PostMapping("/{id}/attachments/generate-letter")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionAttachmentResponse generateLetter(@PathVariable Long id,
                                                      @RequestParam(required = false) PromotionAttachmentType documentType) {
        return promotionAttachmentService.generateLetter(id, documentType);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id,
                                                       @PathVariable Long attachmentId) {
        PromotionAttachment attachment = promotionAttachmentService.get(id, attachmentId);
        Resource resource = promotionAttachmentService.download(id, attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getFileName().replace("\"", "") + "\"")
                .header(HttpHeaders.CONTENT_TYPE, attachment.getContentType())
                .body(resource);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public void deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        promotionAttachmentService.delete(id, attachmentId);
    }

    @GetMapping("/tasks")
    public List<FlowableTaskDTO> tasks() {
        return promotionWorkflowService.getMyTasks();
    }

    @GetMapping("/configuration")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionConfigurationResponse configuration() {
        return promotionConfigurationService.getActiveConfiguration();
    }

    @GetMapping("/configuration/history")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public List<PromotionConfigurationResponse> configurationHistory() {
        return promotionConfigurationService.history();
    }

    @PutMapping("/configuration")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionConfigurationResponse updateConfiguration(
            @Valid @RequestBody PromotionConfigurationCommand command) {
        return promotionConfigurationService.saveActiveConfiguration(command);
    }

    @PostMapping("/configuration/{configurationId}/deactivate")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionConfigurationResponse deactivateConfiguration(@PathVariable Long configurationId) {
        return promotionConfigurationService.deactivate(configurationId);
    }

    @PostMapping("/tasks/{taskId}/approve")
    public void approveTask(@PathVariable String taskId,
                            @RequestBody(required = false) PromotionDecisionCommand command) {
        promotionWorkflowService.approve(taskId, command);
    }

    @PostMapping("/tasks/{taskId}/reject")
    public void rejectTask(@PathVariable String taskId,
                           @RequestBody PromotionDecisionCommand command) {
        promotionWorkflowService.reject(taskId, command);
    }

    @PostMapping("/tasks/{taskId}/return")
    public void returnTask(@PathVariable String taskId,
                           @RequestBody PromotionDecisionCommand command) {
        promotionWorkflowService.returnForCorrection(taskId, command);
    }

    @PostMapping("/{id}/approval-steps/{stepId}/reassign")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse reassignApproval(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @Valid @RequestBody PromotionReassignmentCommand command) {
        return promotionApprovalReassignmentService.reassign(id, stepId, command);
    }

    @PostMapping("/{id}/approval-steps/{stepId}/delegate")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse delegateApproval(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @Valid @RequestBody PromotionReassignmentCommand command) {
        return promotionApprovalReassignmentService.delegate(id, stepId, command);
    }

    @PostMapping("/{id}/implement")
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse implement(@PathVariable Long id,
                                             @RequestBody(required = false) PromotionImplementationCommand command) {
        return promotionService.implement(id, command);
    }

    @GetMapping("/reference/employees")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public List<EmployeeOption> employees() {
        promotionAuthorizationService.requireHrFinanceOrAdmin();
        return employeeRepository.findAllVisible().stream()
                .map(e -> new EmployeeOption(
                        e.getId(),
                        e.getEmployeeNumber(),
                        e.getFullName(),
                        e.getEmail(),
                        e.getDepartment() == null ? null : e.getDepartment().getName(),
                        e.getJobStep() == null ? null : e.getJobStep().getName(),
                        e.getJobStep() == null ? BigDecimal.ZERO : e.getJobStep().getGrossSalary()
                ))
                .toList();
    }

    @GetMapping("/reference/job-steps")
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public List<JobStepOption> jobSteps() {
        promotionAuthorizationService.requireHrFinanceOrAdmin();
        return jobStepRepository.findAllWithJobGrade().stream()
                .map(j -> new JobStepOption(
                        j.getId(),
                        j.getName(),
                        j.getJobGrade() == null ? null : j.getJobGrade().getName(),
                        j.getDepartment() == null ? null : j.getDepartment().getName(),
                        j.getGrossSalary()
                ))
                .toList();
    }

    public record EmployeeOption(Long id, String employeeNumber, String name, String email,
                                 String departmentName, String jobStepName, BigDecimal grossSalary) {
    }

    public record JobStepOption(Long id, String name, String jobGradeName, String departmentName,
                                BigDecimal grossSalary) {
    }

    private ResponseEntity<String> csv(String fileName, String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }
}
