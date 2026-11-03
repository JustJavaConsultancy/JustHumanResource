package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.RequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
@Getter @Setter @Entity @Table(name="request_monetary_policies",uniqueConstraints=@UniqueConstraint(name="uk_monetary_policy_revision",columnNames={"familyKey","revision"}))
public class MonetaryPolicy extends BaseEntity {

 @Column(nullable=false,length=36) private String familyKey;
 @Column(nullable=false) private Integer revision;
 @Column(nullable=false,length=100) private String name;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private RequestType requestType;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false,length=20) private String state="DRAFT";
 @Column(nullable=false) private boolean enabled=true;
 private Long publishedBy;
 private LocalDateTime publishedAt;
}
