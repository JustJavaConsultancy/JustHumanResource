package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @Entity
@Table(name="grouped_approval_cycles", uniqueConstraints=@UniqueConstraint(name="uk_group_request_cycle", columnNames={"requestId", "cycleNo"}))
public class GroupedApprovalCycle extends BaseEntity {

    @Column(nullable=false) private Long requestId;
    @Column(nullable=false) private Integer cycleNo;
    @Column(nullable=false) private Long pathVersionId;
    @Column(nullable=false, length=100) private String pathName;
    @Column(nullable=false, length=30) private String state = "ACTIVE";
    @Column(nullable=false) private Integer currentOrdinal = 1;
    @Column(length=100) private String processInstanceId;
    private LocalDateTime completedAt;
    @Column(length=200) private String resolutionReason;
}
