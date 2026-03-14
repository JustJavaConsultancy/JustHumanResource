package com.justjava.humanresource.core.accesslog.repository;

import com.justjava.humanresource.core.accesslog.entity.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessLogRepository
        extends JpaRepository<AccessLog, Long> {
}