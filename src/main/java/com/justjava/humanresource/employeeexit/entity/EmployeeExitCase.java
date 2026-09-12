package com.justjava.humanresource.employeeexit.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.employeeexit.enums.*;
import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;

@Getter @Setter @Entity
@Table(name="employee_exit_cases", indexes={@Index(name="idx_exit_employee_status",columnList="employeeId,status"),@Index(name="idx_exit_effective_date",columnList="effectiveExitDate")}, uniqueConstraints=@UniqueConstraint(name="uk_exit_number",columnNames="exitNumber"))
public class EmployeeExitCase extends BaseEntity {
 @Column(nullable=false,length=30,updatable=false) private String exitNumber;
 @Column(nullable=false) private Long companyId=1L;
 @Column(nullable=false) private Long employeeId;
 @Column(nullable=false) private Long initiatedByEmployeeId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ExitType exitType;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ExitStatus status=ExitStatus.DRAFT;
 @Column(nullable=false,length=80) private String reasonCode;
 @Column(length=3000) private String reasonDetails;
 @Column(nullable=false) private LocalDate requestDate;
 private LocalDate noticeDate;
 private Integer contractualNoticeDays;
 private Integer noticeWaivedDays;
 private LocalDate proposedLastWorkingDate;
 private LocalDate approvedLastWorkingDate;
 private LocalDate effectiveExitDate;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EmploymentStatus employmentStatusBeforeExit;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private RehireEligibility rehireEligibility=RehireEligibility.NOT_ASSESSED;
 @Column(length=1000) private String rehireEligibilityReason;
 @Column(length=100) private String workflowInstanceId;
 @Enumerated(EnumType.STRING) @Column(length=30) private ApprovalRouteType approvalRouteType=ApprovalRouteType.LINE_MANAGER;
 private Long customApprovalPathId; private Integer currentApprovalLevel; private Integer totalApprovalLevels;
 private LocalDateTime submittedAt, approvedAt, clearanceCompletedAt, settlementCompletedAt, accessRevokedAt, completedAt, cancelledAt;
 @Column(length=1000) private String cancellationReason;
}
