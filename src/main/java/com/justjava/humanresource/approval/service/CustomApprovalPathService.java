package com.justjava.humanresource.approval.service;

import com.justjava.humanresource.approval.dto.ApprovalPathCommand;
import com.justjava.humanresource.approval.dto.ApprovalPathResponse;
import com.justjava.humanresource.approval.dto.RequestTypeApprovalRouteCommand;
import com.justjava.humanresource.approval.entity.CustomApprovalPath;
import com.justjava.humanresource.approval.entity.CustomApprovalPathStep;
import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.approval.repository.CustomApprovalPathRepository;
import com.justjava.humanresource.approval.repository.CustomApprovalPathStepRepository;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.request.entity.WorkflowRequestType;
import com.justjava.humanresource.request.repository.WorkflowRequestTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomApprovalPathService {
    private final CustomApprovalPathRepository pathRepository;
    private final CustomApprovalPathStepRepository stepRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkflowRequestTypeRepository requestTypeRepository;
    private final AuthenticationManager auth;

    @Transactional(readOnly = true)
    public List<ApprovalPathResponse> list() {
        requireHrAdmin();
        return pathRepository.findAllByOrderByNameAsc().stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalPathResponse> listEnabled() {
        requireHrAdmin();
        return pathRepository.findByEnabledTrueOrderByNameAsc().stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalPathResponse get(Long id) {
        requireHrAdmin();
        return response(path(id));
    }

    @Transactional
    public ApprovalPathResponse create(ApprovalPathCommand command) {
        requireHrAdmin();
        validateNameAvailable(command.getName(), null);
        validateSteps(command.getSteps());
        CustomApprovalPath path = new CustomApprovalPath();
        apply(path, command);
        path = pathRepository.save(path);
        saveSteps(path.getId(), command.getSteps());
        return response(path);
    }

    @Transactional
    public ApprovalPathResponse update(Long id, ApprovalPathCommand command) {
        requireHrAdmin();
        CustomApprovalPath path = path(id);
        validateNameAvailable(command.getName(), id);
        validateSteps(command.getSteps());
        apply(path, command);
        path = pathRepository.save(path);
        stepRepository.deleteByCustomApprovalPathId(id);
        saveSteps(id, command.getSteps());
        return response(path);
    }

    @Transactional
    public ApprovalPathResponse setEnabled(Long id, boolean enabled) {
        requireHrAdmin();
        CustomApprovalPath path = path(id);
        path.setEnabled(enabled);
        return response(pathRepository.save(path));
    }

    @Transactional
    public WorkflowRequestType updateRequestTypeRoute(String code, RequestTypeApprovalRouteCommand command) {
        requireHrAdmin();
        WorkflowRequestType type = requestTypeRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Request type not found."));
        ApprovalRouteType routeType = command.getApprovalRouteType();
        if (routeType == ApprovalRouteType.CUSTOM) {
            if (command.getCustomApprovalPathId() == null) {
                throw new IllegalArgumentException("Custom approval path is required.");
            }
            CustomApprovalPath path = path(command.getCustomApprovalPathId());
            if (!path.isEnabled()) {
                throw new IllegalArgumentException("Cannot assign a disabled custom approval path.");
            }
            if (stepRepository.findByCustomApprovalPathIdOrderBySequenceNo(path.getId()).isEmpty()) {
                throw new IllegalArgumentException("Cannot assign an empty custom approval path.");
            }
            type.setCustomApprovalPathId(path.getId());
        } else {
            type.setCustomApprovalPathId(null);
        }
        type.setApprovalRouteType(routeType);
        return requestTypeRepository.save(type);
    }

    private ApprovalPathResponse response(CustomApprovalPath path) {
        return ApprovalPathResponse.from(path, stepRepository.findByCustomApprovalPathIdOrderBySequenceNo(path.getId()));
    }

    private CustomApprovalPath path(Long id) {
        return pathRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Custom approval path not found."));
    }

    private void apply(CustomApprovalPath path, ApprovalPathCommand command) {
        path.setName(command.getName().trim());
        path.setDescription(command.getDescription());
        path.setEnabled(command.isEnabled());
    }

    private void saveSteps(Long pathId, List<ApprovalPathCommand.Step> steps) {
        int sequence = 1;
        for (ApprovalPathCommand.Step commandStep : steps) {
            CustomApprovalPathStep step = new CustomApprovalPathStep();
            step.setCustomApprovalPathId(pathId);
            step.setSequenceNo(sequence++);
            step.setApproverEmployeeId(commandStep.getApproverEmployeeId());
            stepRepository.save(step);
        }
    }

    private void validateNameAvailable(String name, Long currentId) {
        pathRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("An approval path with this name already exists.");
            }
        });
    }

    private void validateSteps(List<ApprovalPathCommand.Step> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("At least one approver is required.");
        }
        Set<Long> seen = new HashSet<>();
        for (ApprovalPathCommand.Step step : steps) {
            Long employeeId = step.getApproverEmployeeId();
            if (!seen.add(employeeId)) {
                throw new IllegalArgumentException("Approvers cannot be duplicated in the same path.");
            }
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new IllegalArgumentException("Approver employee not found: " + employeeId));
            if (employee.getStatus() != RecordStatus.ACTIVE || employee.isRestrictedVisibility()) {
                throw new IllegalArgumentException("Approver must be an active visible employee: " + employeeId);
            }
        }
    }

    private void requireHrAdmin() {
        if (!(auth.isHumanResource() || auth.isJobHR() || auth.isAdmin() || auth.isRestrictedHr())) {
            throw new IllegalStateException("HR or administrator access is required.");
        }
    }
}
