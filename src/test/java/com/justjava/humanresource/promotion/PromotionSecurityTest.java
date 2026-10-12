package com.justjava.humanresource.promotion;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.config.Oauth2SecurityConfig;
import com.justjava.humanresource.promotion.service.PromotionAttachmentService;
import com.justjava.humanresource.promotion.service.PromotionConfigurationService;
import com.justjava.humanresource.promotion.service.PromotionPayrollHandoffService;
import com.justjava.humanresource.promotion.service.PromotionSecurity;
import com.justjava.humanresource.promotion.service.PromotionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionSecurityTest {

    private static final String WORK_AREA = "@promotionSecurity.canAccessPromotionWorkArea()";
    private static final String MANAGE = "@promotionSecurity.canManagePromotions()";
    private static final String OPERATIONAL = "@promotionSecurity.canViewOperationalPromotions()";
    private static final String CONFIGURE = "@promotionSecurity.canConfigurePromotions()";
    private static final String IMPLEMENT = "@promotionSecurity.canImplementPromotions()";
    private static final String PAYROLL_HANDOFF = "@promotionSecurity.canManagePromotionPayrollHandoffs()";

    @Test
    void methodSecurityIsEnabledForPromotionAnnotations() {
        assertNotNull(Oauth2SecurityConfig.class.getAnnotation(EnableMethodSecurity.class));
    }

    @Test
    void promotionRoleExpressionsMapExpectedGroups() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        PromotionSecurity security = new PromotionSecurity(authenticationManager);

        when(authenticationManager.isHumanResource()).thenReturn(true);

        assertTrue(security.canAccessPromotionWorkArea());
        assertTrue(security.canManagePromotions());
        assertTrue(security.canViewOperationalPromotions());
        assertTrue(security.canViewPromotionSalaryData());
    }

    @Test
    void excludedHrSpecialistRolesDoNotInheritPromotionAccess() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        PromotionSecurity security = new PromotionSecurity(authenticationManager);

        when(authenticationManager.isHumanResource()).thenReturn(true);
        when(authenticationManager.isRestrictedHr()).thenReturn(true);

        assertEquals(false, security.canAccessPromotionWorkArea());
        assertEquals(false, security.canManagePromotions());
        assertEquals(false, security.canAccessHrPromotionPage());
    }

    @Test
    void departmentHeadIsPromotionManager() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        PromotionSecurity security = new PromotionSecurity(authenticationManager);

        when(authenticationManager.isDepartmentHead()).thenReturn(true);

        assertTrue(security.canAccessPromotionWorkArea());
        assertTrue(security.canAccessManagerPromotionPage());
    }

    @Test
    void promotionControllersHaveMethodSecurityGuards() throws Exception {
        assertPreAuthorize(PromotionApiController.class, WORK_AREA);
        assertPreAuthorize(PromotionController.class.getMethod("adminPromotions", String.class, org.springframework.ui.Model.class), "@promotionSecurity.canAccessAdminPromotionPage()");
        assertPreAuthorize(PromotionController.class.getMethod("hrPromotions", String.class, org.springframework.ui.Model.class), "@promotionSecurity.canAccessHrPromotionPage()");
        assertPreAuthorize(PromotionController.class.getMethod("financePromotions", String.class, org.springframework.ui.Model.class), "@promotionSecurity.canAccessFinancePromotionPage()");
        assertPreAuthorize(PromotionController.class.getMethod("executivePromotions", String.class, org.springframework.ui.Model.class), "@promotionSecurity.canAccessExecutivePromotionPage()");
        assertPreAuthorize(PromotionController.class.getMethod("managerPromotions", String.class, org.springframework.ui.Model.class), "@promotionSecurity.canAccessManagerPromotionPage()");
        assertPreAuthorize(PromotionController.class.getMethod("employeePromotions", String.class, org.springframework.ui.Model.class), "@promotionSecurity.canAccessEmployeePromotionPage()");
        assertPreAuthorize(PromotionApiController.class.getMethod("create", com.justjava.humanresource.promotion.dto.PromotionRequestCommand.class), MANAGE);
        assertPreAuthorize(PromotionApiController.class.getMethod("dashboard"), OPERATIONAL);
        assertPreAuthorize(PromotionApiController.class.getMethod("updateConfiguration", com.justjava.humanresource.promotion.dto.PromotionConfigurationCommand.class), CONFIGURE);
        assertPreAuthorize(PromotionApiController.class.getMethod("applyPayrollHandoff", Long.class, com.justjava.humanresource.promotion.dto.PromotionDecisionCommand.class), PAYROLL_HANDOFF);
        assertPreAuthorize(PromotionApiController.class.getMethod("implement", Long.class, com.justjava.humanresource.promotion.dto.PromotionImplementationCommand.class), IMPLEMENT);
    }

    @Test
    void sensitivePromotionServicesHaveMethodSecurityGuards() throws Exception {
        assertPreAuthorize(PromotionServiceImpl.class.getMethod("createDraft", com.justjava.humanresource.promotion.dto.PromotionRequestCommand.class), MANAGE);
        assertPreAuthorize(PromotionServiceImpl.class.getMethod("dashboard"), OPERATIONAL);
        assertPreAuthorize(PromotionServiceImpl.class.getMethod("implement", Long.class, com.justjava.humanresource.promotion.dto.PromotionImplementationCommand.class), IMPLEMENT);
        assertPreAuthorize(PromotionConfigurationService.class.getMethod("saveActiveConfiguration", com.justjava.humanresource.promotion.dto.PromotionConfigurationCommand.class), CONFIGURE);
        assertPreAuthorize(PromotionPayrollHandoffService.class.getMethod("apply", Long.class, com.justjava.humanresource.promotion.dto.PromotionDecisionCommand.class), PAYROLL_HANDOFF);
        assertPreAuthorize(PromotionAttachmentService.class.getMethod("upload", Long.class, org.springframework.web.multipart.MultipartFile.class, com.justjava.humanresource.promotion.enums.PromotionAttachmentType.class, boolean.class), MANAGE);
    }

    private void assertPreAuthorize(Class<?> type, String expectedExpression) {
        PreAuthorize annotation = type.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, () -> type.getSimpleName() + " should be guarded by @PreAuthorize");
        assertEquals(expectedExpression, annotation.value());
    }

    private void assertPreAuthorize(Method method, String expectedExpression) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, () -> method.getName() + " should be guarded by @PreAuthorize");
        assertEquals(expectedExpression, annotation.value());
    }
}
