package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.RequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
@Getter @Setter @Entity @Table(name="request_cash_advance_details")
public class CashAdvanceDetail extends BaseEntity {

 @Column(nullable=false,unique=true) private Long requestId;
 @Column(nullable=false,precision=19,scale=2) @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) private BigDecimal amount;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false,length=2000) private String purpose;
 @Column(nullable=false,length=100) private String costCenter;
 @Column(nullable=false) private LocalDate requiredDate;
 @Column(nullable=false) private LocalDate expenseStartDate;
 @Column(nullable=false) private LocalDate expenseEndDate;
 @Column(nullable=false) private LocalDate settlementDueDate;
}
