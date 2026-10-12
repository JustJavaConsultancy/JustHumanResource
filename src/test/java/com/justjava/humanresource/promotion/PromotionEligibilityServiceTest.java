package com.justjava.humanresource.promotion;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.entity.PromotionEligibilityCheck;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import com.justjava.humanresource.promotion.repository.PromotionEligibilityCheckRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import com.justjava.humanresource.promotion.service.PromotionEligibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionEligibilityServiceTest {

    @Mock PromotionEligibilityCheckRepository checkRepository;
    @Mock PromotionRequestRepository requestRepository;
    @Mock PromotionConfigurationRepository configurationRepository;
    @Mock EmployeePositionHistoryRepository positionHistoryRepository;
    @Mock PromotionAuthorizationService authorizationService;
    @Mock PromotionCurrentUserService currentUserService;
    @Mock PromotionActivityService activityService;

    private PromotionEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new PromotionEligibilityService(
                checkRepository,
                requestRepository,
                configurationRepository,
                positionHistoryRepository,
                authorizationService,
                currentUserService,
                activityService
        );
        when(checkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void usesDefaultEligibilityThresholdsWhenNoConfigurationExists() {
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.empty());
        PromotionRequest request = validRequest();
        hasNoOtherActiveRequest(request);
        currentPositionStarted(request, LocalDate.now().minusMonths(5));
        request.setAppraisalScore(new BigDecimal("69.99"));

        List<PromotionEligibilityCheck> checks = service.evaluate(request);

        assertThat(check(checks, "APPRAISAL_SCORE").isPassed()).isFalse();
        assertThat(check(checks, "APPRAISAL_SCORE").getMessage()).isEqualTo("Appraisal score is below 70.00.");
        assertThat(check(checks, "TIME_IN_ROLE").isPassed()).isFalse();
        assertThat(check(checks, "TIME_IN_ROLE").getMessage())
                .isEqualTo("Employee has less than 6 months in current role.");
    }

    @Test
    void usesConfiguredEligibilityThresholds() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setMinimumAppraisalScore(new BigDecimal("80.00"));
        configuration.setMinimumMonthsInCurrentRole(12);
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = validRequest();
        hasNoOtherActiveRequest(request);
        currentPositionStarted(request, LocalDate.now().minusMonths(11));
        request.setAppraisalScore(new BigDecimal("79.99"));

        List<PromotionEligibilityCheck> checks = service.evaluate(request);

        assertThat(check(checks, "APPRAISAL_SCORE").isPassed()).isFalse();
        assertThat(check(checks, "APPRAISAL_SCORE").getMessage()).isEqualTo("Appraisal score is below 80.00.");
        assertThat(check(checks, "TIME_IN_ROLE").isPassed()).isFalse();
        assertThat(check(checks, "TIME_IN_ROLE").getMessage())
                .isEqualTo("Employee has less than 12 months in current role.");
    }

    @Test
    void allowsProbationPromotionWhenConfigurationAllowsIt() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setAllowProbationPromotion(true);
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        PromotionRequest request = validRequest();
        hasNoOtherActiveRequest(request);
        currentPositionStarted(request, LocalDate.now().minusMonths(24));
        request.getEmployee().setEmploymentStatus(EmploymentStatus.PROBATION);

        List<PromotionEligibilityCheck> checks = service.evaluate(request);

        assertThat(check(checks, "NOT_PROBATION").isPassed()).isTrue();
        assertThat(check(checks, "NOT_PROBATION").isOverrideAllowed()).isFalse();
    }

    @Test
    void blocksSubmissionWhenEmployeeHasAnotherActivePromotionRequest() {
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.empty());
        PromotionRequest request = validRequest();
        currentPositionStarted(request, LocalDate.now().minusMonths(24));
        PromotionRequest activeRequest = new PromotionRequest();
        activeRequest.setId(11L);
        activeRequest.setStatus(PromotionStatus.HR_REVIEW);
        when(requestRepository.findByEmployee_IdOrderByRequestedAtDesc(request.getEmployee().getId()))
                .thenReturn(List.of(request, activeRequest));

        List<PromotionEligibilityCheck> checks = service.evaluate(request);

        assertThat(check(checks, "NO_DUPLICATE_ACTIVE_REQUEST").isPassed()).isFalse();
        assertThat(check(checks, "NO_DUPLICATE_ACTIVE_REQUEST").getMessage())
                .isEqualTo("Employee already has an active promotion request.");
        assertThatThrownBy(() -> service.assertCanSubmit(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Promotion request has blocking eligibility failures.");
    }

    @Test
    void usesCurrentPositionEffectiveDateForTimeInRoleInsteadOfHireDate() {
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.empty());
        PromotionRequest request = validRequest();
        hasNoOtherActiveRequest(request);
        request.getEmployee().setDateOfHire(LocalDate.now().minusYears(5));
        currentPositionStarted(request, LocalDate.now().minusMonths(2));

        List<PromotionEligibilityCheck> checks = service.evaluate(request);

        assertThat(check(checks, "TIME_IN_ROLE").isPassed()).isFalse();
        assertThat(check(checks, "TIME_IN_ROLE").getMessage())
                .isEqualTo("Employee has less than 6 months in current role.");
    }

    private PromotionEligibilityCheck check(List<PromotionEligibilityCheck> checks, String code) {
        return checks.stream()
                .filter(check -> check.getCheckCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private PromotionRequest validRequest() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setStatus(RecordStatus.ACTIVE);
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setDateOfHire(LocalDate.now().minusMonths(24));

        JobStep currentRole = new JobStep();
        currentRole.setId(1L);
        JobStep proposedRole = new JobStep();
        proposedRole.setId(2L);

        PromotionRequest request = new PromotionRequest();
        request.setId(10L);
        request.setEmployee(employee);
        request.setStatus(PromotionStatus.DRAFT);
        request.setCurrentRole(currentRole);
        request.setProposedRole(proposedRole);
        request.setAppraisalScore(new BigDecimal("90.00"));
        request.setEffectiveDate(LocalDate.now().plusDays(1));
        return request;
    }

    private void hasNoOtherActiveRequest(PromotionRequest request) {
        when(requestRepository.findByEmployee_IdOrderByRequestedAtDesc(request.getEmployee().getId()))
                .thenReturn(List.of(request));
    }

    private void currentPositionStarted(PromotionRequest request, LocalDate effectiveFrom) {
        EmployeePositionHistory position = new EmployeePositionHistory();
        position.setEmployee(request.getEmployee());
        position.setJobStep(request.getCurrentRole());
        position.setEffectiveFrom(effectiveFrom);
        position.setCurrent(true);
        when(positionHistoryRepository.findCurrentPosition(request.getEmployee().getId()))
                .thenReturn(Optional.of(position));
    }
}
