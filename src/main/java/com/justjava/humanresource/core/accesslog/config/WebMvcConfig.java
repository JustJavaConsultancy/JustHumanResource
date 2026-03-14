package com.justjava.humanresource.core.accesslog.config;

import com.justjava.humanresource.core.accesslog.interceptor.AccessLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AccessLogInterceptor interceptor;

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
    }
}