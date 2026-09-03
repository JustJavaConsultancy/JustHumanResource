package com.justjava.humanresource.employeeexit.entity;
import com.justjava.humanresource.core.entity.BaseEntity; import com.justjava.humanresource.employeeexit.enums.SettlementLineType;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.math.BigDecimal;
@Getter @Setter @Entity @Table(name="employee_exit_settlement_lines")
public class ExitSettlementLine extends BaseEntity {
 @Column(nullable=false) private Long settlementId; @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private SettlementLineType lineType;
 @Column(length=60) private String componentCode; @Column(nullable=false) private String description;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal amount; @Column(nullable=false) private boolean earning;
 @Column(nullable=false) private boolean taxable; @Column(nullable=false) private boolean manualAdjustment;
 @Column(length=1000) private String adjustmentReason; @Column(length=80) private String source;
}
