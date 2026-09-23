package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.dto.KpiAssignmentResponseDTO;
import com.justjava.humanresource.kpi.dto.BalancedScorecardSummaryDTO;
import com.justjava.humanresource.kpi.dto.KpiAppraisalLineCommand;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.entity.KpiAppraisalLine;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import com.justjava.humanresource.kpi.entity.KpiScoringRubricBand;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.kpi.repositories.KpiAppraisalLineRepository;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import com.justjava.humanresource.kpi.repositories.KpiMeasurementRepository;
import com.justjava.humanresource.kpi.repositories.KpiScoringRubricBandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class KpiAppraisalLineService {

    private final KpiAppraisalLineRepository lineRepository;
    private final EmployeeAppraisalRepository appraisalRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiMeasurementRepository measurementRepository;
    private final KpiScoringRubricBandRepository rubricBandRepository;
    private final KpiAssignmentService assignmentService;

    public List<KpiAppraisalLine> createMissingLines(Long appraisalId) {
        EmployeeAppraisal appraisal = appraisalRepository.findById(appraisalId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found: " + appraisalId));

        Map<Long, BigDecimal> measurementScores = measurementRepository
                .findByEmployee_IdAndPeriodBetween(
                        appraisal.getEmployee().getId(),
                        appraisal.getCycle().getStartPeriod(),
                        appraisal.getCycle().getEndPeriod()
                )
                .stream()
                .collect(Collectors.groupingBy(
                        measurement -> measurement.getKpi().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                this::averageScore
                        )
                ));

        List<KpiAssignmentResponseDTO> assignments =
                assignmentService.getAssignmentsForEmployee(appraisal.getEmployee().getId());

        for (KpiAssignmentResponseDTO assignment : assignments) {
            if (assignment.isParentKpi()) {
                continue;
            }
            lineRepository.findByAppraisal_IdAndKpi_Id(appraisal.getId(), assignment.getKpiId())
                    .orElseGet(() -> lineRepository.save(KpiAppraisalLine.builder()
                            .appraisal(appraisal)
                            .kpi(kpiDefinitionRepository.findById(assignment.getKpiId()).orElseThrow())
                            .weight(assignment.getWeight())
                            .rubric(resolveLineRubric(assignment.getKpiId()))
                            .measurementScore(measurementScores.get(assignment.getKpiId()))
                            .finalScore(measurementScores.get(assignment.getKpiId()))
                            .weightedScore(weightedScore(measurementScores.get(assignment.getKpiId()), assignment.getWeight()))
                            .build()));
        }

        return getLines(appraisalId);
    }

    @Transactional(readOnly = true)
    public List<KpiAppraisalLine> getLines(Long appraisalId) {
        return lineRepository.findByAppraisal_IdOrderByKpi_NameAsc(appraisalId);
    }

    public KpiAppraisalLine updateSelfInput(Long lineId, KpiAppraisalLineCommand command) {
        KpiAppraisalLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("KPI appraisal line not found: " + lineId));

        KpiScoringRubricBand band = resolveBand(command.getSelfRubricBandId(), line.getRubric());
        BigDecimal score = band != null ? band.getNumericScore() : command.getSelfScore();
        validateScore(score, "selfScore");
        line.setSelfRubricBand(band);
        line.setSelfScore(score);
        line.setSelfComment(command.getSelfComment());
        line.setEvidenceUrl(command.getEvidenceUrl());
        recalculate(line);
        KpiAppraisalLine saved = lineRepository.save(line);
        refreshAppraisalKpiScore(saved.getAppraisal().getId());
        return saved;
    }

    public KpiAppraisalLine updateManagerInput(Long lineId, KpiAppraisalLineCommand command) {
        KpiAppraisalLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("KPI appraisal line not found: " + lineId));

        KpiScoringRubricBand band = resolveBand(command.getManagerRubricBandId(), line.getRubric());
        BigDecimal score = band != null ? band.getNumericScore() : command.getManagerScore();
        validateScore(score, "managerScore");
        line.setManagerRubricBand(band);
        line.setManagerScore(score);
        line.setManagerComment(command.getManagerComment());
        line.setEvidenceUrl(command.getEvidenceUrl());
        recalculate(line);
        KpiAppraisalLine saved = lineRepository.save(line);
        refreshAppraisalKpiScore(saved.getAppraisal().getId());
        return saved;
    }

    public BigDecimal calculateWeightedFinalScore(Long appraisalId) {
        List<KpiAppraisalLine> lines = getLines(appraisalId);
        if (lines.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal weightedTotal = lines.stream()
                .map(line -> line.getWeightedScore() != null ? line.getWeightedScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWeight = lines.stream()
                .map(line -> line.getWeight() != null ? line.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return weightedTotal.divide(totalWeight, 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<BalancedScorecardSummaryDTO> getPerspectiveSummary(Long appraisalId) {
        Map<Long, PerspectiveAccumulator> grouped = new LinkedHashMap<>();

        for (KpiAppraisalLine line : getLines(appraisalId)) {
            KpiDefinition perspective = topLevelPerspective(line.getKpi());
            Long key = perspective.getId() != null ? perspective.getId() : line.getKpi().getId();

            PerspectiveAccumulator accumulator = grouped.computeIfAbsent(
                    key,
                    ignored -> new PerspectiveAccumulator(perspective.getId(), perspective.getName())
            );
            accumulator.weight = accumulator.weight.add(line.getWeight() != null ? line.getWeight() : BigDecimal.ZERO);
            accumulator.achievedScore = accumulator.achievedScore.add(
                    line.getWeightedScore() != null ? line.getWeightedScore() : BigDecimal.ZERO
            );
        }

        return grouped.values().stream()
                .map(accumulator -> BalancedScorecardSummaryDTO.builder()
                        .perspectiveKpiId(accumulator.perspectiveKpiId)
                        .perspectiveName(accumulator.perspectiveName)
                        .weight(accumulator.weight)
                        .possibleScore(accumulator.weight.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
                        .achievedScore(accumulator.achievedScore.setScale(2, RoundingMode.HALF_UP))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<KpiScoringRubricBand>> getRubricBandsByLine(Long appraisalId) {
        Map<Long, List<KpiScoringRubricBand>> bandsByLine = new LinkedHashMap<>();
        for (KpiAppraisalLine line : getLines(appraisalId)) {
            if (line.getRubric() != null && line.getRubric().getId() != null) {
                bandsByLine.put(line.getId(), rubricBandRepository.findByRubric_IdOrderBySortOrderAscIdAsc(line.getRubric().getId()));
            } else {
                bandsByLine.put(line.getId(), List.of());
            }
        }
        return bandsByLine;
    }

    private void recalculate(KpiAppraisalLine line) {
        BigDecimal score = line.getManagerScore() != null
                ? line.getManagerScore()
                : line.getSelfScore() != null
                ? line.getSelfScore()
                : line.getMeasurementScore();

        line.setFinalScore(score);
        line.setWeightedScore(weightedScore(score, line.getWeight()));
    }

    private BigDecimal averageScore(List<KpiMeasurement> measurements) {
        if (measurements == null || measurements.isEmpty()) {
            return null;
        }
        BigDecimal total = measurements.stream()
                .map(KpiMeasurement::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(measurements.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedScore(BigDecimal score, BigDecimal weight) {
        if (score == null || weight == null) {
            return null;
        }
        return score.multiply(weight).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateScore(BigDecimal score, String fieldName) {
        if (score == null) {
            return;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100.");
        }
    }

    private com.justjava.humanresource.kpi.entity.KpiScoringRubric resolveLineRubric(Long kpiId) {
        KpiDefinition definition = kpiDefinitionRepository.findById(kpiId).orElseThrow();
        if (definition.getScoringRubric() != null) {
            return definition.getScoringRubric();
        }
        KpiDefinition cursor = definition.getParentDefinition();
        while (cursor != null) {
            if (cursor.getScoringRubric() != null) {
                return cursor.getScoringRubric();
            }
            cursor = cursor.getParentDefinition();
        }
        return null;
    }

    private KpiScoringRubricBand resolveBand(Long bandId, com.justjava.humanresource.kpi.entity.KpiScoringRubric rubric) {
        if (bandId == null) {
            return null;
        }
        KpiScoringRubricBand band = rubricBandRepository.findById(bandId)
                .orElseThrow(() -> new IllegalArgumentException("Rubric band not found: " + bandId));
        if (rubric == null || band.getRubric() == null || !band.getRubric().getId().equals(rubric.getId())) {
            throw new IllegalArgumentException("Rubric band does not belong to this KPI line rubric.");
        }
        return band;
    }

    private void refreshAppraisalKpiScore(Long appraisalId) {
        EmployeeAppraisal appraisal = appraisalRepository.findById(appraisalId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found: " + appraisalId));
        appraisal.setKpiScore(calculateWeightedFinalScore(appraisalId));
        appraisalRepository.save(appraisal);
    }

    private KpiDefinition topLevelPerspective(KpiDefinition kpi) {
        KpiDefinition cursor = kpi;
        while (cursor.getParentDefinition() != null) {
            cursor = cursor.getParentDefinition();
        }
        return cursor;
    }

    private static class PerspectiveAccumulator {
        private final Long perspectiveKpiId;
        private final String perspectiveName;
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal achievedScore = BigDecimal.ZERO;

        private PerspectiveAccumulator(Long perspectiveKpiId, String perspectiveName) {
            this.perspectiveKpiId = perspectiveKpiId;
            this.perspectiveName = perspectiveName;
        }
    }
}
