package com.justjava.humanresource.aau;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Component("coreAuthenticationManager")
public class AuthenticationManager {
    public Object get(String fieldName){
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        DefaultOidcUser defaultOidcUser = (DefaultOidcUser) authentication.getPrincipal();
//        System.out.println(" The token here =="+defaultOidcUser.getClaims());
        return defaultOidcUser.getClaims().get(fieldName);
    }

    public boolean isEmployee() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) {
            return false;
        }
        return groups.contains("employees");
    }

    public boolean isAssetManager() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) {
            return false;
        }
        return groups.contains("assetManager");
    }

    public boolean isAuditor() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) {
            return false;
        }
        return groups.contains("auditor");
    }

    public boolean isDepartmentHead() {
        List<String> groups = (List<String>) this.get("groups");
        if (groups == null) {
            return false;
        }
        return groups.contains("departmentHead");
    }

    public Object getAllAttributes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        DefaultOidcUser defaultOidcUser = (DefaultOidcUser) authentication.getPrincipal();
        return defaultOidcUser.getClaims();
    }
}