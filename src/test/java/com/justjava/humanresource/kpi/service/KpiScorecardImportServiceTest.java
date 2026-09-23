package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.kpi.dto.KpiScorecardImportPreviewDTO;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class KpiScorecardImportServiceTest {

    @Test
    void previewParsesWorkbookRowsWithPoi() throws Exception {
        KpiScorecardImportService service = new KpiScorecardImportService(
                mock(KpiDefinitionRepository.class),
                mock(KpiScorecardTemplateService.class)
        );

        KpiScorecardImportPreviewDTO preview = service.previewBalancedScorecard(
                workbook(
                        row("Financial", "Cost Control", "Reduce cost", "Reduce operating cost", "Monthly", "Percent", 40),
                        row("People", "Engagement", "Improve engagement", "Survey score", "Quarterly", "Percent", 60)
                ),
                "Head HR",
                "Head Human Resources"
        );

        assertEquals("Head HR", preview.getTemplateName());
        assertEquals(2, preview.getRows().size());
        assertEquals("Financial", preview.getRows().get(0).getPerspective());
        assertEquals("Reduce cost", preview.getRows().get(0).getIndicator());
        assertTrue(preview.getWarnings().isEmpty());
    }

    @Test
    void previewReturnsWarningForInvalidWeight() throws Exception {
        KpiScorecardImportService service = new KpiScorecardImportService(
                mock(KpiDefinitionRepository.class),
                mock(KpiScorecardTemplateService.class)
        );

        KpiScorecardImportPreviewDTO preview = service.previewBalancedScorecard(
                workbookWithInvalidWeight(),
                "Broken",
                null
        );

        assertTrue(preview.getRows().isEmpty());
        assertFalse(preview.getErrors().isEmpty());
    }

    private MockMultipartFile workbook(RowSpec... specs) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Scorecard");
            int rowIndex = 0;
            for (RowSpec spec : specs) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(1).setCellValue(spec.perspective);
                row.createCell(2).setCellValue(spec.objective);
                row.createCell(3).setCellValue(spec.indicator);
                row.createCell(4).setCellValue(spec.description);
                row.createCell(5).setCellValue(spec.timeline);
                row.createCell(6).setCellValue(spec.measure);
                row.createCell(7).setCellValue(spec.weight);
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "scorecard.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile workbookWithInvalidWeight() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Scorecard");
            Row row = sheet.createRow(0);
            row.createCell(1).setCellValue("Financial");
            row.createCell(3).setCellValue("Reduce cost");
            row.createCell(7).setCellValue("not-a-number");
            workbook.write(output);
            return new MockMultipartFile("file", "scorecard.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private RowSpec row(
            String perspective,
            String objective,
            String indicator,
            String description,
            String timeline,
            String measure,
            double weight
    ) {
        return new RowSpec(perspective, objective, indicator, description, timeline, measure, weight);
    }

    private record RowSpec(
            String perspective,
            String objective,
            String indicator,
            String description,
            String timeline,
            String measure,
            double weight
    ) {}
}
