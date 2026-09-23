package com.justjava.humanresource.kpi.security;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.entity.KpiAppraisalLine;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.kpi.repositories.KpiAppraisalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service("kpiAuthorization")
@RequiredArgsConstructor
public class KpiAuthorizationService {

    private final KpiAppraisalLineRepository lineRepository;
    private final EmployeeAppraisalRepository appraisalRepository;

    private static final Set<String> KPI_MANAGER_GROUPS = Set.of(
            "admin",
            "humanresource",
            "restrictedhr",
            "jobhr"
    );

    public boolean canManageKpi(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return groups(authentication).stream().anyMatch(KPI_MANAGER_GROUPS::contains);
    }

    public boolean canUpdateSelfLine(Long lineId, Authentication authentication) {
        if (canManageKpi(authentication)) {
            return true;
        }
        KpiAppraisalLine line = lineRepository.findById(lineId).orElse(null);
        if (line == null || line.getAppraisal() == null || line.getAppraisal().getEmployee() == null) {
            return false;
        }
        Employee employee = line.getAppraisal().getEmployee();
        String subject = claim(authentication, "sub");
        String email = currentEmail(authentication);
        return matches(employee.getKeycloakUserId(), subject) || matches(employee.getEmail(), email);
    }

    public boolean canUpdateManagerLine(Long lineId, Authentication authentication) {
        return canManageKpi(authentication);
    }

    public boolean canViewAppraisal(Long appraisalId, Authentication authentication) {
        if (canManageKpi(authentication)) {
            return true;
        }
        EmployeeAppraisal appraisal = appraisalRepository.findById(appraisalId).orElse(null);
        if (appraisal == null || appraisal.getEmployee() == null) {
            return false;
        }
        return isCurrentEmployee(appraisal.getEmployee(), authentication);
    }

    public boolean canManageAppraisalLines(Long appraisalId, Authentication authentication) {
        return canManageKpi(authentication);
    }

    public boolean canReadAppraisalLines(Long appraisalId, Authentication authentication) {
        return canViewAppraisal(appraisalId, authentication);
    }

    private String currentEmail(Authentication authentication) {
        String preferredUsername = claim(authentication, "preferred_username");
        String email = claim(authentication, "email");
        return preferredUsername != null ? preferredUsername : email;
    }

    private boolean isCurrentEmployee(Employee employee, Authentication authentication) {
        String subject = claim(authentication, "sub");
        String email = currentEmail(authentication);
        return matches(employee.getKeycloakUserId(), subject) || matches(employee.getEmail(), email);
    }

    private String claim(Authentication authentication, String key) {
        if (authentication == null || !(authentication.getPrincipal() instanceof DefaultOidcUser oidcUser)) {
            return null;
        }
        Object value = oidcUser.getClaims().get(key);
        return value == null ? null : value.toString();
    }

    private boolean matches(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private Set<String> groups(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof DefaultOidcUser oidcUser)) {
            return Set.of();
        }
        Object groupsClaim = oidcUser.getClaims().get("groups");
        if (!(groupsClaim instanceof Collection<?> groups)) {
            return Set.of();
        }
        return groups.stream()
                .map(String::valueOf)
                .map(this::normalize)
                .filter(group -> !group.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalize(String group) {
        String normalized = group.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
