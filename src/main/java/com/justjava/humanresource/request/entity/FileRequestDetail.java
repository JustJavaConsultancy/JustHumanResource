package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.FileAccessType;
import com.justjava.humanresource.request.enums.FileCategory;
import com.justjava.humanresource.request.enums.FileConfidentialityLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="file_request_details")
public class FileRequestDetail extends BaseEntity {
    @Column(nullable=false, unique=true) private Long workflowRequestId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private FileCategory fileCategory;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private FileConfidentialityLevel confidentialityLevel;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private FileAccessType requestedAccessType;
    @Column(nullable=false) private boolean retentionRequired;
    @Column(nullable=false, length=2000) private String purpose;
}
