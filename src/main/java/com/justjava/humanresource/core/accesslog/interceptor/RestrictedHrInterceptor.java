package com.justjava.humanresource.core.accesslog.interceptor;

import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class RestrictedHrInterceptor implements HandlerInterceptor {

    private final AuthenticationManager authenticationManager;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (modelAndView != null) {
            try {
                modelAndView.addObject("isRestrictedHr", authenticationManager.isRestrictedHr());
            } catch (Exception ignored) {}
        }
    }
}
