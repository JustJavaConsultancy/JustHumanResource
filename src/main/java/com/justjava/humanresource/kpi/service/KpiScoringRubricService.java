package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.kpi.dto.KpiScoringRubricCommand;
import com.justjava.humanresource.kpi.entity.KpiScoringRubric;
import com.justjava.humanresource.kpi.entity.KpiScoringRubricBand;
import com.justjava.humanresource.kpi.repositories.KpiScoringRubricBandRepository;
import com.justjava.humanresource.kpi.repositories.KpiScoringRubricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class KpiScoringRubricService {

    private final KpiScoringRubricRepository rubricRepository;
    private final KpiScoringRubricBandRepository bandRepository;

    public KpiScoringRubric create(KpiScoringRubricCommand command) {
        validate(command);

        KpiScoringRubric rubric = KpiScoringRubric.builder()
                .name(command.getName())
                .description(command.getDescription())
                .active(command.isActive())
                .build();

        rubric = rubricRepository.saveAndFlush(rubric);
        saveBands(rubric, command.getBands());
        return rubric;
    }

    public KpiScoringRubric update(Long id, KpiScoringRubricCommand command) {
        validate(command);

        KpiScoringRubric rubric = rubricRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rubric not found: " + id));

        rubric.setName(command.getName());
        rubric.setDescription(command.getDescription());
        rubric.setActive(command.isActive());
        rubric = rubricRepository.saveAndFlush(rubric);

        bandRepository.deleteByRubric_Id(rubric.getId());
        saveBands(rubric, command.getBands());
        return rubric;
    }

    @Transactional(readOnly = true)
    public List<KpiScoringRubric> getActiveRubrics() {
        return rubricRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public KpiScoringRubric getRubric(Long id) {
        return rubricRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rubric not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<KpiScoringRubricBand> getBands(Long rubricId) {
        return bandRepository.findByRubric_IdOrderBySortOrderAscIdAsc(rubricId);
    }

    private void saveBands(KpiScoringRubric rubric, List<KpiScoringRubricCommand.Band> bands) {
        int index = 0;
        for (KpiScoringRubricCommand.Band bandCommand : bands) {
            if (bandCommand.getNumericScore() == null) {
                throw new IllegalArgumentException("Rubric band numericScore is required.");
            }
            KpiScoringRubricBand band = KpiScoringRubricBand.builder()
                    .rubric(rubric)
                    .label(bandCommand.getLabel())
                    .minScore(bandCommand.getMinScore())
                    .maxScore(bandCommand.getMaxScore())
                    .numericScore(bandCommand.getNumericScore())
                    .description(bandCommand.getDescription())
                    .sortOrder(bandCommand.getSortOrder() != null ? bandCommand.getSortOrder() : index)
                    .build();
            bandRepository.save(band);
            index++;
        }
    }

    private void validate(KpiScoringRubricCommand command) {
        if (command.getName() == null || command.getName().isBlank()) {
            throw new IllegalArgumentException("Rubric name is required.");
        }
        if (command.getBands() == null || command.getBands().isEmpty()) {
            throw new IllegalArgumentException("At least one rubric band is required.");
        }

        Set<String> labels = new HashSet<>();
        for (KpiScoringRubricCommand.Band band : command.getBands()) {
            if (band.getLabel() == null || band.getLabel().isBlank()) {
                throw new IllegalArgumentException("Rubric band label is required.");
            }
            if (!labels.add(band.getLabel().trim().toLowerCase())) {
                throw new IllegalArgumentException("Duplicate rubric band label: " + band.getLabel());
            }
            if (band.getNumericScore() == null) {
                throw new IllegalArgumentException("Rubric band numericScore is required.");
            }
            if (band.getMinScore() != null && band.getMaxScore() != null
                    && band.getMinScore().compareTo(band.getMaxScore()) > 0) {
                throw new IllegalArgumentException("Rubric band minScore cannot exceed maxScore.");
            }
        }

        List<KpiScoringRubricCommand.Band> rangedBands = command.getBands().stream()
                .filter(band -> band.getMinScore() != null && band.getMaxScore() != null)
                .sorted((left, right) -> left.getMinScore().compareTo(right.getMinScore()))
                .toList();

        BigDecimal previousMax = null;
        for (KpiScoringRubricCommand.Band band : rangedBands) {
            if (previousMax != null && band.getMinScore().compareTo(previousMax) <= 0) {
                throw new IllegalArgumentException("Rubric score bands cannot overlap.");
            }
            previousMax = band.getMaxScore();
        }
    }
}
