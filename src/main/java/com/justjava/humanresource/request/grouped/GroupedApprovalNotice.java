package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @Entity
@Table(name="grouped_approval_notices")
public class GroupedApprovalNotice extends BaseEntity {

    @Column(nullable=false) private Long requestId;
    @Column(nullable=false) private Long cycleId;
    private Long assignmentId;
    private Long employeeId;
    @Column(nullable=false, length=30) private String kind;
    @Column(nullable=false, unique=true, length=150) private String deduplicationKey;
    @Column(nullable=false) private Integer attempts = 0;
    @Column(nullable=false) private LocalDateTime nextAttemptAt = LocalDateTime.now();
    private LocalDateTime deliveredAt;
    @Column(length=500) private String lastError;
}
