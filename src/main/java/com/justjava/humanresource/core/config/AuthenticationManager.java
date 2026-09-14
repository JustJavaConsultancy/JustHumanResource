package com.justjava.humanresource.core.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthenticationManager {

    public Object get(String fieldName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        if (!(authentication.getPrincipal() instanceof DefaultOidcUser defaultOidcUser)) return null;
//        System.out.println(" The token here =="+defaultOidcUser.getClaims());
        return defaultOidcUser.getClaims().get(fieldName);
    }

    private Set<String> normalizedGroups() {
        Object groupsClaim = this.get("groups");
        if (!(groupsClaim instanceof Collection<?> groups)) {
            return Set.of();
        }
        return groups.stream()
                .map(String::valueOf)
                .map(this::normalizeGroup)
                .collect(Collectors.toSet());
    }

    private String normalizeGroup(String group) {
        String normalized = group.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public boolean isEmployee() {
        return normalizedGroups().contains("employees");
    }
    public boolean isFinancialOfficer() {
        return normalizedGroups().contains("financialofficers");
    }
    public boolean isAdmin() {
        return normalizedGroups().contains("admin");
    }
    public boolean isHumanResource() {
        return normalizedGroups().contains("humanresource");
    }
    public boolean isJobHR() {
        return normalizedGroups().contains("jobhr");
    }
    public boolean isRestrictedHr() {
        return normalizedGroups().contains("restrictedhr");
    }
    public boolean isHiringManager() {
        return normalizedGroups().contains("hiringmanager");
    }

    public Object getAllAttributes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof DefaultOidcUser defaultOidcUser)) return null;
        return defaultOidcUser.getClaims();
    }

    public String getCurrentUserEmail() {
        Object email = this.get("preferred_username");
        if (email == null) {
            email = this.get("email");
        }
        return email != null ? email.toString() : null;
    }

    public String getCurrentUserName() {
        Object name = this.get("name");
        if (name == null) {
            String given = (String) this.get("given_name");
            String family = (String) this.get("family_name");
            if (given != null && family != null) {
                name = given + " " + family;
            } else if (given != null) {
                name = given;
            }
        }
        return name != null ? name.toString() : getCurrentUserEmail();
    }
}