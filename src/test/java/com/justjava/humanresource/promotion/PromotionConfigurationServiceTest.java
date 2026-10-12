package com.justjava.humanresource.promotion;

import com.justjava.humanresource.promotion.dto.PromotionConfigurationCommand;
import com.justjava.humanresource.promotion.dto.PromotionConfigurationResponse;
import com.justjava.humanresource.promotion.dto.PromotionRoutingRuleCommand;
import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import com.justjava.humanresource.promotion.enums.PromotionRequestSource;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionConfigurationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionConfigurationServiceTest {

    @Mock PromotionConfigurationRepository configurationRepository;
    @Mock PromotionAuthorizationService authorizationService;
    @Mock PromotionCurrentUserService currentUserService;

    private PromotionConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new PromotionConfigurationService(configurationRepository, authorizationService, currentUserService);
    }

    @Test
    void returnsDefaultConfigurationWhenNoActiveConfigurationExists() {
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.empty());

        PromotionConfigurationResponse response = service.getActiveConfiguration();

        assertThat(response.getName()).isEqualTo("Default promotion policy");
        assertThat(response.isRequireFinanceApproval()).isTrue();
        assertThat(response.isRequireExecutiveApproval()).isTrue();
        assertThat(response.getMaximumSalaryIncreasePercentWithoutExecutiveApproval())
                .isEqualByComparingTo("30.00");
    }

    @Test
    void updatesActiveConfiguration() {
        PromotionConfiguration existing = new PromotionConfiguration();
        existing.setId(1L);
        existing.setActive(true);
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(existing));
        when(currentUserService.currentUserDisplayName()).thenReturn("HR User");
        when(configurationRepository.save(org.mockito.Mockito.any(PromotionConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PromotionConfigurationCommand command = new PromotionConfigurationCommand();
        command.setName("Senior promotion policy");
        command.setMinimumMonthsInCurrentRole(12);
        command.setMinimumAppraisalScore(new BigDecimal("70.00"));
        command.setRequireFinanceApproval(false);
        command.setRequireExecutiveApproval(true);
        command.setMaximumSalaryIncreasePercentWithoutExecutiveApproval(new BigDecimal("20.00"));
        command.setApprovalReminderAfterHours(24);
        command.setApprovalEscalationAfterHours(48);
        command.setEscalationApproverId(90L);
        command.setEscalationApproverGroup("humanresource");
        command.setRequiredDocumentsBeforeSubmit(List.of(PromotionAttachmentType.APPRAISAL_SUMMARY));
        command.setRequiredDocumentsBeforeImplementation(List.of(PromotionAttachmentType.PROMOTION_LETTER));
        PromotionRoutingRuleCommand routingRule = new PromotionRoutingRuleCommand();
        routingRule.setName("Executive grade review");
        routingRule.setPriority(5);
        routingRule.setJobGradeId(40L);
        routingRule.setRequestSource(PromotionRequestSource.MANAGER_NOMINATED);
        routingRule.setExecutiveApprovalRequired(true);
        command.setRoutingRules(List.of(routingRule));

        PromotionConfigurationResponse response = service.saveActiveConfiguration(command);

        ArgumentCaptor<PromotionConfiguration> captor = ArgumentCaptor.forClass(PromotionConfiguration.class);
        verify(configurationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        PromotionConfiguration savedNewConfiguration = captor.getAllValues().get(1);
        assertThat(captor.getAllValues().get(0).isActive()).isFalse();
        assertThat(captor.getAllValues().get(0).getDeactivatedByName()).isEqualTo("HR User");
        assertThat(savedNewConfiguration.getName()).isEqualTo("Senior promotion policy");
        assertThat(savedNewConfiguration.isRequireFinanceApproval()).isFalse();
        assertThat(savedNewConfiguration.getApprovalReminderAfterHours()).isEqualTo(24);
        assertThat(savedNewConfiguration.getApprovalEscalationAfterHours()).isEqualTo(48);
        assertThat(savedNewConfiguration.getEscalationApproverId()).isEqualTo(90L);
        assertThat(savedNewConfiguration.getEscalationApproverGroup()).isEqualTo("humanresource");
        assertThat(savedNewConfiguration.getRequiredDocumentsBeforeSubmit())
                .containsExactly(PromotionAttachmentType.APPRAISAL_SUMMARY);
        assertThat(savedNewConfiguration.getRequiredDocumentsBeforeImplementation())
                .containsExactly(PromotionAttachmentType.PROMOTION_LETTER);
        assertThat(savedNewConfiguration.getRoutingRules()).hasSize(1);
        assertThat(savedNewConfiguration.getRoutingRules().getFirst().getName()).isEqualTo("Executive grade review");
        assertThat(savedNewConfiguration.getRoutingRules().getFirst().getJobGradeId()).isEqualTo(40L);
        assertThat(savedNewConfiguration.getCreatedByName()).isEqualTo("HR User");
        assertThat(response.getMaximumSalaryIncreasePercentWithoutExecutiveApproval())
                .isEqualByComparingTo("20.00");
        assertThat(response.getApprovalReminderAfterHours()).isEqualTo(24);
        assertThat(response.getApprovalEscalationAfterHours()).isEqualTo(48);
        assertThat(response.getEscalationApproverId()).isEqualTo(90L);
        assertThat(response.getEscalationApproverGroup()).isEqualTo("humanresource");
        assertThat(response.getRequiredDocumentsBeforeSubmit())
                .containsExactly(PromotionAttachmentType.APPRAISAL_SUMMARY);
        assertThat(response.getRequiredDocumentsBeforeImplementation())
                .containsExactly(PromotionAttachmentType.PROMOTION_LETTER);
        assertThat(response.getRoutingRules()).hasSize(1);
        assertThat(response.getRoutingRules().getFirst().getRequestSource())
                .isEqualTo(PromotionRequestSource.MANAGER_NOMINATED);
        assertThat(response.getRoutingRules().getFirst().getExecutiveApprovalRequired()).isTrue();
    }

    @Test
    void returnsConfigurationHistory() {
        PromotionConfiguration active = new PromotionConfiguration();
        active.setId(1L);
        PromotionConfiguration inactive = new PromotionConfiguration();
        inactive.setId(2L);
        inactive.setActive(false);
        when(configurationRepository.findAllByOrderByUpdatedAtDescIdDesc())
                .thenReturn(List.of(active, inactive));

        List<PromotionConfigurationResponse> response = service.history();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getId()).isEqualTo(1L);
        assertThat(response.get(1).isActive()).isFalse();
        verify(authorizationService).requireHrOrAdmin();
    }

    @Test
    void deactivatesActiveConfiguration() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setId(3L);
        configuration.setActive(true);
        when(configurationRepository.findById(3L)).thenReturn(Optional.of(configuration));
        when(currentUserService.currentUserDisplayName()).thenReturn("HR User");
        when(configurationRepository.save(configuration)).thenReturn(configuration);

        PromotionConfigurationResponse response = service.deactivate(3L);

        assertThat(response.isActive()).isFalse();
        assertThat(configuration.getDeactivatedAt()).isNotNull();
        assertThat(configuration.getDeactivatedByName()).isEqualTo("HR User");
        verify(configurationRepository).save(configuration);
    }

    @Test
    void delegatesAuthorizationChecks() {
        service.getActiveConfiguration();

        verify(authorizationService).requireHrOrAdmin();
    }
}
