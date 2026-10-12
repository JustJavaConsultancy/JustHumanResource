package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.promotion.dto.PromotionEligibilityOverrideCommand;
import com.justjava.humanresource.promotion.entity.PromotionEligibilityCheck;
import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionEligibilitySeverity;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import com.justjava.humanresource.promotion.repository.PromotionEligibilityCheckRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PromotionEligibilityService {

    private static final BigDecimal DEFAULT_MIN_APPRAISAL_SCORE = new BigDecimal("70.00");
    private static final int DEFAULT_MIN_MONTHS_IN_ROLE = 6;

    private final PromotionEligibilityCheckRepository checkRepository;
    private final PromotionRequestRepository requestRepository;
    private final PromotionConfigurationRepository configurationRepository;
    private final EmployeePositionHistoryRepository positionHistoryRepository;
    private final PromotionAuthorizationService authorizationService;
    private final PromotionCurrentUserService currentUserService;
    private final PromotionActivityService activityService;

    @Transactional
    public List<PromotionEligibilityCheck> evaluate(PromotionRequest request) {
        PromotionConfiguration configuration = activeConfiguration();
        BigDecimal minimumAppraisalScore = minimumAppraisalScore(configuration);
        int minimumMonthsInRole = minimumMonthsInRole(configuration);
        checkRepository.deleteByPromotionRequest_Id(request.getId());
        List<PromotionEligibilityCheck> checks = List.of(
                check(request, "EMPLOYEE_ACTIVE", "Employee is active", isEmployeeActive(request),
                        PromotionEligibilitySeverity.BLOCKER, "Employee must be active or confirmed."),
                check(request, "NOT_PROBATION", "Employee is not on probation",
                        isNotOnProbation(request, configuration),
                        probationSeverity(configuration), "Employee is still on probation."),
                check(request, "ROLE_CHANGED", "Proposed role differs from current role", hasRoleChanged(request),
                        PromotionEligibilitySeverity.BLOCKER, "Proposed role must differ from current role."),
                check(request, "NO_DUPLICATE_ACTIVE_REQUEST", "No duplicate active promotion request",
                        hasNoDuplicateActiveRequest(request), PromotionEligibilitySeverity.BLOCKER,
                        "Employee already has an active promotion request."),
                check(request, "APPRAISAL_SCORE", "Minimum appraisal score met",
                        hasMinimumAppraisalScore(request, minimumAppraisalScore),
                        PromotionEligibilitySeverity.WARNING,
                        "Appraisal score is below " + minimumAppraisalScore + "."),
                check(request, "TIME_IN_ROLE", "Minimum time in current role met",
                        hasMinimumTimeInRole(request, minimumMonthsInRole),
                        PromotionEligibilitySeverity.WARNING,
                        "Employee has less than " + minimumMonthsInRole + " months in current role."),
                check(request, "EFFECTIVE_DATE", "Effective date is valid", hasValidEffectiveDate(request),
                        PromotionEligibilitySeverity.BLOCKER, "Effective date must be today or in the future.")
        );
        return checkRepository.saveAll(checks);
    }

    public void assertCanSubmit(PromotionRequest request) {
        List<PromotionEligibilityCheck> checks = evaluate(request);
        boolean hasBlocker = checks.stream()
                .anyMatch(c -> !c.isPassed() && c.getSeverity() == PromotionEligibilitySeverity.BLOCKER);
        if (hasBlocker) {
            throw new IllegalStateException("Promotion request has blocking eligibility failures.");
        }
    }

    @Transactional
    public PromotionEligibilityCheck override(Long requestId, Long checkId, PromotionEligibilityOverrideCommand command) {
        authorizationService.requireHrOrAdmin();
        PromotionEligibilityCheck check = checkRepository.findById(checkId)
                .orElseThrow(() -> new IllegalArgumentException("Promotion eligibility check not found: " + checkId));
        if (!check.getPromotionRequest().getId().equals(requestId)) {
            throw new IllegalArgumentException("Promotion eligibility check does not belong to this request.");
        }
        if (check.isPassed()) {
            throw new IllegalStateException("Passed eligibility checks do not need an override.");
        }
        if (!check.isOverrideAllowed()) {
            throw new IllegalStateException("This eligibility check cannot be overridden.");
        }
        String reason = command == null ? null : command.getReason();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("An override reason is required.");
        }
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        check.setOverridden(true);
        check.setOverriddenBy(actor);
        check.setOverrideReason(reason.trim());
        check.setOverriddenAt(LocalDateTime.now());
        PromotionEligibilityCheck saved = checkRepository.save(check);
        activityService.record(check.getPromotionRequest(), PromotionActivityType.ELIGIBILITY_OVERRIDDEN, actor,
                "Eligibility check overridden: " + check.getCheckName() + ".");
        return saved;
    }

    private PromotionEligibilityCheck check(PromotionRequest request, String code, String name, boolean passed,
                                            PromotionEligibilitySeverity severity, String failureMessage) {
        PromotionEligibilityCheck check = new PromotionEligibilityCheck();
        check.setPromotionRequest(request);
        check.setCheckCode(code);
        check.setCheckName(name);
        check.setPassed(passed);
        check.setSeverity(severity);
        check.setMessage(passed ? "Passed" : failureMessage);
        check.setOverrideAllowed(severity == PromotionEligibilitySeverity.WARNING);
        check.setCheckedAt(LocalDateTime.now());
        return check;
    }

    private boolean isEmployeeActive(PromotionRequest request) {
        if (request.getEmployee() == null) {
            return false;
        }
        return request.getEmployee().getStatus() == RecordStatus.ACTIVE
                && (request.getEmployee().getEmploymentStatus() == EmploymentStatus.ACTIVE
                || request.getEmployee().getEmploymentStatus() == EmploymentStatus.CONFIRMED);
    }

    private boolean isNotOnProbation(PromotionRequest request, PromotionConfiguration configuration) {
        if (configuration.isAllowProbationPromotion()) {
            return true;
        }
        return request.getEmployee() != null
                && request.getEmployee().getEmploymentStatus() != EmploymentStatus.PROBATION;
    }

    private boolean hasRoleChanged(PromotionRequest request) {
        if (request.getCurrentRole() == null || request.getProposedRole() == null) {
            return false;
        }
        return !request.getCurrentRole().getId().equals(request.getProposedRole().getId());
    }

    private boolean hasNoDuplicateActiveRequest(PromotionRequest request) {
        if (request.getEmployee() == null) {
            return false;
        }
        Set<PromotionStatus> activeStatuses = Set.of(
                PromotionStatus.DRAFT,
                PromotionStatus.SUBMITTED,
                PromotionStatus.ELIGIBILITY_REVIEW,
                PromotionStatus.LINE_MANAGER_REVIEW,
                PromotionStatus.HR_REVIEW,
                PromotionStatus.FINANCE_REVIEW,
                PromotionStatus.EXECUTIVE_REVIEW,
                PromotionStatus.APPROVED
        );
        return requestRepository.findByEmployee_IdOrderByRequestedAtDesc(request.getEmployee().getId()).stream()
                .filter(existing -> !existing.getId().equals(request.getId()))
                .noneMatch(existing -> activeStatuses.contains(existing.getStatus()));
    }

    private boolean hasMinimumAppraisalScore(PromotionRequest request, BigDecimal minimumAppraisalScore) {
        return request.getAppraisalScore() != null
                && request.getAppraisalScore().compareTo(minimumAppraisalScore) >= 0;
    }

    private boolean hasMinimumTimeInRole(PromotionRequest request, int minimumMonthsInRole) {
        if (request.getEmployee() == null) {
            return true;
        }
        LocalDate roleStartDate = positionHistoryRepository.findCurrentPosition(request.getEmployee().getId())
                .map(position -> position.getEffectiveFrom())
                .orElse(request.getEmployee().getDateOfHire());
        if (roleStartDate == null) {
            return true;
        }
        return ChronoUnit.MONTHS.between(roleStartDate, LocalDate.now()) >= minimumMonthsInRole;
    }

    private boolean hasValidEffectiveDate(PromotionRequest request) {
        return request.getEffectiveDate() != null && !request.getEffectiveDate().isBefore(LocalDate.now());
    }

    private PromotionConfiguration activeConfiguration() {
        return configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc()
                .orElseGet(PromotionConfiguration::new);
    }

    private BigDecimal minimumAppraisalScore(PromotionConfiguration configuration) {
        return configuration.getMinimumAppraisalScore() == null
                ? DEFAULT_MIN_APPRAISAL_SCORE
                : configuration.getMinimumAppraisalScore();
    }

    private int minimumMonthsInRole(PromotionConfiguration configuration) {
        return configuration.getMinimumMonthsInCurrentRole() == null
                ? DEFAULT_MIN_MONTHS_IN_ROLE
                : configuration.getMinimumMonthsInCurrentRole();
    }

    private PromotionEligibilitySeverity probationSeverity(PromotionConfiguration configuration) {
        return configuration.isAllowProbationPromotion()
                ? PromotionEligibilitySeverity.INFO
                : PromotionEligibilitySeverity.WARNING;
    }
}
