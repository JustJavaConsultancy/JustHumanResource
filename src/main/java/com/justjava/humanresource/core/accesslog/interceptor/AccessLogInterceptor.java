package com.justjava.humanresource.core.accesslog.interceptor;

import com.justjava.humanresource.core.accesslog.services.AccessLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AccessLogInterceptor implements HandlerInterceptor {

    private final AccessLogService accessLogService;

    private static final String START_TIME = "startTime";

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        request.setAttribute(
                START_TIME,
                System.currentTimeMillis()
        );

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        long start =
                (long) request.getAttribute(START_TIME);

        long duration =
                System.currentTimeMillis() - start;

        accessLogService.log(
                getUsername(),
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                duration
        );
    }

    private String getUsername() {

        Authentication auth =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (auth == null || !auth.isAuthenticated())
            return "anonymous";

        return auth.getName();
    }
}