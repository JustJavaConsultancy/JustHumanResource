package com.justjava.humanresource.promotion.repository;

import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionApprovalStepRepository extends JpaRepository<PromotionApprovalStep, Long> {

    List<PromotionApprovalStep> findByPromotionRequest_IdOrderBySequenceNumberAsc(Long requestId);

    Optional<PromotionApprovalStep> findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
            Long requestId,
            PromotionApprovalDecision decision
    );

    List<PromotionApprovalStep> findByDecisionAndStage(PromotionApprovalDecision decision, PromotionApprovalStage stage);

    List<PromotionApprovalStep> findByDecisionAndApprover_Id(PromotionApprovalDecision decision, Long approverId);

    List<PromotionApprovalStep> findByDecisionOrderByAssignedAtAsc(PromotionApprovalDecision decision);

    void deleteByPromotionRequest_Id(Long requestId);
}
