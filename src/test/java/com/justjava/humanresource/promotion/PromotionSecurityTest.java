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
    void promotionControllersHaveCoarseMethodSecurityGuards() throws Exception {
        assertPreAuthorize(PromotionController.class, WORK_AREA);
        assertPreAuthorize(PromotionApiController.class, WORK_AREA);
        assertPreAuthorize(PromotionApiController.class.getMethod("create", com.justjava.humanresource.promotion.dto.PromotionRequestCommand.class), MANAGE);
        assertPreAuthorize(PromotionApiController.class.getMethod("dashboard"), OPERATIONAL);
        assertPreAuthorize(PromotionApiController.class.getMethod("updateConfiguration", com.justjava.humanresource.promotion.dto.PromotionConfigurationCommand.class), MANAGE);
        assertPreAuthorize(PromotionApiController.class.getMethod("applyPayrollHandoff", Long.class, com.justjava.humanresource.promotion.dto.PromotionDecisionCommand.class), OPERATIONAL);
    }

    @Test
    void sensitivePromotionServicesHaveMethodSecurityGuards() throws Exception {
        assertPreAuthorize(PromotionServiceImpl.class.getMethod("createDraft", com.justjava.humanresource.promotion.dto.PromotionRequestCommand.class), MANAGE);
        assertPreAuthorize(PromotionServiceImpl.class.getMethod("dashboard"), OPERATIONAL);
        assertPreAuthorize(PromotionConfigurationService.class.getMethod("saveActiveConfiguration", com.justjava.humanresource.promotion.dto.PromotionConfigurationCommand.class), MANAGE);
        assertPreAuthorize(PromotionPayrollHandoffService.class.getMethod("apply", Long.class, com.justjava.humanresource.promotion.dto.PromotionDecisionCommand.class), OPERATIONAL);
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
