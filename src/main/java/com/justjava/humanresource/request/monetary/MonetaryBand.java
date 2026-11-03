package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.RequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
@Getter @Setter @Entity @Table(name="request_monetary_bands",uniqueConstraints=@UniqueConstraint(name="uk_monetary_band_order",columnNames={"policyId","ordinal"}))
public class MonetaryBand extends BaseEntity {

 @Column(nullable=false) private Long policyId;
 @Column(nullable=false) private Integer ordinal;
 @Column(nullable=false,precision=19,scale=2) @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) private BigDecimal lowerBound;
 @Column(precision=19,scale=2) @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) private BigDecimal upperBound;
 @Column(nullable=false,length=20) private String target;
 private Long groupedVersionId;
}
