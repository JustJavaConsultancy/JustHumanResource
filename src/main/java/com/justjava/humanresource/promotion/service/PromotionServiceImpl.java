package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.promotion.dto.*;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.enums.*;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRequestRepository requestRepository;
    private final PromotionApprovalStepRepository approvalStepRepository;
    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeAppraisalRepository appraisalRepository;
    private final PromotionNumberService numberService;
    private final PromotionEligibilityService eligibilityService;
    private final PromotionImplementationService implementationService;
    private final PromotionActivityService activityService;
    private final PromotionMapper mapper;
    private final PromotionCurrentUserService currentUserService;
    private final PromotionWorkflowService workflowService;
    private final PromotionApprovalRouteService approvalRouteService;
    private final PromotionAuthorizationService authorizationService;
    private final PromotionNotificationService notificationService;
    private final PromotionRequiredDocumentService requiredDocumentService;

    @Override
    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse createDraft(PromotionRequestCommand command) {
        authorizationService.requireHrOrAdmin();
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        PromotionRequest request = new PromotionRequest();
        request.setRequestNumber(numberService.generateRequestNumber());
        request.setStatus(PromotionStatus.DRAFT);
        request.setRequestedAt(LocalDateTime.now());
        request.setRequestedBy(actor);
        applyCommand(request, command);
        request = requestRepository.save(request);
        eligibilityService.evaluate(request);
        activityService.record(request, PromotionActivityType.CREATED, actor,
                "Promotion request " + request.getRequestNumber() + " created.");
        return mapper.toDetail(request);
    }

    @Override
    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse updateDraft(Long id, PromotionRequestCommand command) {
        authorizationService.requireHrOrAdmin();
        PromotionRequest request = getRequest(id);
        if (request.getStatus() != PromotionStatus.DRAFT) {
            throw new IllegalStateException("Only draft promotion requests can be edited.");
        }
        applyCommand(request, command);
        request = requestRepository.save(request);
        eligibilityService.evaluate(request);
        activityService.record(request, PromotionActivityType.UPDATED, currentUserService.getCurrentEmployeeOrSystem(),
                "Promotion request updated.");
        return mapper.toDetail(request);
    }

    @Override
    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse submit(Long id) {
        authorizationService.requireHrOrAdmin();
        PromotionRequest request = getRequest(id);
        if (request.getStatus() != PromotionStatus.DRAFT) {
            throw new IllegalStateException("Only draft promotion requests can be submitted.");
        }
        requiredDocumentService.assertRequiredDocumentsPresent(
                request, PromotionRequiredDocumentService.EnforcementStage.SUBMIT);
        eligibilityService.assertCanSubmit(request);
        request.setSubmittedAt(LocalDateTime.now());
        request.setStatus(PromotionStatus.SUBMITTED);
        requestRepository.save(request);
        approvalRouteService.createApprovalRoute(request);
        moveToNextPendingStep(request);
        request.setProcessInstanceId(workflowService.startApproval(request));
        requestRepository.save(request);
        activityService.record(request, PromotionActivityType.SUBMITTED, currentUserService.getCurrentEmployeeOrSystem(),
                "Promotion request submitted for approval.");
        notificationService.submitted(request);
        notificationService.approvalAssigned(request, currentPendingStep(request));
        return mapper.toDetail(request);
    }

    @Override
    @Transactional
    public PromotionDetailResponse approve(Long id, PromotionDecisionCommand command) {
        PromotionRequest request = getRequest(id);
        workflowService.approve(activeTaskId(request), command);
        return mapper.toDetail(getRequest(id));
    }

    @Override
    @Transactional
    public PromotionDetailResponse reject(Long id, PromotionDecisionCommand command) {
        PromotionRequest request = getRequest(id);
        workflowService.reject(activeTaskId(request), command);
        return mapper.toDetail(getRequest(id));
    }

    @Override
    @Transactional
    public PromotionDetailResponse returnForCorrection(Long id, PromotionDecisionCommand command) {
        PromotionRequest request = getRequest(id);
        workflowService.returnForCorrection(activeTaskId(request), command);
        return mapper.toDetail(getRequest(id));
    }

    @Override
    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse cancel(Long id, PromotionDecisionCommand command) {
        authorizationService.requireHrOrAdmin();
        PromotionRequest request = getRequest(id);
        if (!canCancel(request.getStatus())) {
            throw new IllegalStateException("Promotion request cannot be cancelled from status " + request.getStatus() + ".");
        }
        String reason = command == null ? null : command.getComment();
        workflowService.cancelApproval(request, reason);
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        request.setStatus(PromotionStatus.CANCELLED);
        request.setCancelledAt(LocalDateTime.now());
        request.setLastApprovalComment(reason);
        request = requestRepository.save(request);
        activityService.record(request, PromotionActivityType.CANCELLED, actor,
                reason == null || reason.isBlank()
                        ? "Promotion request cancelled."
                        : "Promotion request cancelled: " + reason.trim());
        notificationService.cancelled(request, actor);
        return mapper.toDetail(request);
    }

    @Override
    @Transactional
    @PreAuthorize("@promotionSecurity.canImplementPromotions()")
    public PromotionDetailResponse implement(Long id, PromotionImplementationCommand command) {
        authorizationService.requireHrOrAdmin();
        PromotionRequest request = getRequest(id);
        return mapper.toDetail(implementationService.implement(request, command));
    }

    @Override
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public PromotionSalaryImpactResponse salaryImpact(PromotionRequestCommand command) {
        authorizationService.requireHrFinanceOrAdmin();
        Employee employee = employeeRepository.findById(command.getEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + command.getEmployeeId()));
        JobStep proposedRole = jobStepRepository.findById(command.getProposedRoleId())
                .orElseThrow(() -> new EntityNotFoundException("Job step not found: " + command.getProposedRoleId()));
        BigDecimal currentSalary = employee.getJobStep() == null
                ? BigDecimal.ZERO
                : nullSafe(employee.getJobStep().getGrossSalary());
        BigDecimal proposedSalary = command.getProposedSalary() == null
                ? nullSafe(proposedRole.getGrossSalary())
                : command.getProposedSalary();
        SalaryImpact impact = salaryImpact(currentSalary, proposedSalary);
        return PromotionSalaryImpactResponse.builder()
                .employeeId(employee.getId())
                .employeeName(employee.getFullName())
                .proposedRoleId(proposedRole.getId())
                .proposedRoleName(proposedRole.getName())
                .currentSalary(currentSalary)
                .proposedSalary(proposedSalary)
                .salaryIncreaseAmount(impact.increaseAmount())
                .salaryIncreasePercent(impact.increasePercent())
                .monthlyPayrollImpact(impact.monthlyPayrollImpact())
                .annualPayrollImpact(impact.annualPayrollImpact())
                .build();
    }

    @Override
    public PromotionApprovalRoutePreviewResponse approvalRoutePreview(Long id) {
        PromotionRequest request = getRequest(id);
        authorizationService.requireCanView(request);
        return approvalRouteService.previewApprovalRoute(request);
    }

    @Override
    public PromotionDetailResponse getDetail(Long id) {
        PromotionRequest request = getRequest(id);
        authorizationService.requireCanView(request);
        return mapper.toDetail(request);
    }

    @Override
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public List<PromotionSummaryResponse> list(PromotionStatus status) {
        authorizationService.requireHrFinanceOrAdmin();
        List<PromotionRequest> requests = status == null
                ? requestRepository.findAll()
                : requestRepository.findByStatus(status);
        return summarize(requests, status);
    }

    @Override
    public List<PromotionSummaryResponse> myPromotions(PromotionStatus status) {
        Employee current = currentUserService.findCurrentEmployee()
                .orElseThrow(() -> new AccessDeniedException("Employee context is required."));
        return summarize(requestRepository.findByEmployee_IdOrRequestedBy_Id(current.getId(), current.getId()), status);
    }

    @Override
    @PreAuthorize("@promotionSecurity.canViewOperationalPromotions()")
    public PromotionDashboardResponse dashboard() {
        authorizationService.requireHrFinanceOrAdmin();
        return dashboardFor(requestRepository.findAll());
    }

    @Override
    public PromotionDashboardResponse myDashboard() {
        Employee current = currentUserService.findCurrentEmployee()
                .orElseThrow(() -> new AccessDeniedException("Employee context is required."));
        return dashboardFor(requestRepository.findByEmployee_IdOrRequestedBy_Id(current.getId(), current.getId()));
    }

    private List<PromotionSummaryResponse> summarize(List<PromotionRequest> requests, PromotionStatus status) {
        return requests.stream()
                .filter(request -> status == null || request.getStatus() == status)
                .distinct()
                .sorted(Comparator.comparing(PromotionRequest::getRequestedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(mapper::toSummary)
                .toList();
    }

    private PromotionDashboardResponse dashboardFor(List<PromotionRequest> all) {
        BigDecimal monthlyImpact = all.stream()
                .filter(r -> r.getStatus() == PromotionStatus.APPROVED || r.getStatus() == PromotionStatus.IMPLEMENTED)
                .map(PromotionRequest::getMonthlyPayrollImpact)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal annualImpact = all.stream()
                .filter(r -> r.getStatus() == PromotionStatus.APPROVED || r.getStatus() == PromotionStatus.IMPLEMENTED)
                .map(PromotionRequest::getAnnualPayrollImpact)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<PromotionStatus> pending = Set.of(
                PromotionStatus.SUBMITTED,
                PromotionStatus.LINE_MANAGER_REVIEW,
                PromotionStatus.HR_REVIEW,
                PromotionStatus.FINANCE_REVIEW,
                PromotionStatus.EXECUTIVE_REVIEW
        );
        return PromotionDashboardResponse.builder()
                .pendingApprovalCount(all.stream().filter(r -> pending.contains(r.getStatus())).count())
                .approvedCount(all.stream().filter(r -> r.getStatus() == PromotionStatus.APPROVED).count())
                .implementedCount(all.stream().filter(r -> r.getStatus() == PromotionStatus.IMPLEMENTED).count())
                .rejectedCount(all.stream().filter(r -> r.getStatus() == PromotionStatus.REJECTED).count())
                .monthlySalaryImpact(monthlyImpact)
                .annualSalaryImpact(annualImpact)
                .recentRequests(all.stream()
                        .distinct()
                        .sorted(Comparator.comparing(PromotionRequest::getRequestedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(10)
                        .map(mapper::toSummary)
                        .toList())
                .build();
    }

    private void applyCommand(PromotionRequest request, PromotionRequestCommand command) {
        Employee employee = employeeRepository.findById(command.getEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + command.getEmployeeId()));
        JobStep proposedRole = jobStepRepository.findById(command.getProposedRoleId())
                .orElseThrow(() -> new EntityNotFoundException("Job step not found: " + command.getProposedRoleId()));
        Department proposedDepartment = command.getProposedDepartmentId() == null
                ? proposedRole.getDepartment()
                : departmentRepository.findById(command.getProposedDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + command.getProposedDepartmentId()));
        Employee proposedManager = command.getProposedManagerId() == null ? null
                : employeeRepository.findById(command.getProposedManagerId())
                .orElseThrow(() -> new EntityNotFoundException("Manager not found: " + command.getProposedManagerId()));
        EmployeeAppraisal appraisal = latestFinalizedAppraisal(employee);

        request.setEmployee(employee);
        request.setRequestSource(command.getRequestSource() == null
                ? PromotionRequestSource.HR_INITIATED : command.getRequestSource());
        request.setCurrentRole(employee.getJobStep());
        request.setProposedRole(proposedRole);
        request.setCurrentDepartment(employee.getDepartment());
        request.setProposedDepartment(proposedDepartment);
        request.setCurrentManager(employee.getDepartment() == null ? null : employee.getDepartment().getDepartmentHead());
        request.setProposedManager(proposedManager);
        request.setCurrentSalary(employee.getJobStep() == null ? BigDecimal.ZERO : nullSafe(employee.getJobStep().getGrossSalary()));
        request.setProposedSalary(command.getProposedSalary() == null
                ? nullSafe(proposedRole.getGrossSalary()) : command.getProposedSalary());
        request.setAppraisal(appraisal);
        request.setAppraisalScore(appraisal == null ? null : appraisal.getFinalScore());
        request.setJustification(command.getJustification());
        request.setEffectiveDate(command.getEffectiveDate());
        calculateSalaryImpact(request);
    }

    private EmployeeAppraisal latestFinalizedAppraisal(Employee employee) {
        return appraisalRepository.findByEmployee_IdAndCompletedAtIsNotNull(employee.getId()).stream()
                .filter(a -> a.getFinalScore() != null)
                .max(Comparator.comparing(EmployeeAppraisal::getCompletedAt))
                .orElse(null);
    }

    private void calculateSalaryImpact(PromotionRequest request) {
        SalaryImpact impact = salaryImpact(request.getCurrentSalary(), request.getProposedSalary());
        request.setSalaryIncreaseAmount(impact.increaseAmount());
        request.setSalaryIncreasePercent(impact.increasePercent());
        request.setMonthlyPayrollImpact(impact.monthlyPayrollImpact());
        request.setAnnualPayrollImpact(impact.annualPayrollImpact());
    }

    private SalaryImpact salaryImpact(BigDecimal currentSalary, BigDecimal proposedSalary) {
        BigDecimal current = nullSafe(currentSalary);
        BigDecimal proposed = nullSafe(proposedSalary);
        BigDecimal increase = proposed.subtract(current);
        BigDecimal percent = current.compareTo(BigDecimal.ZERO) > 0
                ? increase.multiply(new BigDecimal("100")).divide(current, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new SalaryImpact(increase, percent, increase, increase.multiply(new BigDecimal("12")));
    }

    private void moveToNextPendingStep(PromotionRequest request) {
        PromotionApprovalStep next = currentPendingStep(request);
        request.setStatus(statusForStage(next.getStage()));
        requestRepository.save(request);
    }

    private PromotionApprovalStep currentPendingStep(PromotionRequest request) {
        return approvalStepRepository
                .findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(request.getId(), PromotionApprovalDecision.PENDING)
                .orElseThrow(() -> new IllegalStateException("No pending approval step for this promotion request."));
    }

    private PromotionStatus statusForStage(PromotionApprovalStage stage) {
        return switch (stage) {
            case LINE_MANAGER -> PromotionStatus.LINE_MANAGER_REVIEW;
            case HR -> PromotionStatus.HR_REVIEW;
            case FINANCE -> PromotionStatus.FINANCE_REVIEW;
            case EXECUTIVE -> PromotionStatus.EXECUTIVE_REVIEW;
        };
    }

    private boolean canCancel(PromotionStatus status) {
        return status == PromotionStatus.DRAFT
                || status == PromotionStatus.SUBMITTED
                || status == PromotionStatus.ELIGIBILITY_REVIEW
                || status == PromotionStatus.LINE_MANAGER_REVIEW
                || status == PromotionStatus.HR_REVIEW
                || status == PromotionStatus.FINANCE_REVIEW
                || status == PromotionStatus.EXECUTIVE_REVIEW;
    }

    private PromotionRequest getRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Promotion request not found: " + id));
    }

    /** Lazily moves an in-flight MVP request onto Flowable when it receives its next decision. */
    private String activeTaskId(PromotionRequest request) {
        if (request.getProcessInstanceId() == null || request.getProcessInstanceId().isBlank()) {
            request.setProcessInstanceId(workflowService.startApproval(request));
            requestRepository.save(request);
        }
        return workflowService.activeTaskId(request);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record SalaryImpact(
            BigDecimal increaseAmount,
            BigDecimal increasePercent,
            BigDecimal monthlyPayrollImpact,
            BigDecimal annualPayrollImpact
    ) {
    }

}
