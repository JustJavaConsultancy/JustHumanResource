package com.justjava.humanresource.core.audit.config;

import com.justjava.humanresource.core.audit.listener.HibernateDeleteListener;
import com.justjava.humanresource.core.audit.listener.HibernateInsertListener;
import com.justjava.humanresource.core.audit.listener.HibernateUpdateListener;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class HibernateAuditConfiguration {

    private final EntityManagerFactory entityManagerFactory;
    private final HibernateInsertListener insertListener;
    private final HibernateUpdateListener updateListener;
    private final HibernateDeleteListener deleteListener;

    @PostConstruct
    public void registerListeners() {

        SessionFactoryImpl sessionFactory =
                entityManagerFactory.unwrap(SessionFactoryImpl.class);

        EventListenerRegistry registry =
                sessionFactory.getServiceRegistry()
                        .getService(EventListenerRegistry.class);

        registry.appendListeners(
                EventType.POST_INSERT,
                insertListener);

        registry.appendListeners(
                EventType.POST_UPDATE,
                updateListener);

        registry.appendListeners(
                EventType.POST_DELETE,
                deleteListener);
    }
}