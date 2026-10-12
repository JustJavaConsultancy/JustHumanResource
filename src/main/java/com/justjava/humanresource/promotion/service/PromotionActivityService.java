package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.entity.PromotionActivityLog;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.repository.PromotionActivityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PromotionActivityService {

    private final PromotionActivityLogRepository activityLogRepository;

    public void record(PromotionRequest request, PromotionActivityType type, Employee actor, String description) {
        PromotionActivityLog log = new PromotionActivityLog();
        log.setPromotionRequest(request);
        log.setActivityType(type);
        log.setActor(actor);
        log.setDescription(description);
        captureRequestMetadata(log);
        log.setCreatedAt(LocalDateTime.now());
        activityLogRepository.save(log);
    }

    private void captureRequestMetadata(PromotionActivityLog log) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        log.setIpAddress(trimToNull(firstHeaderValue(request, "X-Forwarded-For", request.getRemoteAddr()), 80));
        String userAgent = trimToNull(request.getHeader("User-Agent"), 500);
        log.setUserAgent(userAgent);
        log.setDeviceMetadata(resolveDeviceMetadata(userAgent));
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private String firstHeaderValue(HttpServletRequest request, String headerName, String fallback) {
        String header = trimToNull(request.getHeader(headerName), 500);
        if (header == null) {
            return trimToNull(fallback, 500);
        }
        int comma = header.indexOf(',');
        return trimToNull(comma < 0 ? header : header.substring(0, comma), 500);
    }

    private String resolveDeviceMetadata(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String lower = userAgent.toLowerCase();
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) {
            return "Mobile";
        }
        if (lower.contains("tablet") || lower.contains("ipad")) {
            return "Tablet";
        }
        return "Desktop";
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
