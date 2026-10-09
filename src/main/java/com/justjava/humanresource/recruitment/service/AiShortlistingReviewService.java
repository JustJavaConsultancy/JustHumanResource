package com.justjava.humanresource.recruitment.service;

import com.justjava.humanresource.recruitment.dto.ShortlistingReviewCommand;
import com.justjava.humanresource.recruitment.entity.AiShortlistingReviewDecision;
import com.justjava.humanresource.recruitment.entity.AiShortlistingRun;
import com.justjava.humanresource.recruitment.enums.ShortlistingReviewDecision;
import com.justjava.humanresource.recruitment.repository.AiShortlistingReviewDecisionRepository;
import com.justjava.humanresource.recruitment.repository.AiShortlistingRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiShortlistingReviewService {

    private final AiShortlistingRunRepository runRepository;
    private final AiShortlistingReviewDecisionRepository decisionRepository;

    @Transactional(readOnly = true)
    public Optional<AiShortlistingReviewDecision> latestDecision(Long applicationId) {
        return decisionRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    @Transactional(readOnly = true)
    public List<AiShortlistingReviewDecision> decisions(Long applicationId) {
        return decisionRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    @Transactional
    public AiShortlistingReviewDecision record(Long applicationId, ShortlistingReviewCommand command, Long actorEmployeeId) {
        if (command == null || command.getRunId() == null) {
            throw new IllegalArgumentException("Shortlisting run is required.");
        }
        AiShortlistingRun run = runRepository.findById(command.getRunId())
                .orElseThrow(() -> new IllegalArgumentException("Shortlisting run not found."));
        if (!run.getApplicationId().equals(applicationId)) {
            throw new IllegalArgumentException("Shortlisting run does not belong to this application.");
        }
        ShortlistingReviewDecision reviewDecision = command.getDecision() == null
                ? ShortlistingReviewDecision.NO_DECISION
                : command.getDecision();
        if (!command.isAiRecommendationAccepted()
                && (command.getOverrideReason() == null || command.getOverrideReason().isBlank())) {
            throw new IllegalArgumentException("Override reason is required when the AI recommendation is not accepted.");
        }

        AiShortlistingReviewDecision decision = new AiShortlistingReviewDecision();
        decision.setApplicationId(applicationId);
        decision.setAiShortlistingRunId(run.getId());
        decision.setDecision(reviewDecision);
        decision.setDecidedByEmployeeId(actorEmployeeId);
        decision.setDecidedAt(LocalDateTime.now());
        decision.setComment(truncate(command.getComment(), 2000));
        decision.setAiRecommendationAccepted(command.isAiRecommendationAccepted());
        decision.setOverrideReason(truncate(command.getOverrideReason(), 2000));
        return decisionRepository.save(decision);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
