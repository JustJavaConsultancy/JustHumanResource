package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.kpi.dto.KpiAppraisalLineCommand;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.entity.KpiAppraisalLine;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiScoringRubric;
import com.justjava.humanresource.kpi.entity.KpiScoringRubricBand;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.kpi.repositories.KpiAppraisalLineRepository;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import com.justjava.humanresource.kpi.repositories.KpiMeasurementRepository;
import com.justjava.humanresource.kpi.repositories.KpiScoringRubricBandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiAppraisalLineServiceTest {

    @Mock private KpiAppraisalLineRepository lineRepository;
    @Mock private EmployeeAppraisalRepository appraisalRepository;
    @Mock private KpiDefinitionRepository kpiDefinitionRepository;
    @Mock private KpiMeasurementRepository measurementRepository;
    @Mock private KpiScoringRubricBandRepository rubricBandRepository;
    @Mock private KpiAssignmentService assignmentService;

    private KpiAppraisalLineService service;

    @BeforeEach
    void setUp() {
        service = new KpiAppraisalLineService(
                lineRepository,
                appraisalRepository,
                kpiDefinitionRepository,
                measurementRepository,
                rubricBandRepository,
                assignmentService
        );
    }

    @Test
    void selfRubricBandSelectionSetsScoreAndRefreshesAppraisalKpiScore() {
        KpiScoringRubric rubric = KpiScoringRubric.builder().name("Default").active(true).build();
        rubric.setId(1L);
        KpiScoringRubricBand band = KpiScoringRubricBand.builder()
                .rubric(rubric)
                .label("Excellent")
                .numericScore(new BigDecimal("90"))
                .build();
        band.setId(5L);

        EmployeeAppraisal appraisal = new EmployeeAppraisal();
        appraisal.setId(20L);

        KpiDefinition definition = new KpiDefinition();
        definition.setId(30L);
        definition.setName("Service Delivery");

        KpiAppraisalLine line = KpiAppraisalLine.builder()
                .appraisal(appraisal)
                .kpi(definition)
                .rubric(rubric)
                .weight(new BigDecimal("0.50"))
                .build();
        line.setId(10L);

        when(lineRepository.findById(10L)).thenReturn(Optional.of(line));
        when(rubricBandRepository.findById(5L)).thenReturn(Optional.of(band));
        when(lineRepository.save(any(KpiAppraisalLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appraisalRepository.findById(20L)).thenReturn(Optional.of(appraisal));
        when(lineRepository.findByAppraisal_IdOrderByKpi_NameAsc(20L)).thenReturn(List.of(line));

        KpiAppraisalLineCommand command = new KpiAppraisalLineCommand();
        command.setSelfRubricBandId(5L);
        command.setSelfComment("Evidence reviewed");

        KpiAppraisalLine updated = service.updateSelfInput(10L, command);

        assertEquals(new BigDecimal("90"), updated.getSelfScore());
        assertEquals(new BigDecimal("45.00"), updated.getWeightedScore());
        assertEquals(new BigDecimal("90.00"), appraisal.getKpiScore());
    }
}
