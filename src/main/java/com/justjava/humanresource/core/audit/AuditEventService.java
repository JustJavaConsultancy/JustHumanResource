package com.justjava.humanresource.core.audit;

public interface AuditEventService {

    void logEvent(
            String entityType,
            Long entityId,
            String action,
            String description,
            String performedBy
    );
}
