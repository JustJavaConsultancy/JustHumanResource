package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter @Entity @Table(name="asset_request_details")
public class AssetRequestDetail extends BaseEntity {
    @Column(nullable=false, unique=true) private Long workflowRequestId;
    @Column(nullable=false) private String costCenter;
    @Column(nullable=false) private LocalDate requiredDate;
    @Column(nullable=false, length=2000) private String businessJustification;
}
