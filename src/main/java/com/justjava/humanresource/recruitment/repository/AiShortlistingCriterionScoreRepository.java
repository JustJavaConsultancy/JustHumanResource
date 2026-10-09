package com.justjava.humanresource.recruitment.repository;

import com.justjava.humanresource.recruitment.entity.AiShortlistingCriterionScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiShortlistingCriterionScoreRepository extends JpaRepository<AiShortlistingCriterionScore, Long> {
    List<AiShortlistingCriterionScore> findByRunIdOrderByIdAsc(Long runId);
    void deleteByRunId(Long runId);
}
