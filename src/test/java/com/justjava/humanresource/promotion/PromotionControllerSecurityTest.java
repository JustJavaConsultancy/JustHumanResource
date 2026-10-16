package com.justjava.humanresource.promotion;

import com.justjava.humanresource.core.accesslog.services.AccessLogService;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.promotion.service.PromotionSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.ViewResolver;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest(controllers = PromotionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PromotionControllerSecurityTest.MethodSecurityTestConfig.class)
class PromotionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "promotionSecurity")
    private PromotionSecurity promotionSecurity;

    @MockitoBean
    private AccessLogService accessLogService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @WithMockUser
    void roleOwnedPagesAreForbiddenWhenCapabilityDeniesAccess() throws Exception {
        mockMvc.perform(get("/admin/promotions")).andExpect(status().isForbidden());
        mockMvc.perform(get("/hr/promotions")).andExpect(status().isForbidden());
        for (String path : new String[]{"requests", "approvals", "reports", "payroll-handoffs", "settings", "requests/new", "requests/42", "requests/42/edit"}) {
            mockMvc.perform(get("/hr/promotions/" + path)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/finance/promotions")).andExpect(status().isForbidden());
        mockMvc.perform(get("/executive/promotions")).andExpect(status().isForbidden());
        mockMvc.perform(get("/manager/promotions")).andExpect(status().isForbidden());
        mockMvc.perform(get("/employee/promotions")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void roleOwnedPagesAreAllowedOnlyByMatchingCapability() throws Exception {
        when(promotionSecurity.canAccessAdminPromotionPage()).thenReturn(true);
        mockMvc.perform(get("/admin/promotions").requestAttr("userName", "Test User"))
                .andExpect(status().isOk())
                .andExpect(view().name("promotion/manage"));

        when(promotionSecurity.canAccessHrPromotionPage()).thenReturn(true);
        mockMvc.perform(get("/hr/promotions").requestAttr("userName", "Test User"))
                .andExpect(status().isOk())
                .andExpect(view().name("promotion/hr"));

        when(promotionSecurity.canAccessFinancePromotionPage()).thenReturn(true);
        mockMvc.perform(get("/finance/promotions").requestAttr("userName", "Test User"))
                .andExpect(status().isOk())
                .andExpect(view().name("promotion/finance"));

        when(promotionSecurity.canAccessExecutivePromotionPage()).thenReturn(true);
        mockMvc.perform(get("/executive/promotions").requestAttr("userName", "Test User"))
                .andExpect(status().isOk())
                .andExpect(view().name("promotion/executive"));

        when(promotionSecurity.canAccessManagerPromotionPage()).thenReturn(true);
        mockMvc.perform(get("/manager/promotions").requestAttr("userName", "Test User"))
                .andExpect(status().isOk())
                .andExpect(view().name("promotion/manager"));

        when(promotionSecurity.canAccessEmployeePromotionPage()).thenReturn(true);
        mockMvc.perform(get("/employee/promotions").requestAttr("userName", "Test User"))
                .andExpect(status().isOk())
                .andExpect(view().name("promotion/employee"));
    }

    @Test
    @WithMockUser
    void hrSubpagesResolveTheirOwnSections() throws Exception {
        when(promotionSecurity.canAccessHrPromotionPage()).thenReturn(true);
        for (String section : new String[]{"requests", "approvals", "reports", "payroll-handoffs", "settings", "requests/new", "requests/42", "requests/42/edit"}) {
            mockMvc.perform(get("/hr/promotions/" + section))
                    .andExpect(status().isOk()).andExpect(view().name("promotion/hr"));
        }
        mockMvc.perform(get("/hr/promotions/requests"))
                .andExpect(content().string(containsString("id=\"requestPanel\"")))
                .andExpect(content().string(not(containsString("id=\"configurationPanel\""))))
                .andExpect(content().string(not(containsString("id=\"reportsPanel\""))))
                .andExpect(content().string(not(containsString("id=\"taskPanel\""))));
        mockMvc.perform(get("/hr/promotions").param("section", "configuration"))
                .andExpect(redirectedUrl("/hr/promotions/settings"));
    }

    @Test
    @WithMockUser
    void legacyPromotionRouteIsForbiddenWhenWorkAreaCapabilityDeniesAccess() throws Exception {
        mockMvc.perform(get("/promotion"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void legacyPromotionRouteRedirectsWhenWorkAreaCapabilityAllowsAccess() throws Exception {
        when(promotionSecurity.canAccessPromotionWorkArea()).thenReturn(true);
        when(promotionSecurity.canAccessManagerPromotionPage()).thenReturn(true);

        mockMvc.perform(get("/promotion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/manager/promotions?section=dashboard*"));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
        @Bean
        @Primary
        ViewResolver noOpViewResolver() {
            return (viewName, locale) -> (model, request, response) -> {
            };
        }
    }
}
