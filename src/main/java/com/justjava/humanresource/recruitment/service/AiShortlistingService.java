package com.justjava.humanresource.recruitment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.orgStructure.repositories.CompanyRepository;
import com.justjava.humanresource.recruitment.dto.ShortlistingCriterionScoreResult;
import com.justjava.humanresource.recruitment.dto.ShortlistingRecommendationResult;
import com.justjava.humanresource.recruitment.entity.AiShortlistingCriterionScore;
import com.justjava.humanresource.recruitment.entity.AiShortlistingRun;
import com.justjava.humanresource.recruitment.entity.CandidateDocument;
import com.justjava.humanresource.recruitment.entity.JobApplication;
import com.justjava.humanresource.recruitment.entity.JobOpening;
import com.justjava.humanresource.recruitment.enums.AiShortlistingRecommendation;
import com.justjava.humanresource.recruitment.enums.AiShortlistingRunStatus;
import com.justjava.humanresource.recruitment.enums.DocumentExtractionStatus;
import com.justjava.humanresource.recruitment.enums.ShortlistingCriterionType;
import com.justjava.humanresource.recruitment.repository.AiShortlistingCriterionScoreRepository;
import com.justjava.humanresource.recruitment.repository.AiShortlistingRunRepository;
import com.justjava.humanresource.recruitment.repository.CandidateDocumentRepository;
import com.justjava.humanresource.recruitment.repository.JobApplicationRepository;
import com.justjava.humanresource.recruitment.repository.JobOpeningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiShortlistingService {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\r\\n;,]+");
    private static final Pattern WORD_PATTERN = Pattern.compile("[^a-z0-9+#.]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "and", "or", "the", "a", "an", "to", "of", "for", "with", "in", "on", "at", "by", "is",
            "are", "be", "as", "from", "this", "that", "will", "must", "should", "candidate", "role",
            "work", "experience", "knowledge", "ability", "strong", "good", "excellent", "proven"
    );

    private final JobApplicationRepository applicationRepository;
    private final JobOpeningRepository openingRepository;
    private final CandidateDocumentRepository documentRepository;
    private final AiShortlistingRunRepository runRepository;
    private final AiShortlistingCriterionScoreRepository scoreRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public boolean isEnabledForApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .flatMap(application -> companyRepository.findById(application.getCompanyId()))
                .map(company -> company.isRecruitmentAiShortlistingEnabled())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<AiShortlistingRun> latestRun(Long applicationId) {
        return runRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    @Transactional(readOnly = true)
    public List<AiShortlistingCriterionScore> scoresForRun(Long runId) {
        return scoreRepository.findByRunIdOrderByIdAsc(runId);
    }

    @Transactional
    public AiShortlistingRun triggerShortlisting(Long applicationId, Long actorEmployeeId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        JobOpening opening = openingRepository.findById(application.getJobOpeningId())
                .orElseThrow(() -> new IllegalArgumentException("Job opening not found."));

        AiShortlistingRun run = new AiShortlistingRun();
        run.setApplicationId(application.getId());
        run.setCandidateId(application.getCandidateId());
        run.setJobOpeningId(opening.getId());
        run.setTriggeredByEmployeeId(actorEmployeeId);
        run.setTriggeredAt(LocalDateTime.now());
        run.setStatus(AiShortlistingRunStatus.RUNNING);
        run.setAgentName("local-evidence-shortlister");
        run.setAgentVersion("1.0");
        run.setModelProvider("LOCAL");
        run.setModelName("deterministic-keyword-evidence-v1");
        run = runRepository.saveAndFlush(run);

        try {
            List<CandidateDocument> documents = documentRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
            List<CandidateDocument> extracted = documents.stream()
                    .filter(document -> document.getExtractedTextStatus() == DocumentExtractionStatus.EXTRACTED)
                    .filter(document -> document.getExtractedText() != null && !document.getExtractedText().isBlank())
                    .toList();
            if (extracted.isEmpty()) {
                run.setStatus(AiShortlistingRunStatus.NEEDS_HUMAN_REVIEW);
                run.setOverallRecommendation(AiShortlistingRecommendation.INSUFFICIENT_INFORMATION);
                run.setOverallScore(0);
                run.setSummary("No extracted candidate document text is available for AI shortlisting.");
                run.setMissingRequirements("Upload a readable resume or supporting document.");
                run.setInputDocumentIds(documentIds(documents));
                run.setCompletedAt(LocalDateTime.now());
                return runRepository.save(run);
            }

            String documentText = joinDocumentText(extracted);
            ShortlistingRecommendationResult result = evaluate(opening, documentText);
            applyResult(run, opening, extracted, result);
            AiShortlistingRun saved = runRepository.save(run);
            scoreRepository.deleteByRunId(saved.getId());
            for (ShortlistingCriterionScoreResult item : result.getCriterionScores()) {
                scoreRepository.save(toEntity(saved.getId(), item));
            }
            return saved;
        } catch (Exception ex) {
            run.setStatus(AiShortlistingRunStatus.FAILED);
            run.setErrorMessage(truncate(ex.getMessage(), 2000));
            run.setCompletedAt(LocalDateTime.now());
            return runRepository.save(run);
        }
    }

    @Transactional
    public AiShortlistingRun retryShortlisting(Long runId, Long actorEmployeeId) {
        AiShortlistingRun existing = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Shortlisting run not found."));
        return triggerShortlisting(existing.getApplicationId(), actorEmployeeId);
    }

    private ShortlistingRecommendationResult evaluate(JobOpening opening, String documentText) {
        List<Criterion> criteria = criteria(opening);
        String normalizedDocument = normalize(documentText);
        List<ShortlistingCriterionScoreResult> scores = new ArrayList<>();
        int earned = 0;
        int possible = 0;

        for (Criterion criterion : criteria) {
            int max = criterion.required() ? 10 : 5;
            int score = criterionScore(criterion.text(), normalizedDocument, max);
            earned += score;
            possible += max;

            ShortlistingCriterionScoreResult result = new ShortlistingCriterionScoreResult();
            result.setCriterionName(truncate(criterion.text(), 200));
            result.setCriterionType(criterion.type());
            result.setRequired(criterion.required());
            result.setScore(score);
            result.setMaxScore(max);
            if (score > 0) {
                result.setEvidence("Matched job-related terms: " + String.join(", ", matchedTerms(criterion.text(), normalizedDocument)));
            } else {
                result.setMissingEvidence("No clear evidence found for this criterion in the extracted documents.");
            }
            result.setComment(score >= Math.ceil(max * 0.7) ? "Strong evidence" : score > 0 ? "Partial evidence" : "Evidence not found");
            scores.add(result);
        }

        int overall = possible == 0 ? 0 : Math.round((earned * 100f) / possible);
        ShortlistingRecommendationResult result = new ShortlistingRecommendationResult();
        result.setOverallScore(overall);
        result.setCriterionScores(scores);
        result.setOverallRecommendation(recommendation(overall, scores));
        result.setSummary(summary(result.getOverallRecommendation(), overall));
        result.setStrengths(scores.stream()
                .filter(score -> score.getScore() != null && score.getMaxScore() != null && score.getScore() >= Math.ceil(score.getMaxScore() * 0.7))
                .map(score -> score.getCriterionName() + " (" + score.getScore() + "/" + score.getMaxScore() + ")")
                .limit(5)
                .toList());
        result.setConcerns(scores.stream()
                .filter(score -> score.isRequired() && (score.getScore() == null || score.getScore() == 0))
                .map(ShortlistingCriterionScoreResult::getCriterionName)
                .limit(5)
                .toList());
        result.setMissingRequirements(result.getConcerns());
        result.setFairnessWarnings(List.of("Review is based only on job-related text evidence from uploaded documents. HR must make the final decision."));
        result.setRawOutput(toJson(result));
        return result;
    }

    private List<Criterion> criteria(JobOpening opening) {
        List<Criterion> criteria = new ArrayList<>();
        addCriteria(criteria, opening.getRequirements(), true);
        addCriteria(criteria, opening.getResponsibilities(), false);
        addCriteria(criteria, opening.getPreferredQualifications(), false);
        if (criteria.isEmpty() && opening.getDescription() != null) {
            addCriteria(criteria, opening.getDescription(), true);
        }
        return criteria.stream()
                .filter(criterion -> criterion.text().length() >= 3)
                .sorted(Comparator.comparing(Criterion::required).reversed())
                .limit(20)
                .toList();
    }

    private void addCriteria(List<Criterion> criteria, String source, boolean required) {
        if (source == null || source.isBlank()) {
            return;
        }
        for (String part : SPLIT_PATTERN.split(source)) {
            String text = part.trim().replaceFirst("^[-*\\d.)\\s]+", "").trim();
            if (!text.isBlank()) {
                criteria.add(new Criterion(text, classify(text), required));
            }
        }
    }

    private ShortlistingCriterionType classify(String criterion) {
        String text = criterion.toLowerCase(Locale.ROOT);
        if (text.contains("degree") || text.contains("bsc") || text.contains("msc") || text.contains("education")) {
            return ShortlistingCriterionType.EDUCATION;
        }
        if (text.contains("certif")) {
            return ShortlistingCriterionType.CERTIFICATION;
        }
        if (text.contains("years") || text.contains("experience")) {
            return ShortlistingCriterionType.EXPERIENCE;
        }
        if (text.contains("location") || text.contains("remote") || text.contains("onsite") || text.contains("hybrid")) {
            return ShortlistingCriterionType.LOCATION;
        }
        return ShortlistingCriterionType.SKILL;
    }

    private int criterionScore(String criterion, String normalizedDocument, int max) {
        List<String> terms = terms(criterion);
        if (terms.isEmpty()) {
            return 0;
        }
        long matched = terms.stream().filter(normalizedDocument::contains).count();
        return Math.min(max, Math.round((matched * (float) max) / terms.size()));
    }

    private List<String> matchedTerms(String criterion, String normalizedDocument) {
        return terms(criterion).stream()
                .filter(normalizedDocument::contains)
                .limit(8)
                .toList();
    }

    private List<String> terms(String value) {
        String normalized = WORD_PATTERN.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return new ArrayList<>(new LinkedHashSet<>(List.of(normalized.split("\\s+")).stream()
                .map(String::trim)
                .filter(term -> term.length() > 2)
                .filter(term -> !STOP_WORDS.contains(term))
                .toList()));
    }

    private AiShortlistingRecommendation recommendation(int overall, List<ShortlistingCriterionScoreResult> scores) {
        boolean missingRequired = scores.stream().anyMatch(score -> score.isRequired() && (score.getScore() == null || score.getScore() == 0));
        if (overall == 0) {
            return AiShortlistingRecommendation.INSUFFICIENT_INFORMATION;
        }
        if (overall >= 70 && !missingRequired) {
            return AiShortlistingRecommendation.RECOMMENDED;
        }
        if (overall >= 40) {
            return AiShortlistingRecommendation.MAYBE;
        }
        return AiShortlistingRecommendation.NOT_RECOMMENDED;
    }

    private String summary(AiShortlistingRecommendation recommendation, int score) {
        return switch (recommendation) {
            case RECOMMENDED -> "Candidate documents show strong evidence against the published job criteria. Overall score: " + score + "/100.";
            case MAYBE -> "Candidate documents show partial evidence and should be reviewed by HR. Overall score: " + score + "/100.";
            case NOT_RECOMMENDED -> "Candidate documents show limited evidence against the published job criteria. Overall score: " + score + "/100.";
            case INSUFFICIENT_INFORMATION -> "Candidate documents do not provide enough readable evidence for shortlisting.";
        };
    }

    private void applyResult(AiShortlistingRun run,
                             JobOpening opening,
                             List<CandidateDocument> documents,
                             ShortlistingRecommendationResult result) {
        run.setStatus(result.getOverallRecommendation() == AiShortlistingRecommendation.INSUFFICIENT_INFORMATION
                ? AiShortlistingRunStatus.NEEDS_HUMAN_REVIEW
                : AiShortlistingRunStatus.COMPLETED);
        run.setCompletedAt(LocalDateTime.now());
        run.setInputDocumentIds(documentIds(documents));
        run.setJobCriteriaSnapshot(criteriaSnapshot(opening));
        run.setOverallRecommendation(result.getOverallRecommendation());
        run.setOverallScore(result.getOverallScore());
        run.setSummary(result.getSummary());
        run.setStrengths(join(result.getStrengths()));
        run.setConcerns(join(result.getConcerns()));
        run.setMissingRequirements(join(result.getMissingRequirements()));
        run.setFairnessWarnings(join(result.getFairnessWarnings()));
        run.setRawAgentOutput(result.getRawOutput());
        run.setErrorMessage(null);
    }

    private AiShortlistingCriterionScore toEntity(Long runId, ShortlistingCriterionScoreResult result) {
        AiShortlistingCriterionScore score = new AiShortlistingCriterionScore();
        score.setRunId(runId);
        score.setCriterionName(result.getCriterionName());
        score.setCriterionType(result.getCriterionType() == null ? ShortlistingCriterionType.OTHER : result.getCriterionType());
        score.setRequired(result.isRequired());
        score.setScore(result.getScore());
        score.setMaxScore(result.getMaxScore());
        score.setEvidence(result.getEvidence());
        score.setMissingEvidence(result.getMissingEvidence());
        score.setComment(result.getComment());
        return score;
    }

    private String criteriaSnapshot(JobOpening opening) {
        return "Title: " + nullToBlank(opening.getJobTitle()) + "\n"
                + "Requirements:\n" + nullToBlank(opening.getRequirements()) + "\n"
                + "Responsibilities:\n" + nullToBlank(opening.getResponsibilities()) + "\n"
                + "Preferred qualifications:\n" + nullToBlank(opening.getPreferredQualifications()) + "\n"
                + "Location: " + nullToBlank(opening.getLocation()) + "\n"
                + "Work arrangement: " + nullToBlank(opening.getWorkArrangement());
    }

    private String documentIds(List<CandidateDocument> documents) {
        return documents.stream()
                .map(CandidateDocument::getId)
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String joinDocumentText(List<CandidateDocument> documents) {
        return documents.stream()
                .map(CandidateDocument::getExtractedText)
                .reduce("", (left, right) -> left + "\n\n" + right);
    }

    private String normalize(String value) {
        return WORD_PATTERN.matcher(value == null ? "" : value.toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    private String join(List<String> values) {
        return values == null ? null : String.join("\n", values);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private record Criterion(String text, ShortlistingCriterionType type, boolean required) {
    }
}
