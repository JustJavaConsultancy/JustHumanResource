package com.justjava.humanresource.core.audit.listener;

import com.justjava.humanresource.core.audit.entity.AuditLog;
import com.justjava.humanresource.core.audit.services.AuditService;
import lombok.RequiredArgsConstructor;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HibernateUpdateListener implements PostUpdateEventListener {

    private final AuditService auditService;

    @Override
    public void onPostUpdate(PostUpdateEvent event) {

        Object entity = event.getEntity();
        if (entity instanceof AuditLog) return;
        String entityName = entity.getClass().getSimpleName();
        String entityId = extractId(entity);

        Map<String,Object> oldState =
                extractState(event.getOldState(), event.getPersister().getPropertyNames());

        Map<String,Object> newState =
                extractState(event.getState(), event.getPersister().getPropertyNames());

        auditService.log(
                "UPDATE",
                entityName,
                entityId,
                oldState,
                newState
        );
    }

    private Map<String,Object> extractState(Object[] values, String[] names){

        Map<String,Object> map = new HashMap<>();

        for(int i=0;i<names.length;i++){
            map.put(names[i], values[i]);
        }

        return map;
    }

    private String extractId(Object entity){

        try{
            Method m = entity.getClass().getMethod("getId");
            Object id = m.invoke(entity);
            return id != null ? id.toString() : null;
        }catch(Exception e){
            return null;
        }
    }
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }
}