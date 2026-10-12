package com.justjava.humanresource.promotion;

import com.justjava.humanresource.promotion.service.PromotionSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionControllerTest {

    private final PromotionSecurity promotionSecurity = mock(PromotionSecurity.class);
    private final PromotionController controller = new PromotionController(promotionSecurity);

    @Test
    void roleOwnedPromotionRoutesRenderSharedPageWithRoleContext() {
        assertPage("promotion/manage", "dashboard", "admin");
        assertPage("promotion/manage", "dashboard", "hr");
        assertPage("promotion/finance", "approvals", "finance");
        assertPage("promotion/executive", "approvals", "executive");
        assertPage("promotion/manager", "approvals", "manager");
        assertPage("promotion/employee", "requests", "employee");
    }

    @Test
    void legacyPromotionRoutesRedirectToFirstAllowedRoleOwnedPage() {
        when(promotionSecurity.canAccessHrPromotionPage()).thenReturn(true);

        assertEquals("redirect:/hr/promotions?section=dashboard", controller.getPromotions(new ConcurrentModel()));
        assertEquals("redirect:/hr/promotions?section=requests", controller.requests(new ConcurrentModel()));
        assertEquals("redirect:/hr/promotions?section=approvals", controller.approvals(new ConcurrentModel()));
        assertEquals("redirect:/hr/promotions?section=reports", controller.reports(new ConcurrentModel()));
        assertEquals("redirect:/hr/promotions?section=configuration", controller.configuration(new ConcurrentModel()));
        assertEquals("redirect:/hr/promotions?section=payrollHandoffs", controller.payrollHandoffs(new ConcurrentModel()));
    }

    @Test
    void legacyPromotionRoutesRedirectByPriority() {
        when(promotionSecurity.canAccessAdminPromotionPage()).thenReturn(true);
        when(promotionSecurity.canAccessHrPromotionPage()).thenReturn(true);

        assertEquals("redirect:/admin/promotions?section=dashboard", controller.getPromotions(new ConcurrentModel()));
    }

    @Test
    void legacyPromotionRoutesRedirectForEachAllowedRole() {
        assertLegacyRedirect("finance", "redirect:/finance/promotions?section=dashboard");
        assertLegacyRedirect("executive", "redirect:/executive/promotions?section=dashboard");
        assertLegacyRedirect("manager", "redirect:/manager/promotions?section=dashboard");
        assertLegacyRedirect("employee", "redirect:/employee/promotions?section=dashboard");
    }

    @Test
    void legacyPromotionRoutesFallbackWhenNoPromotionRoleIsAllowed() {
        assertEquals("redirect:/", controller.getPromotions(new ConcurrentModel()));
    }

    private void assertPage(String expectedView, String expectedSection, String expectedRole) {
        ConcurrentModel model = new ConcurrentModel();
        String actualView = switch (expectedRole) {
            case "admin" -> controller.adminPromotions(null, model);
            case "hr" -> controller.hrPromotions(null, model);
            case "finance" -> controller.financePromotions(null, model);
            case "executive" -> controller.executivePromotions(null, model);
            case "manager" -> controller.managerPromotions(null, model);
            case "employee" -> controller.employeePromotions(null, model);
            default -> throw new IllegalArgumentException(expectedRole);
        };
        assertEquals(expectedView, actualView);
        assertEquals("Promotion Management", model.getAttribute("title"));
        assertEquals(expectedSection, model.getAttribute("initialSection"));
        assertEquals(expectedRole, model.getAttribute("promotionRole"));
    }

    private void assertLegacyRedirect(String role, String expectedRedirect) {
        PromotionSecurity roleSecurity = mock(PromotionSecurity.class);
        PromotionController roleController = new PromotionController(roleSecurity);
        switch (role) {
            case "finance" -> when(roleSecurity.canAccessFinancePromotionPage()).thenReturn(true);
            case "executive" -> when(roleSecurity.canAccessExecutivePromotionPage()).thenReturn(true);
            case "manager" -> when(roleSecurity.canAccessManagerPromotionPage()).thenReturn(true);
            case "employee" -> when(roleSecurity.canAccessEmployeePromotionPage()).thenReturn(true);
            default -> throw new IllegalArgumentException(role);
        }

        assertEquals(expectedRedirect, roleController.getPromotions(new ConcurrentModel()));
    }

    @Test
    void roleOwnedRoutesPreserveAllowedRequestedSection() {
        ConcurrentModel model = new ConcurrentModel();

        assertEquals("promotion/manage", controller.hrPromotions("configuration", model));
        assertEquals("configuration", model.getAttribute("initialSection"));
    }

    @Test
    void roleOwnedRoutesFallBackFromDisallowedRequestedSection() {
        ConcurrentModel model = new ConcurrentModel();

        assertEquals("promotion/employee", controller.employeePromotions("configuration", model));
        assertEquals("requests", model.getAttribute("initialSection"));
    }
}
