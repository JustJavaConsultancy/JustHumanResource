package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="file_request_details")
public class FileRequestDetail extends BaseEntity {
    @Column(nullable=false, unique=true) private Long workflowRequestId;
    @Column(nullable=false) private String fileCategory;
    @Column(nullable=false) private String confidentialityLevel;
    @Column(nullable=false) private String requestedAccessType;
    @Column(nullable=false) private boolean retentionRequired;
    @Column(nullable=false, length=2000) private String purpose;
}
