package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.approval.entity.CustomApprovalPath;
import com.justjava.humanresource.approval.repository.CustomApprovalPathRepository;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.loan.dto.*;
import com.justjava.humanresource.loan.entity.*;
import com.justjava.humanresource.loan.enums.*;
import com.justjava.humanresource.loan.repository.*;
import com.justjava.humanresource.loan.service.*;
import com.justjava.humanresource.loan.service.LoanEmployeeContextService.EmployeeSnapshot;
import com.justjava.humanresource.loan.service.LoanEmployeeContextService.ViewerRole;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.justjava.humanresource.loan.service.impl.LoanApplicationSupport.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeLoanApplicationServiceImpl implements EmployeeLoanApplicationService {

    private final EmployeeLoanApplicationRepository applicationRepository;
    private final EmployeeLoanApprovalStepRepository stepRepository;
    private final EmployeeLoanAccountRepository accountRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanRepaymentTransactionRepository transactionRepository;
    private final EmployeeLoanAttachmentRepository attachmentRepository;
    private final CustomApprovalPathRepository customApprovalPathRepository;
    private final LoanProductService productService;
    private final LoanRepaymentCalculationService calculationService;
    private final LoanNumberService numberService;
    private final LoanEmployeeContextService contextService;
    private final LoanActivityService activityService;
    private final LoanAttachmentService attachmentService;
    private final LoanApprovalRouteService approvalRouteService;
    private final AuthenticationManager authenticationManager;

    // =====================================================================
    // Commands
    // =====================================================================

    @Override
    @Transactional
    public LoanApplicationResponse createDraft(LoanApplicationCommand command) {
        Employee employee = contextService.getCurrentEmployee();
        LoanProduct product = productService.getEntity(command.getLoanProductId());
        requireActiveProduct(product);
        LoanRepaymentPreviewResponse calc = validate(product, command);

        EmployeeLoanApplication app = new EmployeeLoanApplication();
        app.setApplicationNumber(numberService.generateApplicationNumber());
        app.setEmployee(employee);
        applyEmployeeSnapshot(app, contextService.snapshot(employee));
        applyTerms(app, product, command, calc);
        app.setStatus(LoanApplicationStatus.DRAFT);
        app = applicationRepository.save(app);

        productService.markUsed(product.getId());
        activityService.record(app.getId(), LoanActivityType.DRAFT_CREATED,
                "Draft created for " + product.getName() + ".", employee.getId());
        return toResponse(app);
    }

    @Override
    @Transactional
    public LoanApplicationResponse updateApplication(Long id, LoanApplicationCommand command) {
        Employee employee = contextService.getCurrentEmployee();
        EmployeeLoanApplication app = loadOwned(id, employee.getId());
        requireEditable(app, "edited");

        LoanProduct product = productService.getEntity(command.getLoanProductId());
        boolean productChanged = !product.getId().equals(app.getLoanProduct().getId());
        if (productChanged) {
            requireActiveProduct(product);
        }
        LoanRepaymentPreviewResponse calc = validate(product, command);
        applyTerms(app, product, command, calc);
        app = applicationRepository.save(app);

        if (productChanged) {
            productService.markUsed(product.getId());
        }
        activityService.record(app.getId(), LoanActivityType.DRAFT_UPDATED,
                "Application details updated.", employee.getId());
        return toResponse(app);
    }

    @Override
    @Transactional
    public void deleteDraft(Long id) {
        Employee employee = contextService.getCurrentEmployee();
        EmployeeLoanApplication app = loadOwned(id, employee.getId());
        if (app.getStatus() != LoanApplicationStatus.DRAFT) {
            throw new IllegalStateException("Only draft applications can be deleted.");
        }
        attachmentService.deleteAllForApplication(id);
        activityService.purge(id);
        applicationRepository.delete(app);
    }

    @Override
    @Transactional
    public LoanApplicationResponse submit(Long id) {
        Employee employee = contextService.getCurrentEmployee();
        EmployeeLoanApplication app = loadOwned(id, employee.getId());
        requireEditable(app, "submitted");

        LoanProduct product = productService.getEntity(app.getLoanProduct().getId());
        requireActiveProduct(product);
        // Fails when the stored route no longer resolves (e.g. the custom path was disabled).
        productService.validateApprovalRoute(product.getApprovalRouteType(), product.getCustomApprovalPathId());

        // Re-validate against the product's current limits; a stale draft may no longer comply
        // (the repayment start month in particular can have slipped into the past).
        LoanRepaymentPreviewResponse calc = calculationService.validateOrThrow(product,
                app.getRequestedAmount(), app.getRepaymentAmount(), app.getTenorMonths(), app.getRepaymentStartMonth());

        if (product.isRequiresAttachment() && attachmentRepository.countByLoanApplicationId(id) == 0) {
            throw new IllegalArgumentException("This loan product requires at least one supporting attachment.");
        }

        boolean resubmission = app.getStatus() == LoanApplicationStatus.RETURNED_FOR_CORRECTION;

        applyEmployeeSnapshot(app, contextService.snapshot(employee));
        app.setRequestedAmount(calc.getRequestedAmount());
        app.setRepaymentAmount(calc.getRepaymentAmount());
        app.setTenorMonths(calc.getTenorMonths());
        app.setRepaymentStartMonth(calc.getRepaymentStartMonth());
        app.setTotalInterestAmount(calc.getTotalInterestAmount());
        app.setTotalRepayableAmount(calc.getTotalRepayableAmount());

        // Terms fixed at submission; later product edits must not change this application.
        app.setInterestTypeSnapshot(product.getInterestType());
        app.setInterestRateSnapshot(product.getInterestRate());
        app.setApprovalRouteTypeSnapshot(product.getApprovalRouteType());
        if (product.getApprovalRouteType() == LoanApprovalRouteType.CUSTOM) {
            app.setCustomApprovalPathIdSnapshot(product.getCustomApprovalPathId());
            app.setCustomApprovalPathNameSnapshot(pathName(product.getCustomApprovalPathId()));
        } else {
            app.setCustomApprovalPathIdSnapshot(null);
            app.setCustomApprovalPathNameSnapshot(null);
        }

        app.setStatus(LoanApplicationStatus.SUBMITTED);
        app.setSubmittedAt(LocalDateTime.now());
        app = applicationRepository.save(app);

        activityService.record(app.getId(),
                resubmission ? LoanActivityType.APPLICATION_RESUBMITTED : LoanActivityType.APPLICATION_SUBMITTED,
                (resubmission ? "Application resubmitted" : "Application submitted") + " for approval ("
                        + routeLabel(product.getApprovalRouteType()) + ").",
                employee.getId());
        // Creates the approval steps and starts the Flowable process; an empty custom route fails here
        // and rolls the whole submission back. The initialize delegate moves the status to PENDING_*.
        approvalRouteService.startApproval(app);
        return toResponse(app);
    }

    @Override
    @Transactional
    public LoanApplicationResponse cancel(Long id) {
        Employee employee = contextService.getCurrentEmployee();
        EmployeeLoanApplication app = loadOwned(id, employee.getId());

        boolean cancellable = EDITABLE.contains(app.getStatus())
                || (PENDING_FIRST_STAGE.contains(app.getStatus()) && !hasApproval(app.getId()));
        if (!cancellable) {
            throw new IllegalStateException("This application can no longer be cancelled.");
        }

        approvalRouteService.cancelApproval(app);
        app.setStatus(LoanApplicationStatus.CANCELLED);
        app.setCancelledAt(LocalDateTime.now());
        app.setCancelledByEmployeeId(employee.getId());
        app = applicationRepository.save(app);

        activityService.record(app.getId(), LoanActivityType.APPLICATION_CANCELLED,
                "Application cancelled by the applicant.", employee.getId());
        return toResponse(app);
    }

    // =====================================================================
    // Queries
    // =====================================================================

    @Override
    public LoanApplicationEditResponse getForEdit(Long id) {
        EmployeeLoanApplication app = loadOwned(id, contextService.getCurrentEmployee().getId());
        LoanProduct product = app.getLoanProduct();

        // Most recent return decision across all submissions, shown as the correction request.
        Optional<EmployeeLoanApprovalStep> returned = stepRepository
                .findByLoanApplicationIdOrderBySequenceNoAscIdAsc(id).stream()
                .filter(s -> s.getDecision() == LoanApprovalDecision.RETURN)
                .max(Comparator.comparing(EmployeeLoanApprovalStep::getId));
        String returnedByName = returned.map(EmployeeLoanApprovalStep::getActedByEmployeeId)
                .map(actor -> contextService.employeeNames(List.of(actor)).get(actor)).orElse(null);

        return LoanApplicationEditResponse.builder()
                .id(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .status(app.getStatus())
                .editable(EDITABLE.contains(app.getStatus()))
                .loanProduct(toProductSummary(product))
                .requestedAmount(app.getRequestedAmount())
                .repaymentAmount(app.getRepaymentAmount())
                .tenorMonths(app.getTenorMonths())
                .repaymentStartMonth(app.getRepaymentStartMonth())
                .purpose(app.getPurpose())
                .latestReturnComment(returned.map(EmployeeLoanApprovalStep::getComments).orElse(null))
                .returnedByName(returnedByName)
                .attachmentRequired(product.isRequiresAttachment())
                .attachments(attachmentService.list(id))
                .preview(calculationService.preview(product, app.getRequestedAmount(), app.getRepaymentAmount(),
                        app.getTenorMonths(), app.getRepaymentStartMonth()))
                .build();
    }

    @Override
    public LoanApplicationDetailResponse getEmployeeDetail(Long id) {
        EmployeeLoanApplication app = loadOwned(id, contextService.getCurrentEmployee().getId());
        return buildDetail(app, ViewerRole.EMPLOYEE);
    }

    @Override
    public LoanApplicationDetailResponse getHrDetail(Long id) {
        if (!(authenticationManager.isHumanResource() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("HR access is required.");
        }
        return buildDetail(loadVisibleToStaff(id), ViewerRole.HR);
    }

    @Override
    public LoanApplicationDetailResponse getFinanceDetail(Long id) {
        if (!(authenticationManager.isFinancialOfficer() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("Finance access is required.");
        }
        return buildDetail(loadVisibleToStaff(id), ViewerRole.FINANCE);
    }

    @Override
    public LoanApplicationDetailResponse getCustomApproverDetail(Long id) {
        Employee me = contextService.getCurrentEmployee();
        EmployeeLoanApplication app = loadVisibleToStaff(id);
        if (!contextService.isAssignedCustomApprover(app, me.getId())) {
            throw new AccessDeniedException("This loan application is not assigned to you.");
        }
        return buildDetail(app, ViewerRole.CUSTOM_APPROVER);
    }

    @Override
    public List<LoanApplicationSummaryResponse> listMine(LoanApplicationStatus status) {
        Long me = contextService.getCurrentEmployee().getId();
        List<EmployeeLoanApplication> apps = status == null
                ? applicationRepository.findByEmployeeIdOrderByCreatedAtDesc(me)
                : applicationRepository.findByEmployeeIdAndStatusOrderByCreatedAtDesc(me, status);
        return apps.stream().map(this::toSummary).toList();
    }

    @Override
    public EmployeeLoanDashboardResponse getMyDashboard() {
        Long me = contextService.getCurrentEmployee().getId();
        Map<LoanApplicationStatus, Long> byStatus = applicationRepository.findByEmployeeIdOrderByCreatedAtDesc(me)
                .stream().collect(Collectors.groupingBy(EmployeeLoanApplication::getStatus, Collectors.counting()));

        long pending = sum(byStatus, LoanApplicationStatus.SUBMITTED, LoanApplicationStatus.PENDING_HR_APPROVAL,
                LoanApplicationStatus.PENDING_CUSTOM_APPROVAL, LoanApplicationStatus.HR_APPROVED,
                LoanApplicationStatus.PENDING_FINANCE_APPROVAL);
        long approved = sum(byStatus, LoanApplicationStatus.FINANCE_APPROVED, LoanApplicationStatus.CUSTOM_APPROVED,
                LoanApplicationStatus.ACTIVE, LoanApplicationStatus.COMPLETED, LoanApplicationStatus.CLOSED);

        List<EmployeeLoanAccount> accounts = accountRepository.findByEmployeeIdOrderByCreatedAtDesc(me);
        long completed = accounts.stream().filter(a -> a.getStatus() == LoanAccountStatus.COMPLETED).count();

        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal monthly = BigDecimal.ZERO;
        LocalDate nextMonth = null;
        long missed = 0;
        long active = 0;
        for (EmployeeLoanAccount a : accounts) {
            if (a.getStatus() != LoanAccountStatus.ACTIVE) continue;
            active++;
            balance = balance.add(nz(a.getOutstandingBalance()));
            monthly = monthly.add(nz(a.getRepaymentAmount()));
            for (LoanRepaymentSchedule row : scheduleRepository.findByLoanAccountIdOrderBySequenceNumberAsc(a.getId())) {
                if (row.getStatus() == LoanRepaymentStatus.MISSED) missed++;
                if (row.getStatus() == LoanRepaymentStatus.PENDING || row.getStatus() == LoanRepaymentStatus.PARTIALLY_PAID) {
                    if (nextMonth == null || row.getDueMonth().isBefore(nextMonth)) nextMonth = row.getDueMonth();
                }
                // Split what is still owed into interest and principal in proportion to each installment.
                BigDecimal owed = nz(row.getOutstandingAmount());
                if (owed.signum() > 0 && nz(row.getExpectedAmount()).signum() > 0) {
                    interest = interest.add(nz(row.getInterestPortion()).multiply(owed)
                            .divide(row.getExpectedAmount(), 2, RoundingMode.HALF_UP));
                }
            }
        }

        return EmployeeLoanDashboardResponse.builder()
                .draftCount(sum(byStatus, LoanApplicationStatus.DRAFT))
                .pendingCount(pending)
                .returnedCount(sum(byStatus, LoanApplicationStatus.RETURNED_FOR_CORRECTION))
                .approvedCount(approved)
                .rejectedCount(sum(byStatus, LoanApplicationStatus.REJECTED))
                .cancelledCount(sum(byStatus, LoanApplicationStatus.CANCELLED))
                .activeLoanCount(active)
                .completedLoanCount(completed)
                .outstandingBalance(balance)
                .outstandingInterest(interest)
                .outstandingPrincipal(balance.subtract(interest))
                .monthlyExpectedDeduction(monthly)
                .nextDeductionMonth(nextMonth)
                .missedDeductionCount(missed)
                .assignedApprovalTaskCount(contextService.findAssignedCustomTasks(me).size())
                .build();
    }

    // =====================================================================
    // Detail assembly
    // =====================================================================

    /** HR/Finance/admin logins may have no Employee record; then they simply are not an assigned approver. */
    private Long currentEmployeeIdOrNull() {
        return contextService.findCurrentEmployee().map(Employee::getId).orElse(null);
    }

    private LoanApplicationDetailResponse buildDetail(EmployeeLoanApplication app, ViewerRole role) {
        Long me = currentEmployeeIdOrNull();
        List<EmployeeLoanApprovalStep> steps =
                stepRepository.findByLoanApplicationIdOrderBySequenceNoAscIdAsc(app.getId());
        Optional<EmployeeLoanAccount> account = accountRepository.findByLoanApplicationId(app.getId());
        Optional<EmployeeLoanApprovalStep> current = IN_APPROVAL.contains(app.getStatus())
                ? currentPending(steps) : Optional.empty();

        // Schedule: the persisted one once the loan exists, otherwise a live preview from the stored terms.
        List<LoanRepaymentSchedule> scheduleRows = account
                .map(a -> scheduleRepository.findByLoanAccountIdOrderBySequenceNumberAsc(a.getId()))
                .orElseGet(List::of);
        List<LoanRepaymentScheduleLineResponse> scheduleLines;
        if (account.isPresent()) {
            scheduleLines = scheduleRows.stream().map(this::toLine).toList();
        } else {
            LoanProduct p = app.getLoanProduct();
            scheduleLines = calculationService.calculate(
                    app.getInterestTypeSnapshot() != null ? app.getInterestTypeSnapshot() : p.getInterestType(),
                    app.getInterestTypeSnapshot() != null ? app.getInterestRateSnapshot() : p.getInterestRate(),
                    app.getRequestedAmount(), app.getRepaymentAmount(), app.getTenorMonths(),
                    app.getRepaymentStartMonth()).getLines();
        }

        Map<Long, Integer> sequenceByScheduleId = scheduleRows.stream()
                .collect(Collectors.toMap(LoanRepaymentSchedule::getId, LoanRepaymentSchedule::getSequenceNumber));
        List<LoanRepaymentTransactionResponse> history = account
                .map(a -> transactionRepository.findByLoanAccountIdOrderByTransactionMonthAscIdAsc(a.getId()).stream()
                        .map(t -> toTransaction(t, app, sequenceByScheduleId)).toList())
                .orElseGet(List::of);
        List<LoanMissedDeductionResponse> missed = account
                .map(a -> scheduleRows.stream().filter(r -> r.getStatus() == LoanRepaymentStatus.MISSED)
                        .map(r -> toMissed(r, a, app)).toList())
                .orElseGet(List::of);

        boolean owner = role == ViewerRole.EMPLOYEE;
        boolean editable = owner && EDITABLE.contains(app.getStatus());
        boolean cancellable = owner && (EDITABLE.contains(app.getStatus())
                || (PENDING_FIRST_STAGE.contains(app.getStatus()) && !hasApproval(app.getId(), steps)));

        boolean canAct = false;
        if (current.isPresent()) {
            EmployeeLoanApprovalStep c = current.get();
            canAct = switch (role) {
                case HR -> c.getApprovalStage() == LoanApprovalStage.HR
                        && app.getStatus() == LoanApplicationStatus.PENDING_HR_APPROVAL;
                case FINANCE -> c.getApprovalStage() == LoanApprovalStage.FINANCE
                        && app.getStatus() == LoanApplicationStatus.PENDING_FINANCE_APPROVAL;
                case CUSTOM_APPROVER -> c.getApprovalStage() == LoanApprovalStage.CUSTOM
                        && Objects.equals(c.getApproverEmployeeId(), me)
                        && app.getStatus() == LoanApplicationStatus.PENDING_CUSTOM_APPROVAL;
                default -> false;
            };
        }

        return LoanApplicationDetailResponse.builder()
                .application(toResponse(app))
                .loanProduct(toProductSummary(app.getLoanProduct()))
                .approvalContext(owner ? null : contextService.buildApprovalContext(app))
                .approvalRoute(buildRoute(app, steps, current))
                .scheduleLocked(account.isPresent())
                .repaymentSchedule(scheduleLines)
                .repaymentHistory(history)
                .missedDeductions(missed)
                .loanAccountId(account.map(EmployeeLoanAccount::getId).orElse(null))
                .loanAccountStatus(account.map(a -> a.getStatus().name()).orElse(null))
                .totalPaidAmount(account.map(EmployeeLoanAccount::getTotalPaidAmount).orElse(null))
                .outstandingBalance(account.map(EmployeeLoanAccount::getOutstandingBalance).orElse(null))
                .attachments(attachmentService.list(app.getId()))
                .activities(activityService.listForApplication(app.getId()))
                .viewerRole(role.name())
                .canEdit(editable)
                .canDelete(owner && app.getStatus() == LoanApplicationStatus.DRAFT)
                .canSubmit(editable)
                .canCancel(cancellable)
                .canUploadAttachment(editable)
                .canAct(canAct)
                .currentTaskId(canAct ? approvalRouteService.findActiveTaskId(app.getId()).orElse(null) : null)
                .build();
    }

    private LoanApprovalRouteResponse buildRoute(EmployeeLoanApplication app, List<EmployeeLoanApprovalStep> steps,
                                                 Optional<EmployeeLoanApprovalStep> current) {
        LoanProduct product = app.getLoanProduct();
        boolean snapshotted = app.getApprovalRouteTypeSnapshot() != null;
        LoanApprovalRouteType type = snapshotted ? app.getApprovalRouteTypeSnapshot() : product.getApprovalRouteType();
        Long pathId = snapshotted ? app.getCustomApprovalPathIdSnapshot()
                : (type == LoanApprovalRouteType.CUSTOM ? product.getCustomApprovalPathId() : null);
        String pathName = snapshotted ? app.getCustomApprovalPathNameSnapshot() : pathName(pathId);

        List<EmployeeLoanApprovalStep> latest = latestAttempt(steps);
        int completed = (int) latest.stream().filter(s -> s.getDecision() == LoanApprovalDecision.APPROVE).count();

        Set<Long> nameIds = new HashSet<>();
        steps.forEach(s -> {
            nameIds.add(s.getApproverEmployeeId());
            nameIds.add(s.getActedByEmployeeId());
        });
        Map<Long, String> names = contextService.employeeNames(nameIds);

        List<LoanApprovalStepResponse> stepResponses = steps.stream()
                .sorted(Comparator.comparing(EmployeeLoanApprovalStep::getId))
                .map(s -> LoanApprovalStepResponse.builder()
                        .id(s.getId())
                        .sequenceNo(s.getSequenceNo())
                        .approvalStage(s.getApprovalStage())
                        .stageLabel(stageLabel(s))
                        .approverEmployeeId(s.getApproverEmployeeId())
                        .approverName(names.get(s.getApproverEmployeeId()))
                        .approverGroup(s.getApproverGroup())
                        .decision(s.getDecision())
                        .pending(s.getDecision() == null)
                        .current(current.map(c -> c.getId().equals(s.getId())).orElse(false))
                        .comments(s.getComments())
                        .decisionAt(s.getDecisionAt())
                        .actedByEmployeeId(s.getActedByEmployeeId())
                        .actedByName(names.get(s.getActedByEmployeeId()))
                        .build())
                .toList();

        return LoanApprovalRouteResponse.builder()
                .routeType(type)
                .routeLabel(routeLabel(type))
                .customApprovalPathId(pathId)
                .customApprovalPathName(pathName)
                .currentStage(current.map(EmployeeLoanApprovalStep::getApprovalStage).orElse(null))
                .currentOwnerLabel(current.map(c -> ownerLabel(c, names)).orElse(null))
                .currentApproverEmployeeId(current.map(EmployeeLoanApprovalStep::getApproverEmployeeId).orElse(null))
                .currentApproverName(current.map(c -> names.get(c.getApproverEmployeeId())).orElse(null))
                .currentApproverGroup(current.map(EmployeeLoanApprovalStep::getApproverGroup).orElse(null))
                .totalSteps(latest.size())
                .completedSteps(completed)
                .steps(stepResponses)
                .build();
    }

    // =====================================================================
    // Mapping
    // =====================================================================

    private LoanApplicationResponse toResponse(EmployeeLoanApplication a) {
        LoanProduct p = a.getLoanProduct();
        Employee e = a.getEmployee();
        boolean snapshotted = a.getApprovalRouteTypeSnapshot() != null;
        LoanApprovalRouteType routeType = snapshotted ? a.getApprovalRouteTypeSnapshot() : p.getApprovalRouteType();
        Long pathId = snapshotted ? a.getCustomApprovalPathIdSnapshot()
                : (routeType == LoanApprovalRouteType.CUSTOM ? p.getCustomApprovalPathId() : null);

        return LoanApplicationResponse.builder()
                .id(a.getId())
                .applicationNumber(a.getApplicationNumber())
                .status(a.getStatus())
                .loanProductId(p.getId())
                .loanProductCode(p.getCode())
                .loanProductName(p.getName())
                .employeeId(e.getId())
                .employeeName(e.getFullName())
                .departmentId(a.getDepartmentId())
                .departmentName(e.getDepartment() == null ? null : e.getDepartment().getName())
                .jobGradeName(a.getJobGradeNameSnapshot())
                .jobStepName(a.getJobStepNameSnapshot())
                .grossSalary(a.getGrossSalarySnapshot())
                .requestedAmount(a.getRequestedAmount())
                .repaymentAmount(a.getRepaymentAmount())
                .tenorMonths(a.getTenorMonths())
                .repaymentStartMonth(a.getRepaymentStartMonth())
                .interestType(a.getInterestTypeSnapshot() != null ? a.getInterestTypeSnapshot() : p.getInterestType())
                .interestRate(a.getInterestTypeSnapshot() != null ? a.getInterestRateSnapshot() : p.getInterestRate())
                .totalInterestAmount(a.getTotalInterestAmount())
                .totalRepayableAmount(a.getTotalRepayableAmount())
                .purpose(a.getPurpose())
                .approvalRouteType(routeType)
                .approvalRouteLabel(routeLabel(routeType))
                .customApprovalPathId(pathId)
                .customApprovalPathName(snapshotted ? a.getCustomApprovalPathNameSnapshot() : pathName(pathId))
                .workflowInstanceId(a.getWorkflowInstanceId())
                .submittedAt(a.getSubmittedAt())
                .hrApprovedAt(a.getHrApprovedAt())
                .hrApprovedByEmployeeId(a.getHrApprovedByEmployeeId())
                .financeApprovedAt(a.getFinanceApprovedAt())
                .financeApprovedByEmployeeId(a.getFinanceApprovedByEmployeeId())
                .customApprovalCompletedAt(a.getCustomApprovalCompletedAt())
                .rejectedAt(a.getRejectedAt())
                .rejectedByEmployeeId(a.getRejectedByEmployeeId())
                .cancelledAt(a.getCancelledAt())
                .cancelledByEmployeeId(a.getCancelledByEmployeeId())
                .activatedAt(a.getActivatedAt())
                .closedAt(a.getClosedAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private LoanApplicationSummaryResponse toSummary(EmployeeLoanApplication a) {
        LoanProduct p = a.getLoanProduct();
        Employee e = a.getEmployee();
        LoanApprovalRouteType routeType = a.getApprovalRouteTypeSnapshot() != null
                ? a.getApprovalRouteTypeSnapshot() : p.getApprovalRouteType();

        String owner = null;
        Long approverId = null;
        if (IN_APPROVAL.contains(a.getStatus())) {
            Optional<EmployeeLoanApprovalStep> cur =
                    currentPending(stepRepository.findByLoanApplicationIdOrderBySequenceNoAscIdAsc(a.getId()));
            if (cur.isPresent()) {
                approverId = cur.get().getApproverEmployeeId();
                owner = ownerLabel(cur.get(), contextService.employeeNames(List.of(
                        approverId == null ? -1L : approverId)));
            }
        }

        Long accountId = null;
        BigDecimal outstanding = null;
        boolean hasMissed = false;
        if (a.getStatus() == LoanApplicationStatus.ACTIVE || a.getStatus() == LoanApplicationStatus.COMPLETED
                || a.getStatus() == LoanApplicationStatus.CLOSED) {
            Optional<EmployeeLoanAccount> acct = accountRepository.findByLoanApplicationId(a.getId());
            if (acct.isPresent()) {
                accountId = acct.get().getId();
                outstanding = acct.get().getOutstandingBalance();
                hasMissed = !scheduleRepository
                        .findByLoanAccountIdAndStatus(accountId, LoanRepaymentStatus.MISSED).isEmpty();
            }
        }

        return LoanApplicationSummaryResponse.builder()
                .id(a.getId())
                .applicationNumber(a.getApplicationNumber())
                .status(a.getStatus())
                .employeeId(e.getId())
                .employeeName(e.getFullName())
                .departmentName(e.getDepartment() == null ? null : e.getDepartment().getName())
                .loanProductId(p.getId())
                .loanProductName(p.getName())
                .requestedAmount(a.getRequestedAmount())
                .repaymentAmount(a.getRepaymentAmount())
                .tenorMonths(a.getTenorMonths())
                .repaymentStartMonth(a.getRepaymentStartMonth())
                .approvalRouteType(routeType)
                .approvalRouteLabel(routeLabel(routeType))
                .currentApprovalOwner(owner)
                .currentApproverEmployeeId(approverId)
                .loanAccountId(accountId)
                .outstandingBalance(outstanding)
                .hasMissedDeductions(hasMissed)
                .submittedAt(a.getSubmittedAt())
                .activatedAt(a.getActivatedAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private LoanProductSummaryResponse toProductSummary(LoanProduct p) {
        return LoanProductSummaryResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .description(p.getDescription())
                .minimumAmount(p.getMinimumAmount())
                .maximumAmount(p.getMaximumAmount())
                .minimumRepaymentAmount(p.getMinimumRepaymentAmount())
                .maximumTenorMonths(p.getMaximumTenorMonths())
                .interestType(p.getInterestType())
                .interestRate(p.getInterestRate())
                .approvalRouteType(p.getApprovalRouteType())
                .approvalRouteLabel(routeLabel(p.getApprovalRouteType()))
                .requiresAttachment(p.isRequiresAttachment())
                .active(p.isActive())
                .build();
    }

    private LoanRepaymentScheduleLineResponse toLine(LoanRepaymentSchedule s) {
        return LoanRepaymentScheduleLineResponse.builder()
                .id(s.getId())
                .sequenceNumber(s.getSequenceNumber())
                .dueMonth(s.getDueMonth())
                .expectedAmount(s.getExpectedAmount())
                .principalPortion(s.getPrincipalPortion())
                .interestPortion(s.getInterestPortion())
                .paidAmount(s.getPaidAmount())
                .outstandingAmount(s.getOutstandingAmount())
                .status(s.getStatus())
                .payrollRunId(s.getPayrollRunId())
                .payrollLineItemId(s.getPayrollLineItemId())
                .deductedAt(s.getDeductedAt())
                .missedAt(s.getMissedAt())
                .build();
    }

    private LoanRepaymentTransactionResponse toTransaction(LoanRepaymentTransaction t, EmployeeLoanApplication app,
                                                           Map<Long, Integer> sequenceByScheduleId) {
        return LoanRepaymentTransactionResponse.builder()
                .id(t.getId())
                .loanAccountId(t.getLoanAccountId())
                .loanApplicationId(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .repaymentScheduleId(t.getRepaymentScheduleId())
                .scheduleSequenceNumber(sequenceByScheduleId.get(t.getRepaymentScheduleId()))
                .employeeId(t.getEmployeeId())
                .payrollRunId(t.getPayrollRunId())
                .payrollLineItemId(t.getPayrollLineItemId())
                .amount(t.getAmount())
                .transactionMonth(t.getTransactionMonth())
                .transactionType(t.getTransactionType())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private LoanMissedDeductionResponse toMissed(LoanRepaymentSchedule s, EmployeeLoanAccount account,
                                                 EmployeeLoanApplication app) {
        Employee e = app.getEmployee();
        return LoanMissedDeductionResponse.builder()
                .repaymentScheduleId(s.getId())
                .loanAccountId(account.getId())
                .loanApplicationId(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .employeeId(e.getId())
                .employeeName(e.getFullName())
                .departmentName(e.getDepartment() == null ? null : e.getDepartment().getName())
                .loanProductName(app.getLoanProduct().getName())
                .sequenceNumber(s.getSequenceNumber())
                .dueMonth(s.getDueMonth())
                .expectedAmount(s.getExpectedAmount())
                .paidAmount(s.getPaidAmount())
                .outstandingAmount(s.getOutstandingAmount())
                .status(s.getStatus())
                .payrollRunId(s.getPayrollRunId())
                .missedAt(s.getMissedAt())
                .loanOutstandingBalance(account.getOutstandingBalance())
                .build();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private EmployeeLoanApplication loadOwned(Long id, Long employeeId) {
        return applicationRepository.findByIdAndEmployeeId(id, employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Loan application not found: " + id));
    }

    /** HR, Finance and custom approvers never see another employee's draft. */
    private EmployeeLoanApplication loadVisibleToStaff(Long id) {
        EmployeeLoanApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Loan application not found: " + id));
        if (app.getStatus() == LoanApplicationStatus.DRAFT) {
            throw new EntityNotFoundException("Loan application not found: " + id);
        }
        return app;
    }

    private void requireEditable(EmployeeLoanApplication app, String action) {
        if (!EDITABLE.contains(app.getStatus())) {
            throw new IllegalStateException("Only draft or returned applications can be " + action + ".");
        }
    }

    private void requireActiveProduct(LoanProduct product) {
        if (!product.isActive()) {
            throw new IllegalStateException("Loan product '" + product.getName() + "' is not available.");
        }
    }

    private LoanRepaymentPreviewResponse validate(LoanProduct product, LoanApplicationCommand c) {
        if (c.getPurpose() == null || c.getPurpose().isBlank()) {
            throw new IllegalArgumentException("Purpose is required.");
        }
        return calculationService.validateOrThrow(product, c.getRequestedAmount(), c.getRepaymentAmount(),
                c.getTenorMonths(), c.getRepaymentStartMonth());
    }

    private void applyTerms(EmployeeLoanApplication app, LoanProduct product, LoanApplicationCommand c,
                            LoanRepaymentPreviewResponse calc) {
        app.setLoanProduct(product);
        app.setRequestedAmount(calc.getRequestedAmount());
        app.setRepaymentAmount(calc.getRepaymentAmount());
        app.setTenorMonths(calc.getTenorMonths());
        app.setRepaymentStartMonth(calc.getRepaymentStartMonth());
        app.setTotalInterestAmount(calc.getTotalInterestAmount());
        app.setTotalRepayableAmount(calc.getTotalRepayableAmount());
        app.setPurpose(c.getPurpose().trim());
    }

    private void applyEmployeeSnapshot(EmployeeLoanApplication app, EmployeeSnapshot s) {
        app.setDepartmentId(s.departmentId());
        app.setJobGradeNameSnapshot(s.jobGradeName());
        app.setJobStepNameSnapshot(s.jobStepName());
        app.setGrossSalarySnapshot(s.grossSalary());
    }

    private boolean hasApproval(Long applicationId) {
        return hasApproval(applicationId, stepRepository.findByLoanApplicationIdOrderBySequenceNoAscIdAsc(applicationId));
    }

    private boolean hasApproval(Long applicationId, List<EmployeeLoanApprovalStep> steps) {
        return latestAttempt(steps).stream().anyMatch(s -> s.getDecision() == LoanApprovalDecision.APPROVE);
    }

    private String pathName(Long pathId) {
        if (pathId == null) return null;
        return customApprovalPathRepository.findById(pathId).map(CustomApprovalPath::getName).orElse(null);
    }

    private static String stageLabel(EmployeeLoanApprovalStep s) {
        return switch (s.getApprovalStage()) {
            case HR -> "HR Approval";
            case FINANCE -> "Finance Approval";
            case CUSTOM -> "Custom Approval Level " + s.getSequenceNo();
        };
    }

    private static String ownerLabel(EmployeeLoanApprovalStep s, Map<Long, String> names) {
        if (s.getApproverEmployeeId() != null && names.get(s.getApproverEmployeeId()) != null) {
            return names.get(s.getApproverEmployeeId());
        }
        return switch (s.getApprovalStage()) {
            case HR -> "HR group";
            case FINANCE -> "Finance group";
            case CUSTOM -> s.getApproverGroup() != null ? s.getApproverGroup() : "Custom approver";
        };
    }

    private static long sum(Map<LoanApplicationStatus, Long> counts, LoanApplicationStatus... statuses) {
        long total = 0;
        for (LoanApplicationStatus s : statuses) total += counts.getOrDefault(s, 0L);
        return total;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}