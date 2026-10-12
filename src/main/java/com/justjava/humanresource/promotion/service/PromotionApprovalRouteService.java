package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.promotion.dto.PromotionApprovalRoutePreviewResponse;
import com.justjava.humanresource.promotion.dto.PromotionApprovalStepResponse;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionRoutingRule;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PromotionApprovalRouteService {

    private static final String HR_GROUP = "humanresource";
    private static final String FINANCE_GROUP = "financialofficers";
    private static final String EXECUTIVE_GROUP = "executive";

    private final PromotionApprovalStepRepository approvalStepRepository;
    private final PromotionConfigurationRepository configurationRepository;

    @Transactional
    public void createApprovalRoute(PromotionRequest request) {
        approvalStepRepository.deleteByPromotionRequest_Id(request.getId());
        approvalStepRepository.flush();

        route(request).steps().forEach(step ->
                approvalStepRepository.save(step(request, step.stage(), step.sequenceNumber(), step.approver(), step.approverGroup())));
    }

    @Transactional(readOnly = true)
    public PromotionApprovalRoutePreviewResponse previewApprovalRoute(PromotionRequest request) {
        Route route = route(request);
        return PromotionApprovalRoutePreviewResponse.builder()
                .promotionRequestId(request.getId())
                .requestNumber(request.getRequestNumber())
                .steps(route.steps().stream().map(this::toResponse).toList())
                .matchedRoutingRules(route.matchedRuleNames())
                .build();
    }

    private PromotionConfiguration activeConfiguration() {
        return configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc()
                .orElseGet(PromotionConfiguration::new);
    }

    private boolean requiresFinanceApproval(PromotionRequest request, PromotionConfiguration configuration) {
        return configuration.isRequireFinanceApproval()
                && nullSafe(request.getSalaryIncreaseAmount()).compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean requiresExecutiveApproval(PromotionRequest request, PromotionConfiguration configuration) {
        if (!configuration.isRequireExecutiveApproval()) {
            return false;
        }
        BigDecimal threshold = configuration.getMaximumSalaryIncreasePercentWithoutExecutiveApproval();
        if (threshold == null) {
            threshold = PromotionConfiguration.DEFAULT_EXECUTIVE_APPROVAL_PERCENT_THRESHOLD;
        }
        return nullSafe(request.getSalaryIncreasePercent()).compareTo(threshold) >= 0;
    }

    private PromotionApprovalStep step(PromotionRequest request, PromotionApprovalStage stage, int sequence,
                                       Employee approver, String approverGroup) {
        PromotionApprovalStep step = new PromotionApprovalStep();
        step.setPromotionRequest(request);
        step.setStage(stage);
        step.setSequenceNumber(sequence);
        step.setApprover(approver);
        step.setApproverGroup(approverGroup);
        step.setDecision(PromotionApprovalDecision.PENDING);
        step.setAssignedAt(LocalDateTime.now());
        return step;
    }

    private Route route(PromotionRequest request) {
        PromotionConfiguration configuration = activeConfiguration();
        List<PromotionRoutingRule> matchedRules = matchedRules(request, configuration);
        List<RouteStep> steps = new ArrayList<>();
        int sequence = 1;
        Employee manager = request.getCurrentManager();
        if (manager != null && request.getEmployee() != null && !manager.getId().equals(request.getEmployee().getId())) {
            steps.add(new RouteStep(PromotionApprovalStage.LINE_MANAGER, sequence++, manager, null));
        }
        steps.add(new RouteStep(PromotionApprovalStage.HR, sequence++, null, HR_GROUP));
        if (requiresFinanceApproval(request, configuration, matchedRules)) {
            steps.add(new RouteStep(PromotionApprovalStage.FINANCE, sequence++, null, FINANCE_GROUP));
        }
        if (requiresExecutiveApproval(request, configuration, matchedRules)) {
            steps.add(new RouteStep(PromotionApprovalStage.EXECUTIVE, sequence, null, EXECUTIVE_GROUP));
        }
        return new Route(steps, matchedRules.stream()
                .map(this::ruleName)
                .toList());
    }

    private boolean requiresFinanceApproval(PromotionRequest request, PromotionConfiguration configuration,
                                            List<PromotionRoutingRule> matchedRules) {
        return firstFinanceOverride(matchedRules)
                .orElseGet(() -> requiresFinanceApproval(request, configuration));
    }

    private boolean requiresExecutiveApproval(PromotionRequest request, PromotionConfiguration configuration,
                                              List<PromotionRoutingRule> matchedRules) {
        return firstExecutiveOverride(matchedRules)
                .orElseGet(() -> requiresExecutiveApproval(request, configuration));
    }

    private java.util.Optional<Boolean> firstFinanceOverride(List<PromotionRoutingRule> matchedRules) {
        return matchedRules.stream()
                .map(PromotionRoutingRule::getFinanceApprovalRequired)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private java.util.Optional<Boolean> firstExecutiveOverride(List<PromotionRoutingRule> matchedRules) {
        return matchedRules.stream()
                .map(PromotionRoutingRule::getExecutiveApprovalRequired)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private List<PromotionRoutingRule> matchedRules(PromotionRequest request, PromotionConfiguration configuration) {
        if (configuration.getRoutingRules() == null || configuration.getRoutingRules().isEmpty()) {
            return List.of();
        }
        return configuration.getRoutingRules().stream()
                .filter(rule -> matches(rule, request))
                .sorted(Comparator
                        .comparing((PromotionRoutingRule rule) -> rule.getPriority() == null ? 100 : rule.getPriority())
                        .thenComparing(this::ruleName))
                .toList();
    }

    private boolean matches(PromotionRoutingRule rule, PromotionRequest request) {
        return matchesDepartment(rule, request)
                && matchesJobGrade(rule, request)
                && matchesEmployeeCategory(rule, request)
                && matchesRequestSource(rule, request);
    }

    private boolean matchesDepartment(PromotionRoutingRule rule, PromotionRequest request) {
        if (rule.getDepartmentId() == null) {
            return true;
        }
        Department department = request.getProposedDepartment();
        if (department == null && request.getProposedRole() != null) {
            department = request.getProposedRole().getDepartment();
        }
        return department != null && Objects.equals(department.getId(), rule.getDepartmentId());
    }

    private boolean matchesJobGrade(PromotionRoutingRule rule, PromotionRequest request) {
        if (rule.getJobGradeId() == null) {
            return true;
        }
        JobStep proposedRole = request.getProposedRole();
        JobGrade grade = proposedRole == null ? null : proposedRole.getJobGrade();
        return grade != null && Objects.equals(grade.getId(), rule.getJobGradeId());
    }

    private boolean matchesEmployeeCategory(PromotionRoutingRule rule, PromotionRequest request) {
        if (rule.getEmployeeCategory() == null || rule.getEmployeeCategory().isBlank()) {
            return true;
        }
        Employee employee = request.getEmployee();
        if (employee == null || employee.getGroups() == null) {
            return false;
        }
        String expected = normalize(rule.getEmployeeCategory());
        return employee.getGroups().stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .anyMatch(expected::equals);
    }

    private boolean matchesRequestSource(PromotionRoutingRule rule, PromotionRequest request) {
        return rule.getRequestSource() == null || rule.getRequestSource() == request.getRequestSource();
    }

    private String ruleName(PromotionRoutingRule rule) {
        if (rule.getName() != null && !rule.getName().isBlank()) {
            return rule.getName();
        }
        return "Routing rule " + (rule.getPriority() == null ? 100 : rule.getPriority());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private PromotionApprovalStepResponse toResponse(RouteStep step) {
        Employee approver = step.approver();
        return PromotionApprovalStepResponse.builder()
                .stage(step.stage())
                .decision(PromotionApprovalDecision.PENDING)
                .approverId(approver == null ? null : approver.getId())
                .approverName(approver == null ? null : approver.getFullName())
                .approverGroup(step.approverGroup())
                .sequenceNumber(step.sequenceNumber())
                .build();
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Route(List<RouteStep> steps, List<String> matchedRuleNames) {
    }

    private record RouteStep(PromotionApprovalStage stage, int sequenceNumber, Employee approver, String approverGroup) {
    }
}
