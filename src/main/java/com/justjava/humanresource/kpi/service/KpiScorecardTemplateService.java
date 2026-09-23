package com.justjava.humanresource.kpi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.hr.dto.KpiAssignmentItemRequestDTO;
import com.justjava.humanresource.hr.dto.KpiAssignmentResponseDTO;
import com.justjava.humanresource.hr.dto.KpiBulkAssignmentRequestDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.dto.KpiScorecardApplyPreviewDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardTemplateCommand;
import com.justjava.humanresource.kpi.dto.KpiTemplateAssignmentHistoryDTO;
import com.justjava.humanresource.kpi.dto.KpiTemplateAssignmentCommand;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplate;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateItem;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateVersion;
import com.justjava.humanresource.kpi.entity.KpiScoringRubric;
import com.justjava.humanresource.kpi.entity.KpiTemplateAssignment;
import com.justjava.humanresource.kpi.enums.KpiScorecardTemplateStatus;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateItemRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateVersionRepository;
import com.justjava.humanresource.kpi.repositories.KpiScoringRubricRepository;
import com.justjava.humanresource.kpi.repositories.KpiTemplateAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class KpiScorecardTemplateService {

    private final KpiScorecardTemplateRepository templateRepository;
    private final KpiScorecardTemplateItemRepository itemRepository;
    private final KpiScorecardTemplateVersionRepository versionRepository;
    private final KpiTemplateAssignmentRepository templateAssignmentRepository;
    private final KpiDefinitionRepository kpiRepository;
    private final KpiAssignmentRepository assignmentRepository;
    private final KpiScoringRubricRepository rubricRepository;
    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;
    private final DepartmentRepository departmentRepository;
    private final KpiAssignmentService assignmentService;
    private final ObjectMapper objectMapper;

    @Value("${app.kpi.max-kpi-weight:1.0}")
    private BigDecimal maxKpiWeight;

    public KpiScorecardTemplate create(KpiScorecardTemplateCommand command) {
        return createTemplate(command, "MANUAL", null, null);
    }

    public KpiScorecardTemplate createImported(
            KpiScorecardTemplateCommand command,
            String sourceFileName,
            String importedBy
    ) {
        return createTemplate(command, "EXCEL_IMPORT", sourceFileName, importedBy);
    }

    private KpiScorecardTemplate createTemplate(
            KpiScorecardTemplateCommand command,
            String sourceType,
            String sourceFileName,
            String importedBy
    ) {
        boolean publishRequested = command.isActive();
        command.setActive(false);
        validateTemplateCommand(command);

        KpiScorecardTemplate template = KpiScorecardTemplate.builder()
                .name(command.getName())
                .description(command.getDescription())
                .roleName(command.getRoleName())
                .active(false)
                .status(KpiScorecardTemplateStatus.DRAFT)
                .defaultRubric(resolveRubric(command.getDefaultRubricId()))
                .sourceType(sourceType)
                .sourceFileName(sourceFileName)
                .importedBy(importedBy)
                .build();

        template.setTotalWeight(calculateRootWeight(command.getItems()));
        template = templateRepository.saveAndFlush(template);
        saveItems(template, command.getItems());
        if (publishRequested) {
            command.setActive(true);
            publishTemplate(template, command);
        }
        return template;
    }

    public KpiScorecardTemplate update(Long templateId, KpiScorecardTemplateCommand command) {
        boolean publishRequested = command.isActive();
        command.setActive(false);
        validateTemplateCommand(command);

        KpiScorecardTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Scorecard template not found: " + templateId));
        if (template.getStatus() == KpiScorecardTemplateStatus.PUBLISHED) {
            throw new IllegalStateException("Published scorecard templates are immutable. Duplicate the template to make a new draft revision.");
        }

        template.setName(command.getName());
        template.setDescription(command.getDescription());
        template.setRoleName(command.getRoleName());
        template.setActive(false);
        template.setStatus(KpiScorecardTemplateStatus.DRAFT);
        template.setDefaultRubric(resolveRubric(command.getDefaultRubricId()));
        template.setTotalWeight(calculateRootWeight(command.getItems()));
        template = templateRepository.saveAndFlush(template);

        itemRepository.deleteByTemplate_Id(template.getId());
        saveItems(template, command.getItems());
        if (publishRequested) {
            command.setActive(true);
            publishTemplate(template, command);
        }
        return template;
    }

    @Transactional(readOnly = true)
    public List<KpiScorecardTemplate> getActiveTemplates() {
        return templateRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<KpiScorecardTemplate> getManageableTemplates() {
        return templateRepository.findByStatusNotOrderByNameAsc(KpiScorecardTemplateStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public KpiScorecardTemplate getTemplate(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Scorecard template not found: " + templateId));
    }

    @Transactional(readOnly = true)
    public List<KpiScorecardTemplateItem> getTemplateItems(Long templateId) {
        return itemRepository.findByTemplate_IdOrderBySortOrderAscIdAsc(templateId);
    }

    @Transactional(readOnly = true)
    public List<KpiScorecardTemplateVersion> getTemplateVersions(Long templateId) {
        return versionRepository.findByTemplate_IdOrderByVersionNumberDesc(templateId);
    }

    public KpiTemplateAssignment applyTemplate(KpiTemplateAssignmentCommand command) {
        KpiScorecardTemplate template = templateRepository.findById(command.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("Scorecard template not found: " + command.getTemplateId()));
        if (template.getStatus() == KpiScorecardTemplateStatus.ARCHIVED || !template.isActive()) {
            throw new IllegalStateException("Only active published templates can be applied.");
        }
        KpiScorecardTemplateVersion publishedVersion = versionRepository
                .findTopByTemplate_IdOrderByVersionNumberDesc(template.getId())
                .orElseGet(() -> snapshotPublishedTemplate(template));
        if (command.getValidTo() != null && command.getValidFrom() != null
                && command.getValidTo().isBefore(command.getValidFrom())) {
            throw new IllegalArgumentException("validTo cannot be before validFrom.");
        }

        List<KpiScorecardTemplateItem> items = itemRepository.findByTemplate_IdOrderBySortOrderAscIdAsc(template.getId());
        if (items.isEmpty()) {
            throw new IllegalStateException("Scorecard template has no KPI items.");
        }

        String scope = resolveScope(command);
        Long ownerId = resolveOwnerId(command);

        KpiBulkAssignmentRequestDTO request = toAssignmentRequest(items, command);
        List<KpiAssignmentResponseDTO> assignments = command.isReplaceExisting()
                ? assignmentService.replaceAssignments(scope, ownerId, request)
                : assignmentService.bulkAssign(request);

        if (assignments.isEmpty()) {
            throw new IllegalStateException("Template did not produce any KPI assignments.");
        }

        if (command.isReplaceExisting()) {
            deactivateExistingTemplateAssignments(command);
        }

        KpiTemplateAssignment templateAssignment = KpiTemplateAssignment.builder()
                .template(template)
                .templateVersion(publishedVersion)
                .employee(resolveEmployee(command.getEmployeeId()))
                .jobStep(resolveJobStep(command.getJobStepId()))
                .department(resolveDepartment(command.getDepartmentId()))
                .validFrom(command.getValidFrom() != null ? command.getValidFrom() : LocalDate.now())
                .validTo(command.getValidTo())
                .active(true)
                .appliedBy(currentUsername())
                .build();

        return templateAssignmentRepository.save(templateAssignment);
    }

    @Transactional(readOnly = true)
    public KpiScorecardApplyPreviewDTO previewApplyTemplate(KpiTemplateAssignmentCommand command) {
        KpiScorecardTemplate template = templateRepository.findById(command.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("Scorecard template not found: " + command.getTemplateId()));
        List<KpiScorecardTemplateItem> items = itemRepository.findByTemplate_IdOrderBySortOrderAscIdAsc(template.getId());
        String scope = resolveScope(command);
        Long ownerId = resolveOwnerId(command);

        List<KpiAssignmentResponseDTO> currentAssignments = directAssignments(scope, ownerId);
        List<KpiAssignmentResponseDTO> templateAssignments = items.stream()
                .map(item -> toTemplateAssignmentResponse(item, null))
                .toList();
        Set<Long> currentKpiIds = currentAssignments.stream()
                .map(KpiAssignmentResponseDTO::getKpiId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> templateKpiIds = templateAssignments.stream()
                .map(KpiAssignmentResponseDTO::getKpiId)
                .collect(java.util.stream.Collectors.toSet());

        List<KpiAssignmentResponseDTO> toAdd = templateAssignments.stream()
                .filter(assignment -> !currentKpiIds.contains(assignment.getKpiId()))
                .toList();
        List<KpiAssignmentResponseDTO> toRemove = command.isReplaceExisting()
                ? currentAssignments.stream()
                .filter(assignment -> !templateKpiIds.contains(assignment.getKpiId()))
                .toList()
                : List.of();

        List<String> warnings = new ArrayList<>();
        if (!template.isActive()) {
            warnings.add("Template is not active and cannot be applied until published.");
        }
        if (!command.isReplaceExisting() && !toRemove.isEmpty()) {
            warnings.add("Existing assignments will remain because replaceExisting is false.");
        }

        return KpiScorecardApplyPreviewDTO.builder()
                .templateId(template.getId())
                .templateName(template.getName())
                .scope(scope)
                .ownerId(ownerId)
                .effectiveWeight(assignmentService.calculateEffectiveTotalWeightFromResponses(templateAssignments))
                .currentAssignments(currentAssignments)
                .templateAssignments(templateAssignments)
                .assignmentsToAdd(toAdd)
                .assignmentsToRemove(toRemove)
                .warnings(warnings)
                .build();
    }

    @Transactional(readOnly = true)
    public List<KpiTemplateAssignmentHistoryDTO> getAssignmentHistory() {
        return templateAssignmentRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toAssignmentHistory)
                .toList();
    }

    public KpiScorecardTemplateVersion publish(Long templateId) {
        KpiScorecardTemplate template = getTemplate(templateId);
        if (template.getStatus() == KpiScorecardTemplateStatus.PUBLISHED) {
            return versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(template.getId())
                    .orElseGet(() -> snapshotPublishedTemplate(template));
        }
        List<KpiScorecardTemplateItem> items = getTemplateItems(templateId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Scorecard template has no KPI items.");
        }
        KpiScorecardTemplateCommand command = toCommand(template, items, true);
        validateTemplateCommand(command);

        template.setActive(true);
        template.setStatus(KpiScorecardTemplateStatus.PUBLISHED);
        templateRepository.saveAndFlush(template);

        return saveTemplateVersion(template, command);
    }

    private KpiScorecardTemplateVersion snapshotPublishedTemplate(KpiScorecardTemplate template) {
        List<KpiScorecardTemplateItem> items = getTemplateItems(template.getId());
        KpiScorecardTemplateCommand command = toCommand(template, items, true);
        return saveTemplateVersion(template, command);
    }

    private KpiScorecardTemplateVersion publishTemplate(
            KpiScorecardTemplate template,
            KpiScorecardTemplateCommand command
    ) {
        validateTemplateCommand(command);
        template.setActive(true);
        template.setStatus(KpiScorecardTemplateStatus.PUBLISHED);
        templateRepository.saveAndFlush(template);
        return saveTemplateVersion(template, command);
    }

    private KpiScorecardTemplateVersion saveTemplateVersion(
            KpiScorecardTemplate template,
            KpiScorecardTemplateCommand command
    ) {
        return versionRepository.save(KpiScorecardTemplateVersion.builder()
                .template(template)
                .versionNumber(versionRepository.countByTemplate_Id(template.getId()) + 1)
                .templateName(template.getName())
                .description(template.getDescription())
                .roleName(template.getRoleName())
                .publishedBy(currentUsername())
                .snapshotJson(snapshot(command))
                .build());
    }

    public KpiScorecardTemplate archive(Long templateId) {
        return archive(templateId, false);
    }

    public KpiScorecardTemplate archive(Long templateId, boolean force) {
        KpiScorecardTemplate template = getTemplate(templateId);
        List<KpiTemplateAssignment> activeAssignments =
                templateAssignmentRepository.findByTemplate_IdAndActiveTrue(templateId);
        if (!force && !activeAssignments.isEmpty()) {
            throw new IllegalStateException(
                    "Scorecard template has active assignments. Use force archive only after confirming impact."
            );
        }
        if (force) {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            activeAssignments.forEach(assignment -> {
                assignment.setActive(false);
                if (assignment.getValidTo() == null || assignment.getValidTo().isAfter(yesterday)) {
                    assignment.setValidTo(yesterday);
                }
            });
            templateAssignmentRepository.saveAll(activeAssignments);
        }
        template.setActive(false);
        template.setStatus(KpiScorecardTemplateStatus.ARCHIVED);
        return templateRepository.save(template);
    }

    public KpiScorecardTemplate duplicate(Long templateId) {
        KpiScorecardTemplate source = getTemplate(templateId);
        List<KpiScorecardTemplateItem> sourceItems = getTemplateItems(templateId);
        KpiScorecardTemplate copy = KpiScorecardTemplate.builder()
                .name(uniqueCopyName(source.getName()))
                .description(source.getDescription())
                .roleName(source.getRoleName())
                .totalWeight(source.getTotalWeight())
                .active(false)
                .status(KpiScorecardTemplateStatus.DRAFT)
                .defaultRubric(source.getDefaultRubric())
                .build();
        copy = templateRepository.saveAndFlush(copy);

        Map<Long, KpiScorecardTemplateItem> copiedBySourceId = new HashMap<>();
        for (KpiScorecardTemplateItem sourceItem : sourceItems) {
            KpiScorecardTemplateItem copiedParent = sourceItem.getParentItem() == null
                    ? null
                    : copiedBySourceId.get(sourceItem.getParentItem().getId());
            KpiScorecardTemplateItem copied = KpiScorecardTemplateItem.builder()
                    .template(copy)
                    .kpi(sourceItem.getKpi())
                    .parentItem(copiedParent)
                    .weight(sourceItem.getWeight())
                    .mandatory(sourceItem.isMandatory())
                    .sortOrder(sourceItem.getSortOrder())
                    .frequency(sourceItem.getFrequency())
                    .rubric(sourceItem.getRubric())
                    .build();
            copied = itemRepository.saveAndFlush(copied);
            copiedBySourceId.put(sourceItem.getId(), copied);
        }
        return copy;
    }

    private void saveItems(KpiScorecardTemplate template, List<KpiScorecardTemplateCommand.Item> commands) {
        Map<String, KpiScorecardTemplateItem> savedByClientKey = new HashMap<>();
        int index = 0;

        for (KpiScorecardTemplateCommand.Item command : commands) {
            KpiDefinition kpi = kpiRepository.findById(command.getKpiId())
                    .orElseThrow(() -> new IllegalArgumentException("KPI not found: " + command.getKpiId()));

            KpiScorecardTemplateItem parent = null;
            if (command.getParentClientKey() != null && !command.getParentClientKey().isBlank()) {
                parent = savedByClientKey.get(command.getParentClientKey());
                if (parent == null) {
                    throw new IllegalArgumentException("Parent template item not found: " + command.getParentClientKey());
                }
            }

            KpiScorecardTemplateItem item = KpiScorecardTemplateItem.builder()
                    .template(template)
                    .kpi(kpi)
                    .parentItem(parent)
                    .weight(command.getWeight())
                    .mandatory(command.isMandatory())
                    .sortOrder(command.getSortOrder() != null ? command.getSortOrder() : index)
                    .frequency(command.getFrequency())
                    .rubric(resolveRubric(command.getRubricId()))
                    .build();

            item = itemRepository.saveAndFlush(item);
            if (command.getClientKey() != null && !command.getClientKey().isBlank()) {
                savedByClientKey.put(command.getClientKey(), item);
            }
            index++;
        }
    }

    private void validateTemplateCommand(KpiScorecardTemplateCommand command) {
        if (command.getName() == null || command.getName().isBlank()) {
            throw new IllegalArgumentException("Scorecard template name is required.");
        }
        if (command.getItems() == null || command.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one scorecard template item is required.");
        }

        Set<String> clientKeys = new HashSet<>();
        Set<Long> kpiIds = new HashSet<>();
        Map<String, KpiScorecardTemplateCommand.Item> byClientKey = new LinkedHashMap<>();

        for (KpiScorecardTemplateCommand.Item item : command.getItems()) {
            if (item.getClientKey() == null || item.getClientKey().isBlank()) {
                throw new IllegalArgumentException("Each scorecard item requires a clientKey.");
            }
            if (!clientKeys.add(item.getClientKey())) {
                throw new IllegalArgumentException("Duplicate scorecard item clientKey: " + item.getClientKey());
            }
            if (item.getKpiId() == null) {
                throw new IllegalArgumentException("Each scorecard item requires a kpiId.");
            }
            if (!kpiIds.add(item.getKpiId())) {
                throw new IllegalArgumentException("Each KPI can appear only once in a scorecard template.");
            }
            if (item.getWeight() == null || item.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Each scorecard item weight must be greater than zero.");
            }
            byClientKey.put(item.getClientKey(), item);
        }

        for (KpiScorecardTemplateCommand.Item item : command.getItems()) {
            if (item.getParentClientKey() != null && !item.getParentClientKey().isBlank()
                    && !byClientKey.containsKey(item.getParentClientKey())) {
                throw new IllegalArgumentException("Unknown parentClientKey: " + item.getParentClientKey());
            }
        }

        validateParentCycles(command.getItems());
        if (!command.isActive()) {
            return;
        }

        validateChildWeights(command.getItems());
        BigDecimal rootWeight = calculateRootWeight(command.getItems());
        if (rootWeight.compareTo(maxKpiWeight) > 0) {
            throw new IllegalArgumentException("Scorecard template total weight cannot exceed " + maxKpiWeight);
        }
        if (rootWeight.compareTo(maxKpiWeight) != 0) {
            throw new IllegalArgumentException("Active scorecard template total weight must equal " + maxKpiWeight);
        }
    }

    private void validateParentCycles(List<KpiScorecardTemplateCommand.Item> items) {
        Map<String, String> parentByKey = new HashMap<>();
        for (KpiScorecardTemplateCommand.Item item : items) {
            parentByKey.put(item.getClientKey(), item.getParentClientKey());
        }

        for (KpiScorecardTemplateCommand.Item item : items) {
            Set<String> visited = new HashSet<>();
            String cursor = item.getClientKey();
            while (cursor != null && !cursor.isBlank()) {
                if (!visited.add(cursor)) {
                    throw new IllegalArgumentException("Scorecard hierarchy contains a cycle at: " + item.getClientKey());
                }
                cursor = parentByKey.get(cursor);
            }
        }
    }

    private void validateChildWeights(List<KpiScorecardTemplateCommand.Item> items) {
        Map<String, BigDecimal> childTotals = new HashMap<>();
        Map<String, KpiScorecardTemplateCommand.Item> byClientKey = new HashMap<>();

        for (KpiScorecardTemplateCommand.Item item : items) {
            byClientKey.put(item.getClientKey(), item);
            if (item.getParentClientKey() != null && !item.getParentClientKey().isBlank()) {
                childTotals.merge(item.getParentClientKey(), item.getWeight(), BigDecimal::add);
            }
        }

        for (Map.Entry<String, BigDecimal> entry : childTotals.entrySet()) {
            KpiScorecardTemplateCommand.Item parent = byClientKey.get(entry.getKey());
            if (parent != null && entry.getValue().compareTo(parent.getWeight()) != 0) {
                throw new IllegalArgumentException(
                        "Child weights for scorecard item '" + parent.getClientKey()
                                + "' must equal parent weight " + parent.getWeight()
                                + ". Current child total is " + entry.getValue() + "."
                );
            }
        }
    }

    private BigDecimal calculateRootWeight(List<KpiScorecardTemplateCommand.Item> items) {
        return items.stream()
                .filter(item -> item.getParentClientKey() == null || item.getParentClientKey().isBlank())
                .map(KpiScorecardTemplateCommand.Item::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private KpiBulkAssignmentRequestDTO toAssignmentRequest(
            List<KpiScorecardTemplateItem> items,
            KpiTemplateAssignmentCommand command
    ) {
        KpiBulkAssignmentRequestDTO request = new KpiBulkAssignmentRequestDTO();
        request.setEmployeeId(command.getEmployeeId());
        request.setJobStepId(command.getJobStepId());
        request.setDepartmentId(command.getDepartmentId());

        List<KpiAssignmentItemRequestDTO> requestItems = new ArrayList<>();
        for (KpiScorecardTemplateItem item : items) {
            KpiAssignmentItemRequestDTO requestItem = new KpiAssignmentItemRequestDTO();
            requestItem.setKpiId(item.getKpi().getId());
            requestItem.setWeight(item.getWeight());
            requestItem.setMandatory(item.isMandatory());
            requestItems.add(requestItem);
        }
        request.setKpis(requestItems);
        return request;
    }

    private String resolveScope(KpiTemplateAssignmentCommand command) {
        int scopeCount = 0;
        if (command.getEmployeeId() != null) scopeCount++;
        if (command.getJobStepId() != null) scopeCount++;
        if (command.getDepartmentId() != null) scopeCount++;
        if (scopeCount != 1) {
            throw new IllegalArgumentException("Exactly one of employeeId, jobStepId, or departmentId is required.");
        }
        if (command.getEmployeeId() != null) return "employee";
        if (command.getJobStepId() != null) return "grade";
        return "department";
    }

    private Long resolveOwnerId(KpiTemplateAssignmentCommand command) {
        if (command.getEmployeeId() != null) return command.getEmployeeId();
        if (command.getJobStepId() != null) return command.getJobStepId();
        return command.getDepartmentId();
    }

    private Employee resolveEmployee(Long id) {
        return id == null ? null : employeeRepository.findById(id).orElseThrow();
    }

    private JobStep resolveJobStep(Long id) {
        return id == null ? null : jobStepRepository.findById(id).orElseThrow();
    }

    private Department resolveDepartment(Long id) {
        return id == null ? null : departmentRepository.findById(id).orElseThrow();
    }

    private KpiScoringRubric resolveRubric(Long id) {
        return id == null ? null : rubricRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rubric not found: " + id));
    }

    private List<KpiAssignmentResponseDTO> directAssignments(String scope, Long ownerId) {
        return switch (scope) {
            case "employee" -> assignmentRepository.findByEmployee_IdAndActiveTrue(ownerId).stream()
                    .map(assignment -> toTemplateAssignmentResponse(null, assignment.getKpi(), assignment.getWeight(), assignment.isMandatory(), assignment.getId()))
                    .toList();
            case "grade" -> assignmentRepository.findByJobStep_IdAndActiveTrue(ownerId).stream()
                    .map(assignment -> toTemplateAssignmentResponse(null, assignment.getKpi(), assignment.getWeight(), assignment.isMandatory(), assignment.getId()))
                    .toList();
            case "department" -> assignmentRepository.findByDepartment_IdAndActiveTrue(ownerId).stream()
                    .map(assignment -> toTemplateAssignmentResponse(null, assignment.getKpi(), assignment.getWeight(), assignment.isMandatory(), assignment.getId()))
                    .toList();
            default -> throw new IllegalArgumentException("Unknown scope: " + scope);
        };
    }

    private KpiAssignmentResponseDTO toTemplateAssignmentResponse(KpiScorecardTemplateItem item, Long assignmentId) {
        return toTemplateAssignmentResponse(item, item.getKpi(), item.getWeight(), item.isMandatory(), assignmentId);
    }

    private KpiAssignmentResponseDTO toTemplateAssignmentResponse(
            KpiScorecardTemplateItem item,
            KpiDefinition kpi,
            BigDecimal weight,
            boolean mandatory,
            Long assignmentId
    ) {
        return KpiAssignmentResponseDTO.builder()
                .assignmentId(assignmentId)
                .kpiId(kpi.getId())
                .kpiCode(kpi.getCode())
                .name(kpi.getName())
                .weight(weight)
                .mandatory(mandatory)
                .targetValue(kpi.getTargetValue())
                .kpiUnit(kpi.getUnit())
                .parentDefinitionId(kpi.getParentDefinitionId())
                .parentKpi(kpi.hasChildren())
                .build();
    }

    private void deactivateExistingTemplateAssignments(KpiTemplateAssignmentCommand command) {
        List<KpiTemplateAssignment> activeAssignments;
        if (command.getEmployeeId() != null) {
            activeAssignments = templateAssignmentRepository.findByEmployee_IdAndActiveTrue(command.getEmployeeId());
        } else if (command.getJobStepId() != null) {
            activeAssignments = templateAssignmentRepository.findByJobStep_IdAndActiveTrue(command.getJobStepId());
        } else {
            activeAssignments = templateAssignmentRepository.findByDepartment_IdAndActiveTrue(command.getDepartmentId());
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        activeAssignments.forEach(assignment -> {
            assignment.setActive(false);
            if (assignment.getValidTo() == null || assignment.getValidTo().isAfter(yesterday)) {
                assignment.setValidTo(yesterday);
            }
        });
        templateAssignmentRepository.saveAll(activeAssignments);
    }

    private KpiTemplateAssignmentHistoryDTO toAssignmentHistory(KpiTemplateAssignment assignment) {
        String scope;
        Long ownerId;
        String ownerName;
        if (assignment.getEmployee() != null) {
            scope = "employee";
            ownerId = assignment.getEmployee().getId();
            ownerName = assignment.getEmployee().getFullName();
        } else if (assignment.getJobStep() != null) {
            scope = "jobStep";
            ownerId = assignment.getJobStep().getId();
            ownerName = assignment.getJobStep().getName();
        } else {
            scope = "department";
            ownerId = assignment.getDepartment().getId();
            ownerName = assignment.getDepartment().getName();
        }
        return KpiTemplateAssignmentHistoryDTO.builder()
                .id(assignment.getId())
                .templateId(assignment.getTemplate().getId())
                .templateName(assignment.getTemplate().getName())
                .templateVersionNumber(assignment.getTemplateVersion() != null
                        ? assignment.getTemplateVersion().getVersionNumber()
                        : null)
                .scope(scope)
                .ownerId(ownerId)
                .ownerName(ownerName)
                .validFrom(assignment.getValidFrom())
                .validTo(assignment.getValidTo())
                .active(assignment.isActive())
                .appliedBy(assignment.getAppliedBy())
                .createdAt(assignment.getCreatedAt())
                .build();
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

    private KpiScorecardTemplateCommand toCommand(
            KpiScorecardTemplate template,
            List<KpiScorecardTemplateItem> items,
            boolean active
    ) {
        Map<Long, String> keyByItemId = new HashMap<>();
        KpiScorecardTemplateCommand command = new KpiScorecardTemplateCommand();
        command.setName(template.getName());
        command.setDescription(template.getDescription());
        command.setRoleName(template.getRoleName());
        command.setDefaultRubricId(template.getDefaultRubric() != null ? template.getDefaultRubric().getId() : null);
        command.setActive(active);

        int index = 0;
        for (KpiScorecardTemplateItem item : items) {
            keyByItemId.put(item.getId(), "item_" + item.getId());
        }
        for (KpiScorecardTemplateItem item : items) {
            KpiScorecardTemplateCommand.Item commandItem = new KpiScorecardTemplateCommand.Item();
            commandItem.setClientKey(keyByItemId.get(item.getId()));
            commandItem.setParentClientKey(item.getParentItem() == null ? null : keyByItemId.get(item.getParentItem().getId()));
            commandItem.setKpiId(item.getKpi().getId());
            commandItem.setWeight(item.getWeight());
            commandItem.setMandatory(item.isMandatory());
            commandItem.setSortOrder(item.getSortOrder() > 0 ? item.getSortOrder() : index);
            commandItem.setFrequency(item.getFrequency());
            commandItem.setRubricId(item.getRubric() != null ? item.getRubric().getId() : null);
            command.getItems().add(commandItem);
            index++;
        }
        return command;
    }

    private String snapshot(KpiScorecardTemplateCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to create scorecard template snapshot.", ex);
        }
    }

    private String uniqueCopyName(String name) {
        String baseName = name + " Copy";
        String candidate = baseName;
        int suffix = 2;
        while (templateRepository.existsByNameIgnoreCase(candidate)) {
            candidate = baseName + " " + suffix++;
        }
        return candidate;
    }
}
