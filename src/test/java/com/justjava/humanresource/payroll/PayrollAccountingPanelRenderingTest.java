package com.justjava.humanresource.payroll;

import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationAlert;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertType;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollAccountingPanelService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = com.justjava.humanresource.payroll.workflow.PayrollProofDatabaseGuard.class)
@TestPropertySource(properties = {
        "integration.payroll-accounting.enabled=true",
        "integration.payroll-accounting.company-id=990000000001",
        "app.scheduling.enabled=false",
        "spring.rabbitmq.host=127.0.0.1",
        "spring.rabbitmq.port=1",
        "spring.rabbitmq.username=test-disabled",
        "spring.rabbitmq.password=test-disabled",
        "spring.rabbitmq.dynamic=false"
})
class PayrollAccountingPanelRenderingTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IntegrationPayrollProperties properties;

    @MockitoBean
    private EmployeeOnboardingService employeeOnboardingService;

    @MockitoBean
    private PayrollPeriodService payrollPeriodService;

    @MockitoBean
    private PayrollSetupService payrollSetupService;

    @MockitoBean
    private SetupService setupService;

    @MockitoBean
    private PayrollAccountingPanelService panelService;

    @BeforeEach
    void provideEmptyPayrollDataAndPanel() {
        properties.setEnabled(true);
        when(employeeOnboardingService.getAllOnboardings()).thenReturn(List.of());
        when(payrollPeriodService.getCurrentPeriod(1L)).thenReturn(null);
        when(payrollSetupService.getActiveAllowances()).thenReturn(List.of());
        when(payrollSetupService.getActiveDeductions()).thenReturn(List.of());
        when(payrollSetupService.getActiveTaxReliefs()).thenReturn(List.of());
        when(payrollSetupService.getAllPayGroups()).thenReturn(List.of());

        IntegrationAlert alert = new IntegrationAlert();
        alert.setId(123L);
        alert.setType(IntegrationAlertType.PAYROLL_CHANGED_AFTER_SEND);
        alert.setDedupeKey("CHANGED:123");
        alert.setMessage("Payroll changed after journal publication.");
        alert.setStatus(IntegrationAlertStatus.OPEN);
        alert.setCreatedAt(LocalDateTime.of(2026, 11, 7, 10, 0));
        when(panelService.panel()).thenReturn(new PayrollAccountingPanelService.PanelData(
                List.of(new PayrollAccountingPanelService.SnapshotView(
                        "PAY-2026-10-57", "Sent",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                        LocalDateTime.of(2026, 11, 7, 9, 0))),
                List.of(alert)));
    }

    @Test
    void rendersSnapshotAlertAndAcknowledgeButtonForFinanceOfficer() throws Exception {
        properties.setEnabled(true);
        mockMvc.perform(get("/payroll").with(financeOfficerLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PAY-2026-10-57")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Payroll changed after journal publication.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Acknowledge")));
    }

    @Test
    void rendersSnapshotAndAlertWithoutAcknowledgeButtonForOtherUser() throws Exception {
        properties.setEnabled(true);
        mockMvc.perform(get("/payroll").with(otherUserLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PAY-2026-10-57")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Payroll changed after journal publication.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Acknowledge"))));
    }

    @Test
    void omitsAccountingPanelWhenIntegrationIsDisabled() throws Exception {
        properties.setEnabled(false);
        mockMvc.perform(get("/payroll").with(financeOfficerLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Accounting integration"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Acknowledge"))));
    }

    private static OidcLoginRequestPostProcessor financeOfficerLogin() {
        return oidcLogin().idToken(token -> token.claim("groups", List.of("financialofficers")));
    }

    private static OidcLoginRequestPostProcessor otherUserLogin() {
        return oidcLogin().idToken(token -> token.claim("groups", List.of("employees")));
    }
}
