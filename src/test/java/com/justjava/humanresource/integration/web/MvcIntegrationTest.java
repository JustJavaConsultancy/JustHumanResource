package com.justjava.humanresource.integration.web;

import com.justjava.humanresource.HomeController;
import com.justjava.humanresource.core.accesslog.services.AccessLogService;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.config.GlobalControllerAdvice;
import com.justjava.humanresource.core.config.LoginController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {
        HomeController.class,
        LoginController.class
})
@Import(GlobalControllerAdvice.class)
@AutoConfigureMockMvc(addFilters = false)
class MvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authenticationManager;
    @MockBean
    private AccessLogService accessLogService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void home_shouldRedirectToEmployeeDashboard_whenEmployeeRoleIsPresent() throws Exception {
        when(authenticationManager.isEmployee()).thenReturn(true);

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/employee/dashboard*"));
    }

    @Test
    void home_shouldRedirectToFinanceDashboard_whenFinancialOfficerRoleIsPresent() throws Exception {
        when(authenticationManager.isEmployee()).thenReturn(false);
        when(authenticationManager.isFinancialOfficer()).thenReturn(true);

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/finance/dashboard*"));
    }

    @Test
    void home_shouldRedirectToAdminUsers_whenAdminRoleIsPresent() throws Exception {
        when(authenticationManager.isEmployee()).thenReturn(false);
        when(authenticationManager.isFinancialOfficer()).thenReturn(false);
        when(authenticationManager.isAdmin()).thenReturn(true);

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/users*"));
    }

    @Test
    void home_shouldRedirectToDepartments_whenHrRoleIsPresent() throws Exception {
        when(authenticationManager.isEmployee()).thenReturn(false);
        when(authenticationManager.isFinancialOfficer()).thenReturn(false);
        when(authenticationManager.isAdmin()).thenReturn(false);
        when(authenticationManager.isHumanResource()).thenReturn(true);

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/departments*"));
    }

    @Test
    void home_shouldRenderWelcomePageWithAdviceAttributes_whenNoRoleMatches() throws Exception {
        when(authenticationManager.isEmployee()).thenReturn(false);
        when(authenticationManager.isFinancialOfficer()).thenReturn(false);
        when(authenticationManager.isAdmin()).thenReturn(false);
        when(authenticationManager.isHumanResource()).thenReturn(false);
        when(authenticationManager.getAllAttributes()).thenReturn(Map.of("name", "Integration User"));
        when(authenticationManager.get(anyString())).thenReturn("Integration User");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("title", "Welcome to JustJava HR"))
                .andExpect(model().attribute("subTitle", "Streamline your HR processes with ease"))
                .andExpect(model().attribute("currentPath", "/"))
                .andExpect(model().attribute("userName", "Integration User"));
    }

    @Test
    void login_shouldRenderLoginView() throws Exception {
        when(authenticationManager.get(anyString())).thenReturn("Integration User");

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void globalAdvice_shouldHandleUnhandledException() throws Exception {
        when(authenticationManager.get(anyString())).thenReturn("Integration User");

        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/500"))
                .andExpect(model().attributeExists("errorMessage"));
    }

}
