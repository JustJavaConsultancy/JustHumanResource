package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.dto.PromotionDecisionCommand;
import com.justjava.humanresource.promotion.dto.PromotionPayrollHandoffResponse;
import com.justjava.humanresource.promotion.entity.PromotionPayrollHandoff;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionPayrollHandoffStatus;
import com.justjava.humanresource.promotion.repository.PromotionPayrollHandoffRepository;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionPayrollHandoffService {

    private final PromotionPayrollHandoffRepository handoffRepository;
    private final PromotionCurrentUserService currentUserService;
    private final PromotionActivityService activityService;
    private final PromotionAuthorizationService authorizationService;
    private final PayrollPeriodService payrollPeriodService;
    private final PayrollChangeOrchestrator payrollChangeOrchestrator;

    @Transactional(readOnly = true)
    @PreAuthorize("@promotionSecurity.canManagePromotionPayrollHandoffs()")
    public List<PromotionPayrollHandoffResponse> list(PromotionPayrollHandoffStatus status) {
        authorizationService.requireHrFinanceOrAdmin();
        List<PromotionPayrollHandoff> handoffs = status == null
                ? handoffRepository.findAllByOrderByEffectiveDateAscIdAsc()
                : handoffRepository.findByStatusOrderByEffectiveDateAscIdAsc(status);
        return handoffs.stream().map(this::toResponse).toList();
    }

    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotionPayrollHandoffs()")
    public PromotionPayrollHandoffResponse acknowledge(Long id, PromotionDecisionCommand command) {
        authorizationService.requireHrFinanceOrAdmin();
        PromotionPayrollHandoff handoff = handoff(id);
        if (handoff.getStatus() != PromotionPayrollHandoffStatus.PENDING) {
            throw new IllegalStateException("Only pending payroll handoffs can be acknowledged.");
        }
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        handoff.setStatus(PromotionPayrollHandoffStatus.ACKNOWLEDGED);
        handoff.setAcknowledgedAt(LocalDateTime.now());
        mergeNote(handoff, command);
        handoff = handoffRepository.save(handoff);
        activityService.record(handoff.getPromotionRequest(), PromotionActivityType.PAYROLL_HANDOFF_ACKNOWLEDGED,
                actor, "Payroll handoff acknowledged.");
        return toResponse(handoff);
    }

    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotionPayrollHandoffs()")
    public PromotionPayrollHandoffResponse apply(Long id, PromotionDecisionCommand command) {
        authorizationService.requireHrFinanceOrAdmin();
        PromotionPayrollHandoff handoff = handoff(id);
        if (handoff.getStatus() == PromotionPayrollHandoffStatus.APPLIED) {
            return toResponse(handoff);
        }
        if (handoff.getStatus() != PromotionPayrollHandoffStatus.PENDING
                && handoff.getStatus() != PromotionPayrollHandoffStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Only pending or acknowledged payroll handoffs can be applied.");
        }
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        validateEffectiveDateInOpenPayrollPeriod(handoff);
        validateEmployeeSalaryTarget(handoff);
        payrollChangeOrchestrator.recalculateForEmployee(handoff.getEmployee().getId(), handoff.getEffectiveDate());
        handoff.setStatus(PromotionPayrollHandoffStatus.APPLIED);
        handoff.setAppliedAt(LocalDateTime.now());
        handoff.setAppliedBy(actor);
        if (handoff.getAcknowledgedAt() == null) {
            handoff.setAcknowledgedAt(handoff.getAppliedAt());
        }
        mergeNote(handoff, command);
        handoff = handoffRepository.save(handoff);
        activityService.record(handoff.getPromotionRequest(), PromotionActivityType.PAYROLL_HANDOFF_APPLIED,
                actor, "Payroll handoff applied to payroll.");
        return toResponse(handoff);
    }

    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotionPayrollHandoffs()")
    public PromotionPayrollHandoffResponse cancel(Long id, PromotionDecisionCommand command) {
        authorizationService.requireHrFinanceOrAdmin();
        PromotionPayrollHandoff handoff = handoff(id);
        if (handoff.getStatus() != PromotionPayrollHandoffStatus.PENDING
                && handoff.getStatus() != PromotionPayrollHandoffStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Only pending or acknowledged payroll handoffs can be cancelled.");
        }
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        handoff.setStatus(PromotionPayrollHandoffStatus.CANCELLED);
        mergeNote(handoff, command);
        handoff = handoffRepository.save(handoff);
        activityService.record(handoff.getPromotionRequest(), PromotionActivityType.PAYROLL_HANDOFF_CANCELLED,
                actor, "Payroll handoff cancelled.");
        return toResponse(handoff);
    }

    @Transactional
    public void createPendingHandoff(PromotionRequest request) {
        if (!hasSalaryChange(request) || handoffRepository.existsByPromotionRequest_Id(request.getId())) {
            return;
        }
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        PromotionPayrollHandoff handoff = new PromotionPayrollHandoff();
        handoff.setPromotionRequest(request);
        handoff.setEmployee(request.getEmployee());
        handoff.setPayGroup(request.getEmployee() == null ? null : request.getEmployee().getPayGroup());
        handoff.setEffectiveDate(request.getEffectiveDate());
        handoff.setCurrentSalary(request.getCurrentSalary());
        handoff.setProposedSalary(request.getProposedSalary());
        handoff.setMonthlyPayrollImpact(request.getMonthlyPayrollImpact());
        handoff.setAnnualPayrollImpact(request.getAnnualPayrollImpact());
        handoff.setCreatedBy(actor);
        handoff.setNote("Created from implemented promotion " + request.getRequestNumber() + ".");
        handoffRepository.save(handoff);
        activityService.record(request, PromotionActivityType.PAYROLL_HANDOFF_CREATED, actor,
                "Payroll handoff created for salary change effective " + request.getEffectiveDate() + ".");
    }

    private boolean hasSalaryChange(PromotionRequest request) {
        if (request.getProposedSalary() != null
                && request.getCurrentSalary() != null
                && request.getProposedSalary().compareTo(request.getCurrentSalary()) != 0) {
            return true;
        }
        return positive(request.getMonthlyPayrollImpact()) || positive(request.getAnnualPayrollImpact());
    }

    private boolean positive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private PromotionPayrollHandoff handoff(Long id) {
        return handoffRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promotion payroll handoff not found: " + id));
    }

    private void validateEffectiveDateInOpenPayrollPeriod(PromotionPayrollHandoff handoff) {
        if (handoff.getEffectiveDate() == null) {
            throw new IllegalStateException("Payroll handoff effective date is required.");
        }
        Long companyId = companyId(handoff);
        if (companyId == null) {
            throw new IllegalStateException("Payroll company could not be resolved for this promotion handoff.");
        }
        payrollPeriodService.validatePayrollDate(companyId, handoff.getEffectiveDate());
    }

    private void validateEmployeeSalaryTarget(PromotionPayrollHandoff handoff) {
        if (handoff.getEmployee() == null) {
            throw new IllegalStateException("Payroll handoff employee is required.");
        }
        if (handoff.getEmployee().getJobStep() == null) {
            throw new IllegalStateException("Employee current job step is required before payroll application.");
        }
        if (handoff.getProposedSalary() == null || handoff.getEmployee().getJobStep().getGrossSalary() == null) {
            throw new IllegalStateException("Promotion proposed salary must match the employee current job-step salary before payroll application.");
        }
        if (handoff.getEmployee().getJobStep().getGrossSalary().compareTo(handoff.getProposedSalary()) != 0) {
            throw new IllegalStateException("Employee current job-step salary does not match the promotion handoff proposed salary.");
        }
    }

    private Long companyId(PromotionPayrollHandoff handoff) {
        if (handoff.getEmployee() == null
                || handoff.getEmployee().getDepartment() == null
                || handoff.getEmployee().getDepartment().getCompany() == null) {
            return null;
        }
        return handoff.getEmployee().getDepartment().getCompany().getId();
    }

    private void mergeNote(PromotionPayrollHandoff handoff, PromotionDecisionCommand command) {
        String comment = command == null ? null : command.getComment();
        if (comment == null || comment.isBlank()) {
            return;
        }
        String trimmed = comment.trim();
        if (handoff.getNote() == null || handoff.getNote().isBlank()) {
            handoff.setNote(trimmed);
        } else {
            handoff.setNote(handoff.getNote() + "\n" + trimmed);
        }
    }

    private PromotionPayrollHandoffResponse toResponse(PromotionPayrollHandoff handoff) {
        Employee createdBy = handoff.getCreatedBy();
        Employee appliedBy = handoff.getAppliedBy();
        return PromotionPayrollHandoffResponse.builder()
                .id(handoff.getId())
                .status(handoff.getStatus())
                .effectiveDate(handoff.getEffectiveDate())
                .currentSalary(handoff.getCurrentSalary())
                .proposedSalary(handoff.getProposedSalary())
                .monthlyPayrollImpact(handoff.getMonthlyPayrollImpact())
                .annualPayrollImpact(handoff.getAnnualPayrollImpact())
                .payGroupName(handoff.getPayGroup() == null ? null : handoff.getPayGroup().getName())
                .createdByName(createdBy == null ? "System" : createdBy.getFullName())
                .appliedByName(appliedBy == null ? null : appliedBy.getFullName())
                .createdAt(handoff.getCreatedAt())
                .acknowledgedAt(handoff.getAcknowledgedAt())
                .appliedAt(handoff.getAppliedAt())
                .externalApplicationOnly(false)
                .note(handoff.getNote())
                .build();
    }
}
