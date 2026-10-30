package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @Entity
@Table(name="grouped_approval_events")
public class GroupedApprovalEvent extends BaseEntity {

    @Column(nullable=false) private Long requestId;
    @Column(nullable=false) private Long cycleId;
    private Long assignmentId;
    private Long actorId;
    @Column(nullable=false, length=50) private String eventType;
    @Column(length=2000) private String description;
}
