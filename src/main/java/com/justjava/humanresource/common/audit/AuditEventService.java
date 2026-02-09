package com.justjava.humanresource.common.audit;

public interface AuditEventService {

    void logEvent(
            String entityType,
            Long entityId,
            String action,
            String description,
            String performedBy
    );
}
