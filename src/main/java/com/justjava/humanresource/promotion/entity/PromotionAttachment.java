package com.justjava.humanresource.promotion.entity;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "hr_promotion_attachment",
        indexes = @Index(name = "idx_promotion_attachment_request", columnList = "promotion_request_id")
)
public class PromotionAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "promotion_request_id")
    private PromotionRequest promotionRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private PromotionAttachmentType documentType = PromotionAttachmentType.OTHER;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false, length = 1000)
    private String storageReference;

    private long fileSize;

    @Column(length = 120)
    private String generatedTemplateKey;

    @Column(length = 160)
    private String generatedTemplateName;

    @Column(length = 40)
    private String generatedTemplateVersion;

    private Long documentLibraryDocumentId;

    private LocalDateTime documentLibrarySyncedAt;

    @Column(length = 1000)
    private String documentLibrarySyncError;

    @ManyToOne
    private Employee uploadedBy;

    private LocalDateTime uploadedAt;

    private boolean required;
}
