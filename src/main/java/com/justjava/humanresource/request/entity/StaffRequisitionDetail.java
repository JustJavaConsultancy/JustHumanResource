package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @Entity @Table(name="staff_requisition_details")
public class StaffRequisitionDetail extends BaseEntity {
    @Column(nullable=false, unique=true) private Long workflowRequestId;
    @Column(nullable=false) private String jobTitle;
    @Column(nullable=false) private Long departmentId;
    private Long jobGradeId;
    @Column(nullable=false) private Integer numberOfPositions;
    @Column(nullable=false) private String employmentType;
    private LocalDate targetStartDate;
    @Column(nullable=false) private boolean budgeted;
    @Column(precision=19, scale=2) private BigDecimal estimatedMonthlyCost;
    @Column(nullable=false, length=2000) private String reasonForHire;
    private Long replacementEmployeeId;
}
