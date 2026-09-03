package com.justjava.humanresource.employeeexit.entity;
import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.employeeexit.enums.*;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;
@Getter @Setter @Entity @Table(name="employee_exit_clearance_items",uniqueConstraints=@UniqueConstraint(name="uk_exit_clearance_type",columnNames={"exitCaseId","clearanceType"}))
public class ExitClearanceItem extends BaseEntity {
 @Column(nullable=false) private Long exitCaseId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private ClearanceType clearanceType;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ClearanceStatus status=ClearanceStatus.PENDING;
 private Long ownerEmployeeId; @Column(length=80) private String ownerGroup; private LocalDate dueDate;
 private Long completedByEmployeeId; private LocalDateTime completedAt;
 @Column(length=2000) private String comments; @Column(length=200) private String evidenceReference;
 @Column(length=100) private String flowableTaskId;
}
