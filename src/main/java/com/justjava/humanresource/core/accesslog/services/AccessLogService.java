package com.justjava.humanresource.core.accesslog.services;

import com.justjava.humanresource.core.accesslog.entity.AccessLog;
import com.justjava.humanresource.core.accesslog.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            String username,
            String method,
            String endpoint,
            int status,
            String ip,
            String userAgent,
            long duration
    ) {

        try {

            AccessLog log = new AccessLog();

            log.setUsername(username);
            log.setMethod(method);
            log.setEndpoint(endpoint);
            log.setStatus(status);
            log.setIpAddress(ip);
            log.setUserAgent(userAgent);
            log.setDurationMs(duration);

            repository.save(log);

        } catch (Exception ignored) {}
    }
}