package com.justjava.humanresource.core.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthenticationManager {
    public Object get(String fieldName){
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        if (!(authentication.getPrincipal() instanceof DefaultOidcUser defaultOidcUser)) return null;
//        System.out.println(" The token here =="+defaultOidcUser.getClaims());
        return defaultOidcUser.getClaims().get(fieldName);
    }

    public boolean isEmployee() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) {
            return false;
        }
        return groups.contains("/employees");
    }
    public boolean isFinancialOfficer() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) {
            return false;
        }
        return groups.contains("/financialOfficers");
    }
    public boolean isAdmin() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) {
            return false;
        }
        return groups.contains("/admin");
    }
    public boolean isHumanResource() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) {
            return false;
        }
        return groups.contains("/humanResource");
    }
    public boolean isJobHR() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) {
            return false;
        }
        return groups.contains("/jobHR");
    }
    public boolean isRestrictedHr() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) return false;
        return groups.contains("/restrictedHr");
    }
    public boolean isHiringManager() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) return false;
        return groups.contains("/hiringManager");
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