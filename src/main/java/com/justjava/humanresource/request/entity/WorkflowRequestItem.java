package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter @Entity @Table(name="workflow_request_items")
public class WorkflowRequestItem extends BaseEntity {
    @Column(nullable=false) private Long workflowRequestId;
    private String externalSourceSystem;
    private String externalAssetId;
    private String externalAssetCode;
    @Column(nullable=false, length=200) private String itemName;
    @Column(length=1000) private String description;
    private String category;
    @Column(nullable=false, precision=19, scale=4) private BigDecimal quantity;
    private String unitOfMeasure;
    @Column(precision=19, scale=2) private BigDecimal estimatedUnitCost;
    @Column(precision=19, scale=2) private BigDecimal estimatedTotalCost;
    @Column(length=3) private String currency;
    private String vendorName;
    @Column(length=1000) private String remarks;
}
