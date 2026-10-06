package com.justjava.humanresource.loan.controller;

import com.justjava.humanresource.loan.dto.EmployeeLoanDashboardResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationCommand;
import com.justjava.humanresource.loan.dto.LoanApplicationDetailResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationEditResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationSummaryResponse;
import com.justjava.humanresource.loan.dto.LoanApprovalTaskResponse;
import com.justjava.humanresource.loan.dto.LoanAttachmentResponse;
import com.justjava.humanresource.loan.dto.LoanProductSummaryResponse;
import com.justjava.humanresource.loan.dto.LoanRepaymentPreviewRequest;
import com.justjava.humanresource.loan.dto.LoanRepaymentPreviewResponse;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanAttachmentType;
import com.justjava.humanresource.loan.service.EmployeeLoanApplicationService;
import com.justjava.humanresource.loan.service.LoanApprovalService;
import com.justjava.humanresource.loan.service.LoanAttachmentService;
import com.justjava.humanresource.loan.service.LoanAttachmentService.LoanAttachmentDownload;
import com.justjava.humanresource.loan.service.LoanProductService;
import com.justjava.humanresource.loan.service.LoanRepaymentCalculationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * The logged-in employee's own loans, plus the custom-approver task actions for loans assigned to them.
 * Ownership and assignment are enforced in the services, so this class holds no access logic.
 */
@RestController
@RequestMapping("/api/employee/loans")
@RequiredArgsConstructor
public class EmployeeLoanController {

    private final EmployeeLoanApplicationService applicationService;
    private final LoanProductService productService;
    private final LoanRepaymentCalculationService calculationService;
    private final LoanAttachmentService attachmentService;
    private final LoanApprovalService approvalService;

    /** Optional comment for the task actions. Mandatory for reject/return; the service enforces it. */
    public record TaskComment(@Size(max = 2000) String comment) {
    }

    // ------------------------------------------------------------------ read

    @GetMapping("/dashboard")
    public EmployeeLoanDashboardResponse dashboard() {
        return applicationService.getMyDashboard();
    }

    @GetMapping("/products")
    public List<LoanProductSummaryResponse> products() {
        return productService.listActive();
    }

    @GetMapping
    public List<LoanApplicationSummaryResponse> list(@RequestParam(required = false) LoanApplicationStatus status) {
        return applicationService.listMine(status);
    }

    @GetMapping("/{id}")
    public LoanApplicationDetailResponse detail(@PathVariable Long id) {
        return applicationService.getEmployeeDetail(id);
    }

    @GetMapping("/{id}/edit")
    public LoanApplicationEditResponse editDetails(@PathVariable Long id) {
        return applicationService.getForEdit(id);
    }

    /** Detail for an employee assigned as custom approver; not available to anyone else. */
    @GetMapping("/{id}/approver-detail")
    public LoanApplicationDetailResponse approverDetail(@PathVariable Long id) {
        return applicationService.getCustomApproverDetail(id);
    }

    // ------------------------------------------------------------------ application lifecycle

    @PostMapping("/drafts")
    public ResponseEntity<LoanApplicationResponse> createDraft(@Valid @RequestBody LoanApplicationCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.createDraft(command));
    }

    @PutMapping("/{id}")
    public LoanApplicationResponse update(@PathVariable Long id, @Valid @RequestBody LoanApplicationCommand command) {
        return applicationService.updateApplication(id, command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable Long id) {
        applicationService.deleteDraft(id);
    }

    @PostMapping("/{id}/submit")
    public LoanApplicationResponse submit(@PathVariable Long id) {
        return applicationService.submit(id);
    }

    @PostMapping("/{id}/cancel")
    public LoanApplicationResponse cancel(@PathVariable Long id) {
        return applicationService.cancel(id);
    }

    /** Calculator: validates against the product's limits and returns totals and the schedule preview. */
    @PostMapping("/preview")
    public LoanRepaymentPreviewResponse preview(@Valid @RequestBody LoanRepaymentPreviewRequest request) {
        return calculationService.preview(request);
    }

    // ------------------------------------------------------------------ attachments

    @GetMapping("/{id}/attachments")
    public List<LoanAttachmentResponse> listAttachments(@PathVariable Long id) {
        return attachmentService.list(id);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LoanAttachmentResponse> upload(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "SUPPORTING_DOCUMENT") LoanAttachmentType attachmentType) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.upload(id, file, attachmentType));
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        return file(attachmentService.download(id, attachmentId), false);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/view")
    public ResponseEntity<Resource> view(@PathVariable Long id, @PathVariable Long attachmentId) {
        return file(attachmentService.download(id, attachmentId), true);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        attachmentService.delete(id, attachmentId);
    }

    // ------------------------------------------------------------------ custom approver tasks

    @GetMapping("/approval-tasks")
    public List<LoanApprovalTaskResponse> approvalTasks() {
        return approvalService.listMyCustomTasks();
    }

    @PostMapping("/approval-tasks/{taskId}/approve")
    public void approve(@PathVariable String taskId, @Valid @RequestBody(required = false) TaskComment body) {
        approvalService.approveCustom(taskId, comment(body));
    }

    @PostMapping("/approval-tasks/{taskId}/reject")
    public void reject(@PathVariable String taskId, @Valid @RequestBody(required = false) TaskComment body) {
        approvalService.rejectCustom(taskId, comment(body));
    }

    @PostMapping("/approval-tasks/{taskId}/return")
    public void returnForCorrection(@PathVariable String taskId, @Valid @RequestBody(required = false) TaskComment body) {
        approvalService.returnCustom(taskId, comment(body));
    }

    // ------------------------------------------------------------------ helpers

    private static String comment(TaskComment body) {
        return body == null ? null : body.comment();
    }

    private static ResponseEntity<Resource> file(LoanAttachmentDownload d, boolean inline) {
        MediaType type = d.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(d.contentType());
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(d.filename()).build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(d.resource());
    }
}
