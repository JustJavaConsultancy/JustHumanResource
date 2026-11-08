package com.justjava.humanresource.integration.inbound.payroll;

import com.justjava.humanresource.integration.inbound.payroll.controller.PayrollJournalIntegrationController;
import com.justjava.humanresource.integration.inbound.payroll.controller.PayrollJournalIntegrationExceptionHandler;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollJournalSnapshotService;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollJournalStatusService;

import com.justjava.humanresource.core.config.IntegrationSecurityConfig;
import com.justjava.humanresource.integration.inbound.payroll.dto.IntegrationSnapshotContract;
import com.justjava.humanresource.integration.inbound.payroll.dto.IntegrationSnapshotLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;





@WebMvcTest
@Import({
        IntegrationSecurityConfig.class,
        PayrollJournalIntegrationController.class,
        PayrollJournalIntegrationExceptionHandler.class
})
@TestPropertySource(properties = {
        "integration.payroll-accounting.enabled=true",
        "integration.security.issuer-uri=https://test-issuer.invalid/realms/hr",
        "integration.security.audience=humanresource-api"
})
class PayrollJournalIntegrationControllerTest {
    @Configuration
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PayrollJournalSnapshotService snapshots;

    @MockitoBean
    private PayrollJournalStatusService statuses;

    @Test
    void readReturnsAmsContractFieldNamesAndSafeIntegrationFields() throws Exception {
        when(snapshots.findReadable("PAY-2026-10-57")).thenReturn(
                new IntegrationSnapshotContract(
                        "JUSTHR", "PAY-2026-10-57", "ORIGINAL", "57",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                        "NGN", "Payroll 2026-10",
                        List.of(new IntegrationSnapshotLine(
                                "5000", "Salary Expense", "DEBIT",
                                new BigDecimal("100.00"), BigDecimal.ZERO, null)),
                        SnapshotStatus.PUBLISHED, "content-hash"));

        mockMvc.perform(get("/api/integrations/payroll-journals/PAY-2026-10-57")
                        .with(jwt().jwt(jwt -> jwt
                                .claim("azp", "jjeip-humanresource")
                                .claim("scope", "payroll-journal:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceSystem").value("JUSTHR"))
                .andExpect(jsonPath("$.journalReference").value("PAY-2026-10-57"))
                .andExpect(jsonPath("$.payrollPeriodId").value("57"))
                .andExpect(jsonPath("$.periodStart").value("2026-10-01"))
                .andExpect(jsonPath("$.periodEnd").value("2026-10-31"))
                .andExpect(jsonPath("$.narration").value("Payroll 2026-10"))
                .andExpect(jsonPath("$.lines[0].accountCode").value("5000"))
                .andExpect(jsonPath("$.lines[0].debitAmount").value(100.00))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.contentHash").value("content-hash"));
    }

    @Test
    void statusCallbackAcceptsScopedRequest() throws Exception {
        doNothing().when(statuses).record(
                org.mockito.ArgumentMatchers.eq("PAY-2026-10-57"),
                org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/integrations/payroll-journals/PAY-2026-10-57/status")
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"POSTED\",\"amsJournalId\":123}")
                        .with(jwt().jwt(jwt -> jwt
                                .claim("azp", "jjeip-humanresource")
                                .claim("scope", "payroll-status:write"))))
                .andExpect(status().isOk());
    }

    @Test
    void malformedStatusBodyReturnsNonRetryableBadRequest() throws Exception {
        mockMvc.perform(post("/api/integrations/payroll-journals/PAY-2026-10-57/status")
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":")
                        .with(jwt().jwt(jwt -> jwt
                                .claim("azp", "jjeip-humanresource")
                                .claim("scope", "payroll-status:write"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void unsupportedStatusContentTypeReturnsNonRetryable415() throws Exception {
        mockMvc.perform(post("/api/integrations/payroll-journals/PAY-2026-10-57/status")
                        .contentType(TEXT_PLAIN)
                        .content("{\"status\":\"POSTED\"}")
                        .with(jwt().jwt(jwt -> jwt
                                .claim("azp", "jjeip-humanresource")
                                .claim("scope", "payroll-status:write"))))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void statusCallbackRejectsStatusesOutsidePostedAndRejected() throws Exception {
        mockMvc.perform(post("/api/integrations/payroll-journals/PAY-2026-10-57/status")
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"PENDING_PUBLISH\"}")
                        .with(jwt().jwt(jwt -> jwt
                                .claim("azp", "jjeip-humanresource")
                                .claim("scope", "payroll-status:write"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.retryable").value(false));
    }
}
