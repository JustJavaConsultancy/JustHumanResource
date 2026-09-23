package com.justjava.humanresource.kpi.controller;

import com.justjava.humanresource.core.accesslog.services.AccessLogService;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.kpi.entity.KpiAppraisalLine;
import com.justjava.humanresource.kpi.security.KpiAuthorizationService;
import com.justjava.humanresource.kpi.service.KpiAppraisalLineService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KpiAppraisalLineController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(KpiAppraisalLineControllerSecurityTest.MethodSecurityTestConfig.class)
class KpiAppraisalLineControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KpiAppraisalLineService lineService;

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
    void generateLinesIsForbiddenWhenAuthorizationServiceDeniesAccess() throws Exception {
        when(authorizationService.canManageAppraisalLines(anyLong(), any())).thenReturn(false);

        mockMvc.perform(post("/api/appraisals/{appraisalId}/lines/generate", 20L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void readLinesIsAllowedWhenAuthorizationServiceAllowsAccess() throws Exception {
        when(authorizationService.canReadAppraisalLines(anyLong(), any())).thenReturn(true);
        when(lineService.getLines(anyLong())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/appraisals/{appraisalId}/lines", 20L))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void selfLineUpdateIsForbiddenWhenAuthorizationServiceDeniesAccess() throws Exception {
        when(authorizationService.canUpdateSelfLine(anyLong(), any())).thenReturn(false);

        mockMvc.perform(put("/api/appraisals/lines/{lineId}/self", 10L)
                        .contentType("application/json")
                        .content("{\"selfScore\":80}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void selfLineUpdateIsAllowedWhenAuthorizationServiceAllowsAccess() throws Exception {
        when(authorizationService.canUpdateSelfLine(anyLong(), any())).thenReturn(true);
        KpiAppraisalLine line = new KpiAppraisalLine();
        line.setId(10L);
        when(lineService.updateSelfInput(anyLong(), any())).thenReturn(line);

        mockMvc.perform(put("/api/appraisals/lines/{lineId}/self", 10L)
                        .contentType("application/json")
                        .content("{\"selfScore\":80}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void managerLineUpdateIsForbiddenWhenAuthorizationServiceDeniesAccess() throws Exception {
        when(authorizationService.canUpdateManagerLine(anyLong(), any())).thenReturn(false);

        mockMvc.perform(put("/api/appraisals/lines/{lineId}/manager", 10L)
                        .contentType("application/json")
                        .content("{\"managerScore\":90}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void managerLineUpdateIsAllowedWhenAuthorizationServiceAllowsAccess() throws Exception {
        when(authorizationService.canUpdateManagerLine(anyLong(), any())).thenReturn(true);
        KpiAppraisalLine line = new KpiAppraisalLine();
        line.setId(10L);
        when(lineService.updateManagerInput(anyLong(), any())).thenReturn(line);

        mockMvc.perform(put("/api/appraisals/lines/{lineId}/manager", 10L)
                        .contentType("application/json")
                        .content("{\"managerScore\":90}"))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
