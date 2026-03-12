package com.justjava.humanresource.core.audit.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.core.audit.entity.AuditLog;
import com.justjava.humanresource.core.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper mapper;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action,
                    String entityName,
                    String entityId,
                    Object oldValue,
                    Object newValue){

        try{

            AuditLog log = new AuditLog();

            log.setUsername(getCurrentUser());
            log.setAction(action);
            log.setEntityName(entityName);
            log.setEntityId(entityId);

            if(oldValue != null)
                log.setOldValue(mapper.writeValueAsString(oldValue));

            if(newValue != null)
                log.setNewValue(mapper.writeValueAsString(newValue));

            repository.save(log);

        }catch(Exception ignored){}
    }

    private String getCurrentUser(){
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        return auth != null ? auth.getName() : "system";
    }
}