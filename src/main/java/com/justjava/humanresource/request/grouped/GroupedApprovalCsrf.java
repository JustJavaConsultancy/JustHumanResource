package com.justjava.humanresource.request.grouped;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Legacy action URLs also require CSRF when they target the new grouped workflow. */
public final class GroupedApprovalCsrf {
    private GroupedApprovalCsrf() {}
    public static void requireToken() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
            throw new AccessDeniedException("A CSRF-protected request is required for grouped approvals.");
        var request = attributes.getRequest();
        var token = CookieCsrfTokenRepository.withHttpOnlyFalse().loadToken(request);
        if (token == null || !java.security.MessageDigest.isEqual(token.getToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.util.Objects.toString(request.getHeader(token.getHeaderName()), "").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new AccessDeniedException("Refresh the page before submitting a grouped approval decision.");
    }
}
