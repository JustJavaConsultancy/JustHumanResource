package com.justjava.humanresource.recruitment.repository;

import com.justjava.humanresource.recruitment.entity.AiShortlistingReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiShortlistingReviewDecisionRepository extends JpaRepository<AiShortlistingReviewDecision, Long> {
    Optional<AiShortlistingReviewDecision> findFirstByApplicationIdOrderByCreatedAtDesc(Long applicationId);
    List<AiShortlistingReviewDecision> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
