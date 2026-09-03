package com.justjava.humanresource.employeeexit.entity;
import com.justjava.humanresource.core.entity.BaseEntity; import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
@Getter @Setter @Entity @Table(name="employee_exit_activities",indexes=@Index(name="idx_exit_activity",columnList="exitCaseId,createdAt"))
public class ExitActivity extends BaseEntity { @Column(nullable=false) private Long exitCaseId; @Column(nullable=false,length=80) private String activityType; @Column(nullable=false,length=2000) private String description; private Long actorEmployeeId; @Column(length=100) private String flowableTaskId; }
