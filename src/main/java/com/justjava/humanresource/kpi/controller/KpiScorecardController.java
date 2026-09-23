package com.justjava.humanresource.kpi.controller;

import com.justjava.humanresource.kpi.dto.KpiScorecardTemplateCommand;
import com.justjava.humanresource.kpi.dto.KpiScorecardApplyPreviewDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardImportConfirmCommand;
import com.justjava.humanresource.kpi.dto.KpiScorecardImportPreviewDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardTemplateDetailDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardImportResultDTO;
import com.justjava.humanresource.kpi.dto.KpiScoringRubricDetailDTO;
import com.justjava.humanresource.kpi.dto.KpiScoringRubricCommand;
import com.justjava.humanresource.kpi.dto.KpiTemplateAssignmentCommand;
import com.justjava.humanresource.kpi.dto.KpiTemplateAssignmentHistoryDTO;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplate;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateItem;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateVersion;
import com.justjava.humanresource.kpi.entity.KpiScoringRubric;
import com.justjava.humanresource.kpi.entity.KpiScoringRubricBand;
import com.justjava.humanresource.kpi.entity.KpiTemplateAssignment;
import com.justjava.humanresource.kpi.service.KpiScorecardTemplateService;
import com.justjava.humanresource.kpi.service.KpiScorecardImportService;
import com.justjava.humanresource.kpi.service.KpiScoringRubricService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kpi/scorecards")
@RequiredArgsConstructor
@PreAuthorize("@kpiAuthorization.canManageKpi(authentication)")
public class KpiScorecardController {

    private final KpiScorecardTemplateService templateService;
    private final KpiScoringRubricService rubricService;
    private final KpiScorecardImportService importService;

    @PostMapping("/templates")
    public KpiScorecardTemplate createTemplate(@RequestBody KpiScorecardTemplateCommand command) {
        return templateService.create(command);
    }

    @PutMapping("/templates/{id}")
    public KpiScorecardTemplate updateTemplate(
            @PathVariable Long id,
            @RequestBody KpiScorecardTemplateCommand command
    ) {
        return templateService.update(id, command);
    }

    @GetMapping("/templates")
    public List<KpiScorecardTemplate> getActiveTemplates() {
        return templateService.getActiveTemplates();
    }

    @GetMapping("/templates/{id}")
    public KpiScorecardTemplateDetailDTO getTemplate(@PathVariable Long id) {
        KpiScorecardTemplate template = templateService.getTemplate(id);
        List<KpiScorecardTemplateItem> items = templateService.getTemplateItems(id);
        Map<Long, String> keyByItemId = new HashMap<>();
        for (KpiScorecardTemplateItem item : items) {
            keyByItemId.put(item.getId(), "item_" + item.getId());
        }
        return KpiScorecardTemplateDetailDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .roleName(template.getRoleName())
                .totalWeight(template.getTotalWeight())
                .active(template.isActive())
                .status(template.getStatus())
                .defaultRubricId(template.getDefaultRubric() != null ? template.getDefaultRubric().getId() : null)
                .sourceType(template.getSourceType())
                .sourceFileName(template.getSourceFileName())
                .importedBy(template.getImportedBy())
                .items(items.stream()
                        .map(item -> KpiScorecardTemplateDetailDTO.Item.builder()
                                .id(item.getId())
                                .clientKey(keyByItemId.get(item.getId()))
                                .parentClientKey(item.getParentItem() == null ? null : keyByItemId.get(item.getParentItem().getId()))
                                .kpiId(item.getKpi().getId())
                                .weight(item.getWeight())
                                .mandatory(item.isMandatory())
                                .sortOrder(item.getSortOrder())
                                .frequency(item.getFrequency())
                                .rubricId(item.getRubric() != null ? item.getRubric().getId() : null)
                                .build())
                        .toList())
                .build();
    }

    @GetMapping("/templates/{id}/items")
    public List<KpiScorecardTemplateItem> getTemplateItems(@PathVariable Long id) {
        return templateService.getTemplateItems(id);
    }

    @GetMapping("/templates/{id}/versions")
    public List<KpiScorecardTemplateVersion> getTemplateVersions(@PathVariable Long id) {
        return templateService.getTemplateVersions(id);
    }

    @PostMapping("/templates/apply")
    public KpiTemplateAssignment applyTemplate(@RequestBody KpiTemplateAssignmentCommand command) {
        return templateService.applyTemplate(command);
    }

    @PostMapping("/templates/apply-preview")
    public KpiScorecardApplyPreviewDTO previewApplyTemplate(@RequestBody KpiTemplateAssignmentCommand command) {
        return templateService.previewApplyTemplate(command);
    }

    @GetMapping("/templates/assignments")
    public List<KpiTemplateAssignmentHistoryDTO> getAssignmentHistory() {
        return templateService.getAssignmentHistory();
    }

    @PostMapping("/templates/{id}/publish")
    public KpiScorecardTemplate publishTemplate(@PathVariable Long id) {
        templateService.publish(id);
        return templateService.getTemplate(id);
    }

    @PostMapping("/templates/{id}/archive")
    public KpiScorecardTemplate archiveTemplate(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return templateService.archive(id, force);
    }

    @PostMapping("/templates/{id}/duplicate")
    public KpiScorecardTemplate duplicateTemplate(@PathVariable Long id) {
        return templateService.duplicate(id);
    }

    @PostMapping("/templates/import")
    public KpiScorecardImportResultDTO importTemplate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String templateName,
            @RequestParam(required = false) String roleName
    ) {
        return importService.importBalancedScorecard(file, templateName, roleName);
    }

    @PostMapping("/templates/import/preview")
    public KpiScorecardImportPreviewDTO previewImportTemplate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String templateName,
            @RequestParam(required = false) String roleName
    ) {
        return importService.previewBalancedScorecard(file, templateName, roleName);
    }

    @PostMapping("/templates/import/confirm")
    public KpiScorecardImportResultDTO confirmImportTemplate(@RequestBody KpiScorecardImportConfirmCommand command) {
        return importService.confirmBalancedScorecardImport(command);
    }

    @PostMapping("/rubrics")
    public KpiScoringRubric createRubric(@RequestBody KpiScoringRubricCommand command) {
        return rubricService.create(command);
    }

    @PutMapping("/rubrics/{id}")
    public KpiScoringRubric updateRubric(
            @PathVariable Long id,
            @RequestBody KpiScoringRubricCommand command
    ) {
        return rubricService.update(id, command);
    }

    @GetMapping("/rubrics")
    public List<KpiScoringRubric> getRubrics() {
        return rubricService.getActiveRubrics();
    }

    @GetMapping("/rubrics/{id}")
    public KpiScoringRubricDetailDTO getRubric(@PathVariable Long id) {
        KpiScoringRubric rubric = rubricService.getRubric(id);
        return KpiScoringRubricDetailDTO.builder()
                .id(rubric.getId())
                .name(rubric.getName())
                .description(rubric.getDescription())
                .active(rubric.isActive())
                .bands(rubricService.getBands(id).stream()
                        .map(band -> KpiScoringRubricDetailDTO.Band.builder()
                                .id(band.getId())
                                .label(band.getLabel())
                                .minScore(band.getMinScore())
                                .maxScore(band.getMaxScore())
                                .numericScore(band.getNumericScore())
                                .description(band.getDescription())
                                .sortOrder(band.getSortOrder())
                                .build())
                        .toList())
                .build();
    }

    @GetMapping("/rubrics/{id}/bands")
    public List<KpiScoringRubricBand> getRubricBands(@PathVariable Long id) {
        return rubricService.getBands(id);
    }
}
