package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @Entity
@Table(name="grouped_approval_levels", uniqueConstraints=@UniqueConstraint(name="uk_group_level_order", columnNames={"cycleId", "ordinal"}))
public class GroupedApprovalLevel extends BaseEntity {

    @Column(nullable=false) private Long cycleId;
    @Column(nullable=false) private Integer ordinal;
    @Column(nullable=false, length=100) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private ApprovalPolicy policy;
    @Column(nullable=false, length=30) private String state = "NOT_STARTED";
    private LocalDateTime activatedAt;
    private LocalDateTime resolvedAt;
    private Long resolvedByAssignmentId;
}
