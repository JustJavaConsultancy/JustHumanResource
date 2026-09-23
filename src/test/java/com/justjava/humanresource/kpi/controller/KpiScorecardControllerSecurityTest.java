package com.justjava.humanresource.kpi.controller;

import com.justjava.humanresource.core.accesslog.services.AccessLogService;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.kpi.dto.KpiScorecardTemplateCommand;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplate;
import com.justjava.humanresource.kpi.security.KpiAuthorizationService;
import com.justjava.humanresource.kpi.service.KpiScorecardImportService;
import com.justjava.humanresource.kpi.service.KpiScorecardTemplateService;
import com.justjava.humanresource.kpi.service.KpiScoringRubricService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KpiScorecardController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(KpiScorecardControllerSecurityTest.MethodSecurityTestConfig.class)
class KpiScorecardControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KpiScorecardTemplateService templateService;

    @MockBean
    private KpiScoringRubricService rubricService;

    @MockBean
    private KpiScorecardImportService importService;

    @MockBean(name = "kpiAuthorization")
    private KpiAuthorizationService authorizationService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private AccessLogService accessLogService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @Test
    @WithMockUser
    void createTemplateIsForbiddenWhenAuthorizationServiceDeniesAccess() throws Exception {
        when(authorizationService.canManageKpi(any())).thenReturn(false);

        mockMvc.perform(post("/api/kpi/scorecards/templates")
                        .contentType("application/json")
                        .content("{\"name\":\"Template\",\"active\":false,\"items\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createTemplateIsAllowedWhenAuthorizationServiceAllowsAccess() throws Exception {
        when(authorizationService.canManageKpi(any())).thenReturn(true);
        KpiScorecardTemplate template = KpiScorecardTemplate.builder()
                .name("Template")
                .active(false)
                .build();
        template.setId(10L);
        when(templateService.create(any(KpiScorecardTemplateCommand.class))).thenReturn(template);

        mockMvc.perform(post("/api/kpi/scorecards/templates")
                        .contentType("application/json")
                        .content("{\"name\":\"Template\",\"active\":false,\"items\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void archiveTemplateIsForbiddenWhenAuthorizationServiceDeniesAccess() throws Exception {
        when(authorizationService.canManageKpi(any())).thenReturn(false);

        mockMvc.perform(post("/api/kpi/scorecards/templates/{id}/archive", 10L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void archiveTemplateIsAllowedWhenAuthorizationServiceAllowsAccess() throws Exception {
        when(authorizationService.canManageKpi(any())).thenReturn(true);
        KpiScorecardTemplate template = KpiScorecardTemplate.builder()
                .name("Template")
                .active(false)
                .build();
        template.setId(10L);
        when(templateService.archive(anyLong(), any(Boolean.class))).thenReturn(template);

        mockMvc.perform(post("/api/kpi/scorecards/templates/{id}/archive", 10L))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
