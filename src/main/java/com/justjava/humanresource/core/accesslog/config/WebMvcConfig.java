package com.justjava.humanresource.core.accesslog.config;

import com.justjava.humanresource.core.accesslog.interceptor.AccessLogInterceptor;
import com.justjava.humanresource.core.accesslog.interceptor.RestrictedHrInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AccessLogInterceptor interceptor;
    private final RestrictedHrInterceptor restrictedHrInterceptor;

    @Override
    public void addInterceptors(
            InterceptorRegistry registry) {

        registry.addInterceptor(interceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico"
                );
        registry.addInterceptor(restrictedHrInterceptor) // ← ADD
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/favicon.ico");
    }
}