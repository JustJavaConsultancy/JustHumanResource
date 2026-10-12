package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.promotion.dto.PromotionConfigurationCommand;
import com.justjava.humanresource.promotion.dto.PromotionConfigurationResponse;
import com.justjava.humanresource.promotion.dto.PromotionRoutingRuleCommand;
import com.justjava.humanresource.promotion.dto.PromotionRoutingRuleResponse;
import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.entity.PromotionRoutingRule;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionConfigurationService {

    private final PromotionConfigurationRepository configurationRepository;
    private final PromotionAuthorizationService authorizationService;
    private final PromotionCurrentUserService currentUserService;

    @Transactional(readOnly = true)
    @PreAuthorize("@promotionSecurity.canConfigurePromotions()")
    public PromotionConfigurationResponse getActiveConfiguration() {
        authorizationService.requireHrOrAdmin();
        return toResponse(activeConfiguration());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@promotionSecurity.canConfigurePromotions()")
    public List<PromotionConfigurationResponse> history() {
        authorizationService.requireHrOrAdmin();
        return configurationRepository.findAllByOrderByUpdatedAtDescIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("@promotionSecurity.canConfigurePromotions()")
    public PromotionConfigurationResponse saveActiveConfiguration(PromotionConfigurationCommand command) {
        authorizationService.requireHrOrAdmin();
        String actorName = currentUserService.currentUserDisplayName();
        configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc()
                .ifPresent(active -> {
                    active.setActive(false);
                    active.setDeactivatedAt(LocalDateTime.now());
                    active.setDeactivatedByName(actorName);
                    active.setUpdatedByName(actorName);
                    configurationRepository.save(active);
                });
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setName(command.getName());
        configuration.setMinimumMonthsInCurrentRole(command.getMinimumMonthsInCurrentRole());
        configuration.setMinimumAppraisalScore(command.getMinimumAppraisalScore());
        configuration.setAllowProbationPromotion(command.isAllowProbationPromotion());
        configuration.setRequireFinanceApproval(command.isRequireFinanceApproval());
        configuration.setRequireExecutiveApproval(command.isRequireExecutiveApproval());
        configuration.setMaximumSalaryIncreasePercentWithoutExecutiveApproval(
                command.getMaximumSalaryIncreasePercentWithoutExecutiveApproval() == null
                        ? PromotionConfiguration.DEFAULT_EXECUTIVE_APPROVAL_PERCENT_THRESHOLD
                        : command.getMaximumSalaryIncreasePercentWithoutExecutiveApproval()
        );
        configuration.setApprovalReminderAfterHours(command.getApprovalReminderAfterHours());
        configuration.setApprovalEscalationAfterHours(command.getApprovalEscalationAfterHours());
        configuration.setEscalationApproverId(command.getEscalationApproverId());
        configuration.setEscalationApproverGroup(blankToNull(command.getEscalationApproverGroup()));
        configuration.setRequiredDocumentsBeforeSubmit(copy(command.getRequiredDocumentsBeforeSubmit()));
        configuration.setRequiredDocumentsBeforeImplementation(copy(command.getRequiredDocumentsBeforeImplementation()));
        configuration.setRoutingRules(toRoutingRules(command.getRoutingRules()));
        configuration.setActive(true);
        configuration.setCreatedByName(actorName);
        configuration.setUpdatedByName(actorName);
        return toResponse(configurationRepository.save(configuration));
    }

    @Transactional
    @PreAuthorize("@promotionSecurity.canConfigurePromotions()")
    public PromotionConfigurationResponse deactivate(Long id) {
        authorizationService.requireHrOrAdmin();
        PromotionConfiguration configuration = configurationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promotion configuration not found: " + id));
        if (!configuration.isActive()) {
            return toResponse(configuration);
        }
        String actorName = currentUserService.currentUserDisplayName();
        configuration.setActive(false);
        configuration.setDeactivatedAt(LocalDateTime.now());
        configuration.setDeactivatedByName(actorName);
        configuration.setUpdatedByName(actorName);
        return toResponse(configurationRepository.save(configuration));
    }

    private PromotionConfiguration activeConfiguration() {
        return configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc()
                .orElseGet(PromotionConfiguration::new);
    }

    private PromotionConfigurationResponse toResponse(PromotionConfiguration configuration) {
        return PromotionConfigurationResponse.builder()
                .id(configuration.getId())
                .name(configuration.getName())
                .minimumMonthsInCurrentRole(configuration.getMinimumMonthsInCurrentRole())
                .minimumAppraisalScore(configuration.getMinimumAppraisalScore())
                .allowProbationPromotion(configuration.isAllowProbationPromotion())
                .requireFinanceApproval(configuration.isRequireFinanceApproval())
                .requireExecutiveApproval(configuration.isRequireExecutiveApproval())
                .maximumSalaryIncreasePercentWithoutExecutiveApproval(
                        configuration.getMaximumSalaryIncreasePercentWithoutExecutiveApproval())
                .approvalReminderAfterHours(configuration.getApprovalReminderAfterHours())
                .approvalEscalationAfterHours(configuration.getApprovalEscalationAfterHours())
                .escalationApproverId(configuration.getEscalationApproverId())
                .escalationApproverGroup(configuration.getEscalationApproverGroup())
                .requiredDocumentsBeforeSubmit(copy(configuration.getRequiredDocumentsBeforeSubmit()))
                .requiredDocumentsBeforeImplementation(copy(configuration.getRequiredDocumentsBeforeImplementation()))
                .routingRules(toRoutingRuleResponses(configuration.getRoutingRules()))
                .active(configuration.isActive())
                .createdAt(configuration.getCreatedAt())
                .updatedAt(configuration.getUpdatedAt())
                .createdByName(configuration.getCreatedByName())
                .updatedByName(configuration.getUpdatedByName())
                .deactivatedAt(configuration.getDeactivatedAt())
                .deactivatedByName(configuration.getDeactivatedByName())
                .build();
    }

    private <T> List<T> copy(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<PromotionRoutingRule> toRoutingRules(List<PromotionRoutingRuleCommand> commands) {
        if (commands == null) {
            return new ArrayList<>();
        }
        return commands.stream()
                .map(this::toRoutingRule)
                .toList();
    }

    private PromotionRoutingRule toRoutingRule(PromotionRoutingRuleCommand command) {
        PromotionRoutingRule rule = new PromotionRoutingRule();
        rule.setName(command.getName());
        rule.setPriority(command.getPriority() == null ? 100 : command.getPriority());
        rule.setDepartmentId(command.getDepartmentId());
        rule.setJobGradeId(command.getJobGradeId());
        rule.setEmployeeCategory(command.getEmployeeCategory());
        rule.setRequestSource(command.getRequestSource());
        rule.setFinanceApprovalRequired(command.getFinanceApprovalRequired());
        rule.setExecutiveApprovalRequired(command.getExecutiveApprovalRequired());
        return rule;
    }

    private List<PromotionRoutingRuleResponse> toRoutingRuleResponses(List<PromotionRoutingRule> rules) {
        if (rules == null) {
            return new ArrayList<>();
        }
        return rules.stream()
                .map(rule -> PromotionRoutingRuleResponse.builder()
                        .name(rule.getName())
                        .priority(rule.getPriority())
                        .departmentId(rule.getDepartmentId())
                        .jobGradeId(rule.getJobGradeId())
                        .employeeCategory(rule.getEmployeeCategory())
                        .requestSource(rule.getRequestSource())
                        .financeApprovalRequired(rule.getFinanceApprovalRequired())
                        .executiveApprovalRequired(rule.getExecutiveApprovalRequired())
                        .build())
                .toList();
    }

}
