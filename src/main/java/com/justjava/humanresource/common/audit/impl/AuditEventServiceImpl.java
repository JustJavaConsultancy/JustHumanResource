package com.justjava.humanresource.common.audit.impl;

import com.justjava.humanresource.common.audit.AuditEvent;
import com.justjava.humanresource.common.audit.AuditEventRepository;
import com.justjava.humanresource.common.audit.AuditEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditEventServiceImpl implements AuditEventService {

    private final AuditEventRepository repository;


    @Override
    public void logEvent(String entityType, Long entityId, String action, String description, String performedBy) {
        AuditEvent event = new AuditEvent();
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setAction(action);
        event.setDescription(description);
        event.setPerformedBy(performedBy);

        repository.save(event);
    }
}
