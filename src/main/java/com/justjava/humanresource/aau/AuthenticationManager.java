package com.justjava.humanresource.aau;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
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
        return groups.contains("/employees");
    }

    public Object getAllAttributes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        DefaultOidcUser defaultOidcUser = (DefaultOidcUser) authentication.getPrincipal();
        return defaultOidcUser.getClaims();
    }
}
