package com.justjava.humanresource.request.controller;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.request.dto.FinanceRequestActionCommand;
import com.justjava.humanresource.request.dto.FinanceRequestForwardCommand;
import com.justjava.humanresource.request.dto.RequestLookupOption;
import com.justjava.humanresource.request.dto.WorkflowRequestDetailDTO;
import com.justjava.humanresource.request.dto.WorkflowRequestFinanceActionDTO;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.entity.WorkflowRequestAttachment;
import com.justjava.humanresource.request.service.WorkflowRequestAttachmentService;
import com.justjava.humanresource.request.service.WorkflowRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finance-only API for requests. Deliberately separate from /api/requests so Finance access never
 * broadens employee or HR endpoints. Reads (Step 2) plus final decisions: approve, reject, return (Step 4).
 */
@RestController
@RequestMapping("/api/finance/requests")
@RequiredArgsConstructor
public class FinanceWorkflowRequestController {

    private final WorkflowRequestService service;
    private final WorkflowRequestAttachmentService attachments;
    private final AuthenticationManager auth;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    @GetMapping
    public List<WorkflowRequest> list() {
        requireFinance();
        return service.financeRequests();
    }

    /** Id-to-name maps used by the Finance pages to label requesters, approvers and departments. */
    @GetMapping("/lookups")
    public Lookups lookups() {
        requireFinance();
        Map<Long, String> employees = new HashMap<>();
        for (Employee e : employeeRepository.findAllVisible()) {
            employees.putIfAbsent(e.getId(), e.getFullName());
        }
        Map<Long, String> departments = new HashMap<>();
        for (Department d : departmentRepository.findAll()) {
            departments.putIfAbsent(d.getId(), d.getName());
        }
        return new Lookups(employees, departments);
    }

    @GetMapping("/{id}")
    public WorkflowRequestDetailDTO details(@PathVariable Long id) {
        requireFinance();
        return service.financeDetails(id);
    }

    @PostMapping("/{id}/approve")
    public WorkflowRequest approve(@PathVariable Long id,
                                   @Valid @RequestBody(required = false) FinanceRequestActionCommand command) {
        requireFinance();
        return service.financeApprove(id, comment(command));
    }

    @PostMapping("/{id}/reject")
    public WorkflowRequest reject(@PathVariable Long id,
                                  @Valid @RequestBody(required = false) FinanceRequestActionCommand command) {
        requireFinance();
        return service.financeReject(id, comment(command));
    }

    @PostMapping("/{id}/return")
    public WorkflowRequest returnForCorrection(@PathVariable Long id,
                                               @Valid @RequestBody(required = false) FinanceRequestActionCommand command) {
        requireFinance();
        return service.financeReturn(id, comment(command));
    }

    /** Forward is a hand-off, not a decision: it never consumes Finance's one final decision. */
    @PostMapping("/{id}/forward")
    public WorkflowRequest forward(@PathVariable Long id,
                                   @Valid @RequestBody FinanceRequestForwardCommand command) {
        requireFinance();
        return service.financeForward(id, command.getToEmployeeId(), command.getComment());
    }

    @GetMapping("/{id}/forward-recipients")
    public List<RequestLookupOption> forwardRecipients(@PathVariable Long id) {
        requireFinance();
        return service.financeForwardRecipients(id);
    }

    /** Append-only Finance action history (forwards, final decisions, recipient decisions). */
    @GetMapping("/{id}/actions")
    public List<WorkflowRequestFinanceActionDTO> actions(@PathVariable Long id) {
        requireFinance();
        return service.financeActions(id);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        return attachment(id, attachmentId, false);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/view")
    public ResponseEntity<Resource> view(@PathVariable Long id, @PathVariable Long attachmentId) {
        return attachment(id, attachmentId, true);
    }

    private ResponseEntity<Resource> attachment(Long id, Long attachmentId, boolean inline) {
        requireFinance();
        service.requireFinanceRead(id);
        WorkflowRequestAttachment a = attachments.get(id, attachmentId);
        if (!Objects.equals(a.getWorkflowRequestId(), id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found.");
        }
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(a.getOriginalFilename()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .contentLength(a.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(attachments.load(id, attachmentId));
    }

    private static String comment(FinanceRequestActionCommand command) {
        return command == null ? null : command.getComment();
    }

    private void requireFinance() {
        if (!auth.isFinancialOfficer()) {
            throw new AccessDeniedException("Financial officer access is required.");
        }
    }

    public record Lookups(Map<Long, String> employees, Map<Long, String> departments) {}
}