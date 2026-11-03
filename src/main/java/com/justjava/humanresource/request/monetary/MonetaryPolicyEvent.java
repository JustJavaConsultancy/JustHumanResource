package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.RequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @Entity @Table(name="request_monetary_policy_events")
public class MonetaryPolicyEvent extends BaseEntity {
 private Long policyId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private RequestType requestType;
 @Column(nullable=false,length=40) private String action;
 @Column(nullable=false) private Long actorId;
}
