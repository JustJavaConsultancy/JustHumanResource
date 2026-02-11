package com.justjava.humanresource.common.audit;

import com.justjava.humanresource.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_events")
public class AuditEvent extends BaseEntity {

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private String action;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private String performedBy;

    public Long getEntityId() {
        return entityId;
    }
}
