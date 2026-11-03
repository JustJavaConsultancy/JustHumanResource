package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @Entity
@Table(name="grouped_approval_assignments", uniqueConstraints=@UniqueConstraint(name="uk_group_level_employee", columnNames={"levelId", "employeeId"}))
public class GroupedApprovalAssignment extends BaseEntity {

    @Column(nullable=false) private Long requestId;
    @Column(nullable=false) private Long levelId;
    @Column(nullable=false) private Long employeeId;
    @Column(nullable=false, length=200) private String employeeName;
    @Column(nullable=false, length=30) private String state = "NOT_STARTED";
    @Column(length=100, unique=true) private String taskId;
    @Column(length=2000) private String comment;
    private LocalDateTime decidedAt;
    @Column(length=200) private String supersessionReason;
}
