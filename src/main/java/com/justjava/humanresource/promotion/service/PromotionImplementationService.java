package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.dto.PromotionImplementationCommand;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PromotionImplementationService {

    private final PromotionRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeePositionHistoryRepository positionHistoryRepository;
    private final PromotionActivityService activityService;
    private final PromotionCurrentUserService currentUserService;
    private final PromotionNotificationService notificationService;
    private final PromotionPayrollHandoffService payrollHandoffService;
    private final PromotionRequiredDocumentService requiredDocumentService;

    @Transactional
    public PromotionRequest implement(PromotionRequest request, PromotionImplementationCommand command) {
        if (request.getStatus() == PromotionStatus.IMPLEMENTED) {
            reconcileImplementedRequest(request);
            return request;
        }
        if (request.getStatus() != PromotionStatus.APPROVED) {
            throw new IllegalStateException("Only approved promotion requests can be implemented.");
        }
        Employee employee = request.getEmployee();
        if (employee.getPayGroup() == null) {
            throw new IllegalStateException("Employee has no pay group; position history cannot be created.");
        }
        requiredDocumentService.assertRequiredDocumentsPresent(
                request, PromotionRequiredDocumentService.EnforcementStage.IMPLEMENTATION);

        Department newDepartment = resolveProposedDepartment(request);
        if (newDepartment == null) {
            throw new IllegalStateException("Promotion has no target department.");
        }

        var sameDatePositions = positionHistoryRepository
                .findAllByEmployeeIdAndEffectiveFrom(employee.getId(), request.getEffectiveDate());
        var existingImplementedPosition = sameDatePositions.stream()
                .filter(position -> position.isCurrent()
                        && sameId(position.getJobStep(), request.getProposedRole())
                        && sameId(position.getDepartment(), newDepartment))
                .findFirst();
        if (existingImplementedPosition.isPresent()) {
            applyEmployeeCurrentPosition(employee, request, newDepartment);
            return markImplemented(request, command);
        }
        if (!sameDatePositions.isEmpty()) {
            throw new IllegalStateException("Employee already has a position history row for the promotion effective date.");
        }

        validateEmployeeBaseline(employee, request);

        positionHistoryRepository.findByEmployee_IdAndCurrentTrue(employee.getId()).ifPresent(current -> {
            current.setCurrent(false);
            current.setEffectiveTo(request.getEffectiveDate().minusDays(1));
            positionHistoryRepository.save(current);
        });

        EmployeePositionHistory history = EmployeePositionHistory.builder()
                .employee(employee)
                .department(newDepartment)
                .jobStep(request.getProposedRole())
                .payGroup(employee.getPayGroup())
                .effectiveFrom(request.getEffectiveDate())
                .current(true)
                .status(RecordStatus.ACTIVE)
                .build();
        positionHistoryRepository.save(history);

        applyEmployeeCurrentPosition(employee, request, newDepartment);
        return markImplemented(request, command);
    }

    private void validateEmployeeBaseline(Employee employee, PromotionRequest request) {
        if (!sameId(employee.getJobStep(), request.getCurrentRole())) {
            throw new IllegalStateException("Employee current role no longer matches the promotion baseline.");
        }
        if (!sameId(employee.getDepartment(), request.getCurrentDepartment())) {
            throw new IllegalStateException("Employee current department no longer matches the promotion baseline.");
        }
    }

    private void applyEmployeeCurrentPosition(Employee employee, PromotionRequest request, Department newDepartment) {
        employee.setJobStep(request.getProposedRole());
        employee.setDepartment(newDepartment);
        employeeRepository.save(employee);
    }

    private PromotionRequest markImplemented(PromotionRequest request, PromotionImplementationCommand command) {
        request.setStatus(PromotionStatus.IMPLEMENTED);
        request.setImplementedAt(LocalDateTime.now());
        request.setImplementationComment(command == null ? null : command.getComment());
        requestRepository.save(request);
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        activityService.record(request, PromotionActivityType.IMPLEMENTED, actor,
                "Promotion implemented effective " + request.getEffectiveDate() + ".");
        payrollHandoffService.createPendingHandoff(request);
        notificationService.implemented(request, actor);
        return request;
    }

    private void reconcileImplementedRequest(PromotionRequest request) {
        Employee employee = request.getEmployee();
        Department newDepartment = resolveProposedDepartment(request);
        if (employee != null
                && newDepartment != null
                && sameId(employee.getJobStep(), request.getCurrentRole())
                && sameId(employee.getDepartment(), request.getCurrentDepartment())) {
            applyEmployeeCurrentPosition(employee, request, newDepartment);
        }
        payrollHandoffService.createPendingHandoff(request);
    }

    private Department resolveProposedDepartment(PromotionRequest request) {
        if (request.getProposedDepartment() != null) {
            return request.getProposedDepartment();
        }
        if (request.getProposedRole() != null && request.getProposedRole().getDepartment() != null) {
            return request.getProposedRole().getDepartment();
        }
        return request.getCurrentDepartment();
    }

    private boolean sameId(JobStep left, JobStep right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return Objects.equals(left.getId(), right.getId());
    }

    private boolean sameId(Department left, Department right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return Objects.equals(left.getId(), right.getId());
    }
}
