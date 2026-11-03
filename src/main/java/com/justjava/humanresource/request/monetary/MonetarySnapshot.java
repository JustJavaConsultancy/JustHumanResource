package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.RequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
@Getter @Setter @Entity @Table(name="request_monetary_snapshots",uniqueConstraints=@UniqueConstraint(name="uk_monetary_request_submission",columnNames={"requestId","submissionNo"}))
public class MonetarySnapshot extends BaseEntity {

 @Column(nullable=false) private Long requestId;
 @Column(nullable=false) private Integer submissionNo;
 @Column(nullable=false) private Long policyId;
 @Column(nullable=false) private Long bandId;
 @Column(nullable=false,length=140) private String policyName;
 @Column(nullable=false,precision=19,scale=2) @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) private BigDecimal amount;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false,precision=19,scale=2) @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) private BigDecimal lowerBound;
 @Column(precision=19,scale=2) @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) private BigDecimal upperBound;
 @Column(nullable=false,length=20) private String target;
 private Long groupedVersionId;
 @Column(nullable=false,columnDefinition="TEXT") private String resolvedRoute;
 @Column(length=100) private String workflowInstanceId;
 @Column(nullable=false) private Long submittedBy;
}
