package com.justjava.humanresource.kpi.security;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.entity.KpiAppraisalLine;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.kpi.repositories.KpiAppraisalLineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KpiAuthorizationServiceTest {

    @Test
    void hrGroupCanManageKpiAndManagerLines() {
        KpiAppraisalLineRepository repository = mock(KpiAppraisalLineRepository.class);
        EmployeeAppraisalRepository appraisalRepository = mock(EmployeeAppraisalRepository.class);
        KpiAuthorizationService service = new KpiAuthorizationService(repository, appraisalRepository);

        assertTrue(service.canManageKpi(auth("hr@example.com", "user-1", List.of("/humanresource"))));
        assertTrue(service.canUpdateManagerLine(10L, auth("hr@example.com", "user-1", List.of("/humanresource"))));
    }

    @Test
    void employeeCanUpdateOwnSelfLineByEmail() {
        KpiAppraisalLineRepository repository = mock(KpiAppraisalLineRepository.class);
        EmployeeAppraisalRepository appraisalRepository = mock(EmployeeAppraisalRepository.class);
        KpiAuthorizationService service = new KpiAuthorizationService(repository, appraisalRepository);
        when(repository.findById(10L)).thenReturn(Optional.of(line("employee@example.com", "keycloak-1")));

        assertTrue(service.canUpdateSelfLine(10L, auth("employee@example.com", "other-sub", List.of("/employees"))));
    }

    @Test
    void employeeCannotUpdateAnotherEmployeeSelfLine() {
        KpiAppraisalLineRepository repository = mock(KpiAppraisalLineRepository.class);
        EmployeeAppraisalRepository appraisalRepository = mock(EmployeeAppraisalRepository.class);
        KpiAuthorizationService service = new KpiAuthorizationService(repository, appraisalRepository);
        when(repository.findById(10L)).thenReturn(Optional.of(line("owner@example.com", "keycloak-1")));

        assertFalse(service.canUpdateSelfLine(10L, auth("other@example.com", "other-sub", List.of("/employees"))));
    }

    @Test
    void employeeCanViewOwnAppraisal() {
        KpiAppraisalLineRepository repository = mock(KpiAppraisalLineRepository.class);
        EmployeeAppraisalRepository appraisalRepository = mock(EmployeeAppraisalRepository.class);
        KpiAuthorizationService service = new KpiAuthorizationService(repository, appraisalRepository);
        when(appraisalRepository.findById(20L)).thenReturn(Optional.of(appraisal("employee@example.com", "keycloak-1")));

        assertTrue(service.canViewAppraisal(20L, auth("employee@example.com", "other-sub", List.of("/employees"))));
    }

    private TestingAuthenticationToken auth(String email, String subject, List<String> groups) {
        Map<String, Object> claims = Map.of(
                "sub", subject,
                "email", email,
                "preferred_username", email,
                "groups", groups
        );
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
        DefaultOidcUser user = new DefaultOidcUser(List.of(), token);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null);
        authentication.setAuthenticated(true);
        return authentication;
    }

    private KpiAppraisalLine line(String employeeEmail, String keycloakUserId) {
        KpiAppraisalLine line = new KpiAppraisalLine();
        line.setAppraisal(appraisal(employeeEmail, keycloakUserId));
        return line;
    }

    private EmployeeAppraisal appraisal(String employeeEmail, String keycloakUserId) {
        Employee employee = new Employee();
        employee.setEmail(employeeEmail);
        employee.setKeycloakUserId(keycloakUserId);

        EmployeeAppraisal appraisal = new EmployeeAppraisal();
        appraisal.setEmployee(employee);
        return appraisal;
    }
}
