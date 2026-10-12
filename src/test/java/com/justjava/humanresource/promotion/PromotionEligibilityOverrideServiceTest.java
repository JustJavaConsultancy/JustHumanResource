package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.promotion.dto.PromotionEligibilityOverrideCommand;
import com.justjava.humanresource.promotion.entity.PromotionEligibilityCheck;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionEligibilitySeverity;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionEligibilityOverrideServiceTest {

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
    }

    @Test
    void overridesFailedWarningEligibilityCheck() {
        PromotionRequest request = request();
        PromotionEligibilityCheck check = failedWarning(request);
        Employee actor = actor();
        PromotionEligibilityOverrideCommand command = command("Recent performance improvement");
        when(checkRepository.findById(check.getId())).thenReturn(Optional.of(check));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(actor);
        when(checkRepository.save(check)).thenReturn(check);

        PromotionEligibilityCheck response = service.override(request.getId(), check.getId(), command);

        assertThat(response.isOverridden()).isTrue();
        assertThat(response.getOverriddenBy()).isEqualTo(actor);
        assertThat(response.getOverrideReason()).isEqualTo("Recent performance improvement");
        assertThat(response.getOverriddenAt()).isNotNull();
        verify(authorizationService).requireHrOrAdmin();
        verify(activityService).record(request, PromotionActivityType.ELIGIBILITY_OVERRIDDEN, actor,
                "Eligibility check overridden: Minimum appraisal score met.");
    }

    @Test
    void rejectsOverrideForPassedCheck() {
        PromotionEligibilityCheck check = failedWarning(request());
        check.setPassed(true);
        when(checkRepository.findById(check.getId())).thenReturn(Optional.of(check));

        assertThatThrownBy(() -> service.override(check.getPromotionRequest().getId(), check.getId(), command("Not needed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Passed eligibility checks do not need an override.");

        verify(checkRepository, never()).save(check);
    }

    @Test
    void rejectsOverrideForNonOverrideableCheck() {
        PromotionEligibilityCheck check = failedWarning(request());
        check.setOverrideAllowed(false);
        check.setSeverity(PromotionEligibilitySeverity.BLOCKER);
        when(checkRepository.findById(check.getId())).thenReturn(Optional.of(check));

        assertThatThrownBy(() -> service.override(check.getPromotionRequest().getId(), check.getId(), command("Force")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This eligibility check cannot be overridden.");

        verify(checkRepository, never()).save(check);
    }

    @Test
    void rejectsOverrideForCheckBelongingToDifferentRequest() {
        PromotionEligibilityCheck check = failedWarning(request());
        when(checkRepository.findById(check.getId())).thenReturn(Optional.of(check));

        assertThatThrownBy(() -> service.override(999L, check.getId(), command("Wrong request")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Promotion eligibility check does not belong to this request.");
    }

    private PromotionRequest request() {
        PromotionRequest request = new PromotionRequest();
        request.setId(20L);
        return request;
    }

    private PromotionEligibilityCheck failedWarning(PromotionRequest request) {
        PromotionEligibilityCheck check = new PromotionEligibilityCheck();
        check.setId(30L);
        check.setPromotionRequest(request);
        check.setCheckCode("APPRAISAL_SCORE");
        check.setCheckName("Minimum appraisal score met");
        check.setPassed(false);
        check.setSeverity(PromotionEligibilitySeverity.WARNING);
        check.setOverrideAllowed(true);
        return check;
    }

    private Employee actor() {
        Employee actor = new Employee();
        actor.setId(40L);
        actor.setFirstName("HR");
        actor.setLastName("User");
        return actor;
    }

    private PromotionEligibilityOverrideCommand command(String reason) {
        PromotionEligibilityOverrideCommand command = new PromotionEligibilityOverrideCommand();
        command.setReason(reason);
        return command;
    }
}
