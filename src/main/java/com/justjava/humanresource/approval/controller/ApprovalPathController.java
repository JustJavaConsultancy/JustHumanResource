package com.justjava.humanresource.approval.controller;

import com.justjava.humanresource.approval.dto.ApprovalPathCommand;
import com.justjava.humanresource.approval.dto.ApprovalPathResponse;
import com.justjava.humanresource.approval.dto.RequestTypeApprovalRouteCommand;
import com.justjava.humanresource.approval.service.CustomApprovalPathService;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.request.dto.RequestLookupOption;
import com.justjava.humanresource.request.entity.WorkflowRequestType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/approval-paths")
@RequiredArgsConstructor
public class ApprovalPathController {
    private final CustomApprovalPathService service;
    private final EmployeeRepository employeeRepository;
    private final AuthenticationManager auth;

    @GetMapping
    public List<ApprovalPathResponse> list() {
        return service.list();
    }

    @GetMapping("/enabled")
    public List<ApprovalPathResponse> enabled() {
        return service.listEnabled();
    }

    @GetMapping("/{id}")
    public ApprovalPathResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalPathResponse create(@Valid @RequestBody ApprovalPathCommand command) {
        return service.create(command);
    }

    @PutMapping("/{id}")
    public ApprovalPathResponse update(@PathVariable Long id, @Valid @RequestBody ApprovalPathCommand command) {
        return service.update(id, command);
    }

    @PostMapping("/{id}/enable")
    public ApprovalPathResponse enable(@PathVariable Long id) {
        return service.setEnabled(id, true);
    }

    @PostMapping("/{id}/disable")
    public ApprovalPathResponse disable(@PathVariable Long id) {
        return service.setEnabled(id, false);
    }

    @GetMapping("/approvers")
    public List<RequestLookupOption> approvers() {
        requireHrAdmin();
        return employeeRepository.findAllVisible().stream()
                .filter(e -> e.getStatus() == RecordStatus.ACTIVE)
                .sorted(Comparator.comparing(Employee::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(e -> new RequestLookupOption(e.getId(), e.getEmployeeNumber() + " - " + e.getFullName()))
                .toList();
    }

    @PutMapping("/request-types/{code}")
    public WorkflowRequestType updateRequestTypeRoute(
            @PathVariable String code,
            @Valid @RequestBody RequestTypeApprovalRouteCommand command
    ) {
        return service.updateRequestTypeRoute(code, command);
    }

    private void requireHrAdmin() {
        if (!(auth.isHumanResource() || auth.isJobHR() || auth.isAdmin() || auth.isRestrictedHr())) {
            throw new IllegalStateException("HR or administrator access is required.");
        }
    }
}
