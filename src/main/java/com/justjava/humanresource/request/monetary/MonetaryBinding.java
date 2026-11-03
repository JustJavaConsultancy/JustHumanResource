package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.RequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
@Getter @Setter @Entity @Table(name="request_monetary_bindings",uniqueConstraints=@UniqueConstraint(name="uk_monetary_binding",columnNames={"requestType","currency"}))
public class MonetaryBinding extends BaseEntity {

 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private RequestType requestType;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false) private Long policyId;
}
