package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.kpi.dto.KpiScorecardImportResultDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardImportConfirmCommand;
import com.justjava.humanresource.kpi.dto.KpiScorecardImportPreviewDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardImportRowDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardTemplateCommand;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplate;
import com.justjava.humanresource.kpi.enums.KpiCategory;
import com.justjava.humanresource.kpi.enums.KpiFrequency;
import com.justjava.humanresource.kpi.enums.KpiHierarchyRole;
import com.justjava.humanresource.kpi.enums.KpiUnit;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class KpiScorecardImportService {

    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiScorecardTemplateService templateService;

    public KpiScorecardImportResultDTO importBalancedScorecard(MultipartFile file, String templateName, String roleName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required.");
        }

        WorkbookData workbookData = readWorkbook(file);
        List<RowData> rows = workbookData.rows();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No readable rows found in workbook.");
        }

        ImportState state = new ImportState();
        String currentPerspective = null;
        String currentGoal = null;
        boolean inCoreValues = false;

        KpiScorecardTemplateCommand command = new KpiScorecardTemplateCommand();
        command.setName(templateName != null && !templateName.isBlank() ? templateName : inferTemplateName(rows));
        command.setRoleName(roleName);
        command.setDescription("Imported balanced scorecard from " + file.getOriginalFilename());
        command.setActive(true);

        for (RowData row : rows) {
            String perspective = row.value(2);
            String goal = row.value(3);
            String indicator = row.value(4);
            String description = row.value(5);
            String timeline = row.value(6);
            String measure = row.value(7);
            BigDecimal weight = decimal(row.value(8));

            if (contains(row, "Core Values and Brand Promise")) {
                currentPerspective = "Core Values and Brand Promise";
                currentGoal = null;
                inCoreValues = true;
                continue;
            }

            if (inCoreValues) {
                String coreValue = row.value(4);
                String coreMeasure = row.value(5);
                BigDecimal coreWeight = decimal(row.value(6));
                if (isBlank(coreValue) || coreWeight == null) {
                    continue;
                }
                addLeaf(command, state, currentPerspective, null, coreValue, coreMeasure, null, null, coreWeight);
                continue;
            }

            if (weight == null || isBlank(indicator) || isHeaderRow(row)) {
                continue;
            }
            if (!isBlank(perspective)) {
                currentPerspective = perspective;
            }
            if (!isBlank(goal)) {
                currentGoal = goal;
            }
            if (isBlank(currentPerspective)) {
                continue;
            }

            addLeaf(command, state, currentPerspective, currentGoal, indicator, description, timeline, measure, weight);
        }

        for (Map.Entry<String, BigDecimal> entry : state.weights.entrySet()) {
            KpiScorecardTemplateCommand.Item item = state.itemsByKey.get(entry.getKey());
            if (item != null) {
                item.setWeight(toDecimalWeight(entry.getValue()));
            }
        }

        command.setItems(new ArrayList<>(state.itemsByKey.values()));
        KpiScorecardTemplate template = templateService.createImported(
                command,
                file.getOriginalFilename(),
                currentUsername()
        );

        return KpiScorecardImportResultDTO.builder()
                .templateId(template.getId())
                .templateName(template.getName())
                .definitionsCreated(state.definitionsCreated)
                .definitionsReused(state.definitionsReused)
                .templateItemsCreated(command.getItems().size())
                .build();
    }

    @Transactional(readOnly = true)
    public KpiScorecardImportPreviewDTO previewBalancedScorecard(MultipartFile file, String templateName, String roleName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required.");
        }
        WorkbookData workbookData = readWorkbook(file);
        List<RowData> workbookRows = workbookData.rows();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<KpiScorecardImportRowDTO> rows = parsePreviewRows(workbookRows, warnings, errors);
        BigDecimal totalWeight = rows.stream()
                .map(KpiScorecardImportRowDTO::getWeight)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (rows.isEmpty()) {
            errors.add("No importable scorecard rows were detected.");
        }
        if (totalWeight.compareTo(BigDecimal.valueOf(100)) != 0) {
            errors.add("Imported row weights total " + totalWeight + " points; published templates require exactly 100 points.");
        }
        validateDuplicatePreviewRows(rows, errors);

        return KpiScorecardImportPreviewDTO.builder()
                .templateName(templateName != null && !templateName.isBlank() ? templateName : inferTemplateName(workbookRows))
                .roleName(roleName)
                .totalWeight(totalWeight)
                .rows(rows)
                .warnings(warnings)
                .errors(errors)
                .build();
    }

    public KpiScorecardImportResultDTO confirmBalancedScorecardImport(KpiScorecardImportConfirmCommand command) {
        if (isBlank(command.getTemplateName())) {
            throw new IllegalArgumentException("Template name is required.");
        }
        if (command.getRows() == null || command.getRows().isEmpty()) {
            throw new IllegalArgumentException("At least one preview row is required to confirm import.");
        }
        List<String> errors = validateConfirmRows(command.getRows());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Import preview contains blocking errors: " + String.join("; ", errors));
        }

        ImportState state = new ImportState();
        KpiScorecardTemplateCommand templateCommand = new KpiScorecardTemplateCommand();
        templateCommand.setName(command.getTemplateName());
        templateCommand.setRoleName(command.getRoleName());
        templateCommand.setDescription("Imported balanced scorecard from preview confirmation");
        templateCommand.setActive(true);

        for (KpiScorecardImportRowDTO row : command.getRows()) {
            if (isBlank(row.getPerspective()) || isBlank(row.getIndicator()) || row.getWeight() == null) {
                continue;
            }
            addLeaf(
                    templateCommand,
                    state,
                    row.getPerspective(),
                    row.getObjective(),
                    row.getIndicator(),
                    row.getDescription(),
                    row.getTimeline(),
                    row.getMeasure(),
                    row.getWeight()
            );
        }

        for (Map.Entry<String, BigDecimal> entry : state.weights.entrySet()) {
            KpiScorecardTemplateCommand.Item item = state.itemsByKey.get(entry.getKey());
            if (item != null) {
                item.setWeight(toDecimalWeight(entry.getValue()));
            }
        }

        templateCommand.setItems(new ArrayList<>(state.itemsByKey.values()));
        KpiScorecardTemplate template = templateService.createImported(
                templateCommand,
                "preview confirmation",
                currentUsername()
        );

        return KpiScorecardImportResultDTO.builder()
                .templateId(template.getId())
                .templateName(template.getName())
                .definitionsCreated(state.definitionsCreated)
                .definitionsReused(state.definitionsReused)
                .templateItemsCreated(templateCommand.getItems().size())
                .build();
    }

    private List<KpiScorecardImportRowDTO> parsePreviewRows(List<RowData> rows, List<String> warnings, List<String> errors) {
        List<KpiScorecardImportRowDTO> previewRows = new ArrayList<>();
        String currentPerspective = null;
        String currentGoal = null;
        boolean inCoreValues = false;
        int rowNumber = 0;

        for (RowData row : rows) {
            rowNumber++;
            String perspective = row.value(2);
            String goal = row.value(3);
            String indicator = row.value(4);
            String description = row.value(5);
            String timeline = row.value(6);
            String measure = row.value(7);
            BigDecimal weight = decimal(row.value(8));

            if (contains(row, "Core Values and Brand Promise")) {
                currentPerspective = "Core Values and Brand Promise";
                currentGoal = null;
                inCoreValues = true;
                continue;
            }

            if (inCoreValues) {
                String coreValue = row.value(4);
                String coreMeasure = row.value(5);
                BigDecimal coreWeight = decimal(row.value(6));
                if (!isBlank(coreValue) && coreWeight != null) {
                    previewRows.add(KpiScorecardImportRowDTO.builder()
                            .rowNumber(rowNumber)
                            .perspective(currentPerspective)
                            .indicator(coreValue)
                            .measure(coreMeasure)
                            .weight(coreWeight)
                            .build());
                }
                continue;
            }

            if (isHeaderRow(row)) {
                continue;
            }
            if (!isBlank(perspective)) {
                currentPerspective = perspective;
            }
            if (!isBlank(goal)) {
                currentGoal = goal;
            }
            if (isBlank(indicator)) {
                continue;
            }
            if (weight == null) {
                errors.add("Row " + rowNumber + ": skipped '" + indicator + "' because weight is missing or invalid.");
                continue;
            }
            if (isBlank(currentPerspective)) {
                errors.add("Row " + rowNumber + ": skipped '" + indicator + "' because perspective could not be determined.");
                continue;
            }
            previewRows.add(KpiScorecardImportRowDTO.builder()
                    .rowNumber(rowNumber)
                    .perspective(currentPerspective)
                    .objective(currentGoal)
                    .indicator(indicator)
                    .description(description)
                    .timeline(timeline)
                    .measure(measure)
                    .weight(weight)
                    .build());
        }

        return previewRows;
    }

    private List<String> validateConfirmRows(List<KpiScorecardImportRowDTO> rows) {
        List<String> errors = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (KpiScorecardImportRowDTO row : rows) {
            String label = row.getRowNumber() != null ? "Row " + row.getRowNumber() : "Preview row";
            if (isBlank(row.getPerspective())) {
                errors.add(label + ": perspective is required.");
            }
            if (isBlank(row.getIndicator())) {
                errors.add(label + ": indicator is required.");
            }
            if (row.getWeight() == null || row.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(label + ": weight must be greater than zero.");
            } else {
                totalWeight = totalWeight.add(row.getWeight());
            }
        }

        if (totalWeight.compareTo(BigDecimal.valueOf(100)) != 0) {
            errors.add("Preview row weights must total exactly 100 points. Current total is " + totalWeight + ".");
        }
        validateDuplicatePreviewRows(rows, errors);
        return errors;
    }

    private void validateDuplicatePreviewRows(List<KpiScorecardImportRowDTO> rows, List<String> errors) {
        Set<String> seen = new java.util.HashSet<>();
        for (KpiScorecardImportRowDTO row : rows) {
            String key = code(row.getPerspective()) + "|" + code(row.getObjective()) + "|" + code(row.getIndicator());
            if (!seen.add(key)) {
                String label = row.getRowNumber() != null ? "Row " + row.getRowNumber() : "Preview row";
                errors.add(label + ": duplicate indicator under the same perspective/objective: " + row.getIndicator());
            }
        }
    }

    private void addLeaf(
            KpiScorecardTemplateCommand command,
            ImportState state,
            String perspectiveName,
            String goalName,
            String indicatorName,
            String description,
            String timeline,
            String measure,
            BigDecimal pointWeight
    ) {
        String perspectiveKey = key("perspective", perspectiveName);
        ensureItem(state, perspectiveKey, null, perspectiveName, KpiHierarchyRole.PERSPECTIVE, BigDecimal.ZERO, null, null, null, null);
        state.weights.merge(perspectiveKey, pointWeight, BigDecimal::add);

        String parentKey = perspectiveKey;
        if (!isBlank(goalName)) {
            String goalKey = key(perspectiveKey, goalName);
            ensureItem(state, goalKey, perspectiveKey, goalName, KpiHierarchyRole.OBJECTIVE, BigDecimal.ZERO, null, null, null, null);
            state.weights.merge(goalKey, pointWeight, BigDecimal::add);
            parentKey = goalKey;
        }

        String fullDescription = buildDescription(description, timeline, measure);
        String leafKey = key(parentKey, indicatorName);
        ensureItem(
                state,
                leafKey,
                parentKey,
                indicatorName,
                KpiHierarchyRole.INDICATOR,
                toDecimalWeight(pointWeight),
                fullDescription,
                timeline,
                measure,
                pointWeight
        );
    }

    private KpiScorecardTemplateCommand.Item ensureItem(
            ImportState state,
            String clientKey,
            String parentKey,
            String name,
            KpiHierarchyRole role,
            BigDecimal weight,
            String description,
            String timeline,
            String measure,
            BigDecimal originalWeight
    ) {
        KpiScorecardTemplateCommand.Item existing = state.itemsByKey.get(clientKey);
        if (existing != null) {
            return existing;
        }

        KpiDefinition parentDefinition = null;
        if (parentKey != null) {
            KpiScorecardTemplateCommand.Item parentItem = state.itemsByKey.get(parentKey);
            if (parentItem != null) {
                parentDefinition = kpiDefinitionRepository.findById(parentItem.getKpiId()).orElse(null);
            }
        }

        DefinitionResolution definitionResolution = findOrCreateDefinition(
                clientKey,
                name,
                role,
                description,
                frequency(timeline),
                parentDefinition
        );
        KpiDefinition definition = definitionResolution.definition();
        if (definitionResolution.created()) state.definitionsCreated++;
        else state.definitionsReused++;

        KpiScorecardTemplateCommand.Item item = new KpiScorecardTemplateCommand.Item();
        item.setClientKey(clientKey);
        item.setParentClientKey(parentKey);
        item.setKpiId(definition.getId());
        item.setWeight(weight);
        item.setMandatory(true);
        item.setSortOrder(state.itemsByKey.size());
        item.setFrequency(frequency(timeline));
        state.itemsByKey.put(clientKey, item);
        return item;
    }

    private DefinitionResolution findOrCreateDefinition(
            String codeSeed,
            String name,
            KpiHierarchyRole role,
            String description,
            KpiFrequency frequency,
            KpiDefinition parentDefinition
    ) {
        String code = code(codeSeed);
        Optional<KpiDefinition> existing = kpiDefinitionRepository.findByCode(code);
        if (existing.isPresent()) {
            KpiDefinition definition = existing.get();
            boolean changed = false;
            if (definition.getParentDefinition() == null && parentDefinition != null) {
                definition.setParentDefinition(parentDefinition);
                changed = true;
            }
            if (definition.getHierarchyRole() == null) {
                definition.setHierarchyRole(role);
                changed = true;
            }
            if (definition.getFrequency() == null && frequency != null) {
                definition.setFrequency(frequency);
                changed = true;
            }
            return new DefinitionResolution(changed ? kpiDefinitionRepository.saveAndFlush(definition) : definition, false);
        }

        KpiDefinition definition = new KpiDefinition();
        definition.setCode(code);
        definition.setName(name);
        definition.setDescription(description);
        definition.setCategory(KpiCategory.PRODUCTIVITY);
        definition.setTargetValue(BigDecimal.valueOf(100));
        definition.setUnit(KpiUnit.PERCENTAGE);
        definition.setHierarchyRole(role);
        definition.setFrequency(frequency);
        definition.setParentDefinition(parentDefinition);
        definition.setActive(true);
        return new DefinitionResolution(kpiDefinitionRepository.saveAndFlush(definition), true);
    }

    private WorkbookData readWorkbook(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Workbook does not contain any sheets.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<RowData> rows = new ArrayList<>();
            for (Row row : sheet) {
                Map<Integer, String> values = new LinkedHashMap<>();
                for (Cell cell : row) {
                    values.put(cell.getColumnIndex() + 1, formatter.formatCellValue(cell, evaluator));
                }
                rows.add(new RowData(values));
            }
            return new WorkbookData(rows);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to read KPI scorecard workbook.", ex);
        }
    }

    private boolean isHeaderRow(RowData row) {
        return contains(row, "PERSPECTIVE") && contains(row, "INDICATOR");
    }

    private boolean contains(RowData row, String text) {
        return row.values.values().stream()
                .filter(value -> value != null)
                .anyMatch(value -> value.trim().equalsIgnoreCase(text));
    }

    private String inferTemplateName(List<RowData> rows) {
        return rows.stream()
                .flatMap(row -> row.values.values().stream())
                .filter(value -> value != null && value.toUpperCase(Locale.ROOT).contains("KPI"))
                .findFirst()
                .orElse("Imported Balanced Scorecard");
    }

    private String buildDescription(String description, String timeline, String measure) {
        StringBuilder builder = new StringBuilder();
        if (!isBlank(description)) builder.append(description.trim());
        if (!isBlank(timeline)) builder.append(builder.isEmpty() ? "" : "\n").append("Timeline: ").append(timeline.trim());
        if (!isBlank(measure)) builder.append(builder.isEmpty() ? "" : "\n").append("Measure: ").append(measure.trim());
        return builder.toString();
    }

    private BigDecimal decimal(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim()
                .replace(",", "")
                .replace("%", "");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal toDecimalWeight(BigDecimal points) {
        return points.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private KpiFrequency frequency(String value) {
        if (isBlank(value)) return null;
        String normalized = value.toUpperCase(Locale.ROOT);
        if (normalized.contains("DAILY")) return KpiFrequency.DAILY;
        if (normalized.contains("MONTHLY")) return KpiFrequency.MONTHLY;
        if (normalized.contains("QUARTER")) return KpiFrequency.QUARTERLY;
        if (normalized.contains("BI")) return KpiFrequency.BI_ANNUALLY;
        if (normalized.contains("RECRUITMENT")) return KpiFrequency.FOR_EVERY_RECRUITMENT;
        if (normalized.contains("ANNUAL")) return KpiFrequency.ANNUALLY;
        if (normalized.contains("REQUIRED")) return KpiFrequency.AS_REQUIRED;
        return null;
    }

    private String key(String prefix, String name) {
        return code(prefix + "_" + name);
    }

    private String code(String value) {
        String code = value == null ? "KPI" : value.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return code.length() > 80 ? code.substring(0, 80) : code;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "system";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof DefaultOidcUser oidcUser) {
            Object preferredUsername = oidcUser.getClaims().get("preferred_username");
            Object email = oidcUser.getClaims().get("email");
            if (preferredUsername != null) return preferredUsername.toString();
            if (email != null) return email.toString();
        }
        return authentication.getName() != null ? authentication.getName() : "system";
    }

    private record WorkbookData(List<RowData> rows) {}

    private record RowData(Map<Integer, String> values) {
        private String value(int column) {
            return values.getOrDefault(column, "");
        }
    }

    private static class ImportState {
        private final Map<String, KpiScorecardTemplateCommand.Item> itemsByKey = new LinkedHashMap<>();
        private final Map<String, BigDecimal> weights = new HashMap<>();
        private int definitionsCreated;
        private int definitionsReused;
    }

    private record DefinitionResolution(KpiDefinition definition, boolean created) {}
}
