package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.promotion.dto.PromotionApprovalRoutePreviewResponse;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionRoutingRule;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.enums.PromotionRequestSource;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import com.justjava.humanresource.promotion.service.PromotionApprovalRouteService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionApprovalRouteServiceTest {

    @Mock PromotionApprovalStepRepository approvalStepRepository;
    @Mock PromotionConfigurationRepository configurationRepository;

    private PromotionApprovalRouteService service;

    @BeforeEach
    void setUp() {
        service = new PromotionApprovalRouteService(approvalStepRepository, configurationRepository);
    }

    @Test
    void createsExecutiveApprovalStepWhenSalaryIncreaseMeetsThreshold() {
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.empty());
        PromotionRequest request = requestWithManager();
        request.setSalaryIncreaseAmount(new BigDecimal("50000.00"));
        request.setSalaryIncreasePercent(new BigDecimal("30.00"));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(4)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.FINANCE,
                        PromotionApprovalStage.EXECUTIVE
                );
    }

    @Test
    void omitsExecutiveApprovalStepBelowThreshold() {
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.empty());
        PromotionRequest request = requestWithManager();
        request.setSalaryIncreaseAmount(new BigDecimal("50000.00"));
        request.setSalaryIncreasePercent(new BigDecimal("29.99"));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(3)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.FINANCE
                );
    }

    @Test
    void usesConfiguredExecutiveApprovalThreshold() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setMaximumSalaryIncreasePercentWithoutExecutiveApproval(new BigDecimal("15.00"));
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = requestWithManager();
        request.setSalaryIncreaseAmount(new BigDecimal("25000.00"));
        request.setSalaryIncreasePercent(new BigDecimal("15.00"));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(4)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.FINANCE,
                        PromotionApprovalStage.EXECUTIVE
                );
    }

    @Test
    void skipsFinanceApprovalWhenConfigurationDisablesIt() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setRequireFinanceApproval(false);
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = requestWithManager();
        request.setSalaryIncreaseAmount(new BigDecimal("25000.00"));
        request.setSalaryIncreasePercent(new BigDecimal("10.00"));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR
                );
    }

    @Test
    void skipsExecutiveApprovalWhenConfigurationDisablesIt() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setRequireExecutiveApproval(false);
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = requestWithManager();
        request.setSalaryIncreaseAmount(new BigDecimal("50000.00"));
        request.setSalaryIncreasePercent(new BigDecimal("50.00"));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        verify(approvalStepRepository, never()).save(org.mockito.ArgumentMatchers.argThat(
                step -> step.getStage() == PromotionApprovalStage.EXECUTIVE
        ));

        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.FINANCE
                );
    }

    @Test
    void previewsApprovalRouteWithoutPersistingSteps() {
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.empty());
        PromotionRequest request = requestWithManager();
        request.setRequestNumber("PROM-2026-0001");
        request.setSalaryIncreaseAmount(new BigDecimal("50000.00"));
        request.setSalaryIncreasePercent(new BigDecimal("30.00"));

        PromotionApprovalRoutePreviewResponse response = service.previewApprovalRoute(request);

        assertThat(response.getPromotionRequestId()).isEqualTo(request.getId());
        assertThat(response.getRequestNumber()).isEqualTo("PROM-2026-0001");
        assertThat(response.getSteps())
                .extracting(step -> step.getStage())
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.FINANCE,
                        PromotionApprovalStage.EXECUTIVE
                );
        assertThat(response.getSteps())
                .extracting(step -> step.getDecision())
                .containsOnly(PromotionApprovalDecision.PENDING);
        verify(approvalStepRepository, never()).save(org.mockito.ArgumentMatchers.any(PromotionApprovalStep.class));
    }

    @Test
    void departmentRuleCanRequireFinanceApproval() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setRequireFinanceApproval(false);
        PromotionRoutingRule rule = new PromotionRoutingRule();
        rule.setName("Operations finance review");
        rule.setPriority(10);
        rule.setDepartmentId(20L);
        rule.setFinanceApprovalRequired(true);
        configuration.setRoutingRules(List.of(rule));
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = requestWithManager();
        request.setProposedDepartment(department(20L));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.FINANCE
                );
    }

    @Test
    void gradeRuleCanRequireExecutiveApproval() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setRequireExecutiveApproval(false);
        PromotionRoutingRule rule = new PromotionRoutingRule();
        rule.setName("Senior grade executive review");
        rule.setPriority(10);
        rule.setJobGradeId(99L);
        rule.setExecutiveApprovalRequired(true);
        configuration.setRoutingRules(List.of(rule));
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = requestWithManager();
        request.setProposedRole(jobStep(10L, 99L, 20L));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.EXECUTIVE
                );
    }

    @Test
    void sourceRuleCanSkipExecutiveApproval() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        PromotionRoutingRule rule = new PromotionRoutingRule();
        rule.setName("Bulk cycle skip executive");
        rule.setPriority(10);
        rule.setRequestSource(PromotionRequestSource.BULK_PROMOTION_CYCLE);
        rule.setExecutiveApprovalRequired(false);
        configuration.setRoutingRules(List.of(rule));
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = requestWithManager();
        request.setRequestSource(PromotionRequestSource.BULK_PROMOTION_CYCLE);
        request.setSalaryIncreaseAmount(new BigDecimal("25000.00"));
        request.setSalaryIncreasePercent(new BigDecimal("80.00"));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.FINANCE
                );
    }

    @Test
    void employeeCategoryRuleMatchesEmployeeGroup() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setRequireExecutiveApproval(false);
        PromotionRoutingRule rule = new PromotionRoutingRule();
        rule.setName("Contractor executive review");
        rule.setPriority(10);
        rule.setEmployeeCategory("contractor");
        rule.setExecutiveApprovalRequired(true);
        configuration.setRoutingRules(List.of(rule));
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = requestWithManager();
        request.getEmployee().setGroups(List.of("Contractor"));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.EXECUTIVE
                );
    }

    @Test
    void lowerPriorityMatchedRuleWinsForFinanceOverride() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        PromotionRoutingRule skipFinance = new PromotionRoutingRule();
        skipFinance.setName("Priority skip finance");
        skipFinance.setPriority(1);
        skipFinance.setDepartmentId(20L);
        skipFinance.setFinanceApprovalRequired(false);
        PromotionRoutingRule requireFinance = new PromotionRoutingRule();
        requireFinance.setName("Fallback finance review");
        requireFinance.setPriority(50);
        requireFinance.setDepartmentId(20L);
        requireFinance.setFinanceApprovalRequired(true);
        configuration.setRoutingRules(List.of(requireFinance, skipFinance));
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = requestWithManager();
        request.setProposedDepartment(department(20L));
        request.setSalaryIncreaseAmount(new BigDecimal("25000.00"));

        service.createApprovalRoute(request);

        ArgumentCaptor<PromotionApprovalStep> captor = ArgumentCaptor.forClass(PromotionApprovalStep.class);
        verify(approvalStepRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PromotionApprovalStep::getStage)
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR
                );
    }

    @Test
    void previewIncludesMatchedRoutingRulesInPrecedenceOrder() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        PromotionRoutingRule sourceRule = new PromotionRoutingRule();
        sourceRule.setName("Source rule");
        sourceRule.setPriority(20);
        sourceRule.setRequestSource(PromotionRequestSource.MANAGER_NOMINATED);
        sourceRule.setExecutiveApprovalRequired(true);
        PromotionRoutingRule departmentRule = new PromotionRoutingRule();
        departmentRule.setName("Department rule");
        departmentRule.setPriority(5);
        departmentRule.setDepartmentId(20L);
        departmentRule.setFinanceApprovalRequired(true);
        configuration.setRoutingRules(List.of(sourceRule, departmentRule));
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = requestWithManager();
        request.setRequestNumber("PROM-2026-0002");
        request.setRequestSource(PromotionRequestSource.MANAGER_NOMINATED);
        request.setProposedDepartment(department(20L));

        PromotionApprovalRoutePreviewResponse response = service.previewApprovalRoute(request);

        assertThat(response.getMatchedRoutingRules())
                .containsExactly("Department rule", "Source rule");
        assertThat(response.getSteps())
                .extracting(step -> step.getStage())
                .containsExactly(
                        PromotionApprovalStage.LINE_MANAGER,
                        PromotionApprovalStage.HR,
                        PromotionApprovalStage.FINANCE,
                        PromotionApprovalStage.EXECUTIVE
                );
        verify(approvalStepRepository, never()).save(org.mockito.ArgumentMatchers.any(PromotionApprovalStep.class));
    }

    private PromotionRequest requestWithManager() {
        Employee employee = new Employee();
        employee.setId(1L);
        Employee manager = new Employee();
        manager.setId(2L);

        PromotionRequest request = new PromotionRequest();
        request.setId(10L);
        request.setEmployee(employee);
        request.setCurrentManager(manager);
        return request;
    }

    private Department department(Long id) {
        Department department = new Department();
        department.setId(id);
        return department;
    }

    private JobStep jobStep(Long id, Long gradeId, Long departmentId) {
        JobGrade grade = new JobGrade();
        grade.setId(gradeId);
        JobStep step = new JobStep();
        step.setId(id);
        step.setJobGrade(grade);
        step.setDepartment(department(departmentId));
        return step;
    }
}
