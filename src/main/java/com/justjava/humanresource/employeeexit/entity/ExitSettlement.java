package com.justjava.humanresource.employeeexit.entity;
import com.justjava.humanresource.core.entity.BaseEntity; import com.justjava.humanresource.employeeexit.enums.SettlementStatus;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.math.BigDecimal; import java.time.*;
@Getter @Setter @Entity @Table(name="employee_exit_settlements",uniqueConstraints=@UniqueConstraint(name="uk_exit_settlement_version",columnNames={"exitCaseId","settlementVersion"}))
public class ExitSettlement extends BaseEntity {
 @Column(nullable=false) private Long exitCaseId; @Column(nullable=false) private Integer settlementVersion;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private SettlementStatus status=SettlementStatus.DRAFT;
 @Column(nullable=false,length=3) private String currency="NGN"; private LocalDate cutoffDate;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal grossEarnings=BigDecimal.ZERO;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal totalDeductions=BigDecimal.ZERO;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal netSettlement=BigDecimal.ZERO;
 private Long calculatedByEmployeeId, approvedByEmployeeId; private LocalDateTime calculatedAt, approvedAt, paidAt;
 private Long payrollRunId, journalBatchId, paymentId; @Column(length=100) private String calculationFingerprint;
}
