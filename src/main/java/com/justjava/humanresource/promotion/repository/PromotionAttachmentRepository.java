package com.justjava.humanresource.promotion.repository;

import com.justjava.humanresource.promotion.entity.PromotionAttachment;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionAttachmentRepository extends JpaRepository<PromotionAttachment, Long> {

    List<PromotionAttachment> findByPromotionRequest_IdOrderByUploadedAtDesc(Long requestId);

    boolean existsByPromotionRequest_IdAndDocumentType(Long requestId, PromotionAttachmentType documentType);
}
