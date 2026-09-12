package com.justjava.humanresource.employeeexit.entity;
import com.justjava.humanresource.core.entity.BaseEntity; import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @Entity @Table(name="employee_exit_handover_items")
public class ExitHandoverItem extends BaseEntity { @Column(nullable=false) private Long exitCaseId; @Column(nullable=false) private String subject; @Column(length=2000) private String details; private Long successorEmployeeId; @Column(nullable=false) private boolean completed; private Long verifiedByEmployeeId; private LocalDateTime verifiedAt; }
