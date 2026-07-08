package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.AttachmentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @Entity @Table(name="workflow_request_attachments")
public class WorkflowRequestAttachment extends BaseEntity {
    @Column(nullable=false) private Long workflowRequestId;
    @Column(nullable=false) private String originalFilename;
    @Column(nullable=false, unique=true) private String storedFilename;
    @Column(nullable=false, length=1000) private String storagePath;
    @Column(nullable=false) private String contentType;
    @Column(nullable=false) private Long fileSize;
    @Column(nullable=false) private Long uploadedByEmployeeId;
    @Column(nullable=false) private LocalDateTime uploadedAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private AttachmentType attachmentType;
    @Column(length=500) private String description;
}
