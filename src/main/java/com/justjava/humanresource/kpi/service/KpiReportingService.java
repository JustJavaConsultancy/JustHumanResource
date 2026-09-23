package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.kpi.dto.KpiCompletionStatusDTO;
import com.justjava.humanresource.kpi.dto.KpiCyclePerformanceReportDTO;
import com.justjava.humanresource.kpi.dto.KpiDepartmentPerformanceDTO;
import com.justjava.humanresource.kpi.dto.KpiPerspectivePerformanceDTO;
import com.justjava.humanresource.kpi.dto.KpiReportFilterDTO;
import com.justjava.humanresource.kpi.dto.KpiRubricDistributionDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardMonitoringDTO;
import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.entity.KpiAppraisalLine;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplate;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateItem;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateVersion;
import com.justjava.humanresource.kpi.entity.KpiTemplateAssignment;
import com.justjava.humanresource.kpi.enums.AppraisalStatus;
import com.justjava.humanresource.kpi.enums.KpiScorecardTemplateStatus;
import com.justjava.humanresource.kpi.repositories.AppraisalCycleRepository;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.kpi.repositories.KpiAppraisalLineRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateItemRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateVersionRepository;
import com.justjava.humanresource.kpi.repositories.KpiTemplateAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KpiReportingService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final KpiScorecardTemplateRepository templateRepository;
    private final KpiScorecardTemplateItemRepository templateItemRepository;
    private final KpiScorecardTemplateVersionRepository versionRepository;
    private final KpiTemplateAssignmentRepository assignmentRepository;
    private final EmployeeAppraisalRepository appraisalRepository;
    private final KpiAppraisalLineRepository lineRepository;
    private final AppraisalCycleRepository cycleRepository;

    public KpiScorecardMonitoringDTO getMonitoringDashboard() {
        List<KpiScorecardTemplate> templates = templateRepository.findAll();
        List<KpiTemplateAssignment> assignments = assignmentRepository.findAll();
        List<EmployeeAppraisal> appraisals = appraisalRepository.findAll();

        return KpiScorecardMonitoringDTO.builder()
                .draftTemplates(countTemplates(templates, KpiScorecardTemplateStatus.DRAFT))
                .publishedTemplates(countTemplates(templates, KpiScorecardTemplateStatus.PUBLISHED))
                .archivedTemplates(countTemplates(templates, KpiScorecardTemplateStatus.ARCHIVED))
                .activeEmployeeAssignments(countActiveAssignments(assignments, KpiTemplateAssignment::getEmployee))
                .activeJobStepAssignments(countActiveAssignments(assignments, KpiTemplateAssignment::getJobStep))
                .activeDepartmentAssignments(countActiveAssignments(assignments, KpiTemplateAssignment::getDepartment))
                .pendingSelfReview(appraisals.stream().filter(this::isPendingSelfReview).count())
                .pendingManagerReview(appraisals.stream().filter(this::isPendingManagerReview).count())
                .recentlyImportedTemplates(recentImports(templates))
                .recentlyPublishedVersions(recentVersions())
                .templatesWithValidationIssues(templateIssues(templates))
                .build();
    }

    public KpiCyclePerformanceReportDTO getPerformanceReport(KpiReportFilterDTO filter) {
        List<EmployeeAppraisal> appraisals = filteredAppraisals(filter);
        List<EmployeeAppraisal> completed = appraisals.stream()
                .filter(this::hasScore)
                .toList();
        AppraisalCycle cycle = resolveCycle(filter);

        return KpiCyclePerformanceReportDTO.builder()
                .cycleId(cycle != null ? cycle.getId() : null)
                .cycleName(cycle != null ? cycle.getName() : "All Cycles")
                .averageScore(averageScore(completed))
                .totalAppraisals(appraisals.size())
                .completedAppraisals(completed.size())
                .completionRate(rate(completed.size(), appraisals.size()))
                .topPerformers(performers(completed, Comparator.comparing(this::score).reversed()))
                .bottomPerformers(performers(completed, Comparator.comparing(this::score)))
                .trend(cycleTrend(filter))
                .build();
    }

    public List<KpiDepartmentPerformanceDTO> getDepartmentPerformance(KpiReportFilterDTO filter) {
        Map<Long, List<EmployeeAppraisal>> grouped = filteredAppraisals(filter).stream()
                .filter(appraisal -> appraisal.getEmployee() != null && appraisal.getEmployee().getDepartment() != null)
                .collect(Collectors.groupingBy(
                        appraisal -> appraisal.getEmployee().getDepartment().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.values().stream()
                .map(rows -> {
                    Department department = rows.get(0).getEmployee().getDepartment();
                    List<EmployeeAppraisal> completed = rows.stream().filter(this::hasScore).toList();
                    return KpiDepartmentPerformanceDTO.builder()
                            .departmentId(department.getId())
                            .departmentName(department.getName())
                            .totalAppraisals(rows.size())
                            .completedAppraisals(completed.size())
                            .averageScore(averageScore(completed))
                            .completionRate(rate(completed.size(), rows.size()))
                            .build();
                })
                .sorted(Comparator.comparing(KpiDepartmentPerformanceDTO::getDepartmentName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public List<KpiDepartmentPerformanceDTO> getJobStepPerformance(KpiReportFilterDTO filter) {
        Map<Long, List<EmployeeAppraisal>> grouped = filteredAppraisals(filter).stream()
                .filter(appraisal -> appraisal.getEmployee() != null && appraisal.getEmployee().getJobStep() != null)
                .collect(Collectors.groupingBy(
                        appraisal -> appraisal.getEmployee().getJobStep().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.values().stream()
                .map(rows -> {
                    JobStep jobStep = rows.get(0).getEmployee().getJobStep();
                    List<EmployeeAppraisal> completed = rows.stream().filter(this::hasScore).toList();
                    return KpiDepartmentPerformanceDTO.builder()
                            .departmentId(jobStep.getId())
                            .departmentName(jobStep.getName())
                            .totalAppraisals(rows.size())
                            .completedAppraisals(completed.size())
                            .averageScore(averageScore(completed))
                            .completionRate(rate(completed.size(), rows.size()))
                            .build();
                })
                .sorted(Comparator.comparing(KpiDepartmentPerformanceDTO::getDepartmentName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public List<KpiPerspectivePerformanceDTO> getPerspectivePerformance(KpiReportFilterDTO filter) {
        List<Long> appraisalIds = filteredAppraisals(filter).stream()
                .map(EmployeeAppraisal::getId)
                .collect(Collectors.toSet())
                .stream()
                .toList();
        Map<Long, PerspectiveAccumulator> grouped = new LinkedHashMap<>();
        for (KpiAppraisalLine line : lineRepository.findAll()) {
            if (!appraisalIds.contains(line.getAppraisal().getId()) || line.getFinalScore() == null) {
                continue;
            }
            KpiDefinition perspective = topLevelPerspective(line.getKpi());
            if (filter != null && filter.getPerspectiveId() != null && !Objects.equals(perspective.getId(), filter.getPerspectiveId())) {
                continue;
            }
            PerspectiveAccumulator accumulator = grouped.computeIfAbsent(
                    perspective.getId(),
                    ignored -> new PerspectiveAccumulator(perspective.getId(), perspective.getName())
            );
            accumulator.scoredLines++;
            accumulator.scoreTotal = accumulator.scoreTotal.add(line.getFinalScore());
            accumulator.weightedTotal = accumulator.weightedTotal.add(line.getWeightedScore() != null ? line.getWeightedScore() : BigDecimal.ZERO);
        }
        return grouped.values().stream()
                .map(acc -> KpiPerspectivePerformanceDTO.builder()
                        .perspectiveId(acc.id)
                        .perspectiveName(acc.name)
                        .averageScore(divide(acc.scoreTotal, acc.scoredLines))
                        .totalWeightedScore(acc.weightedTotal.setScale(2, RoundingMode.HALF_UP))
                        .scoredLines(acc.scoredLines)
                        .build())
                .toList();
    }

    public List<KpiRubricDistributionDTO> getRubricDistribution(KpiReportFilterDTO filter) {
        List<Long> appraisalIds = filteredAppraisals(filter).stream().map(EmployeeAppraisal::getId).toList();
        Map<Long, RubricAccumulator> grouped = new LinkedHashMap<>();
        for (KpiAppraisalLine line : lineRepository.findAll()) {
            if (!appraisalIds.contains(line.getAppraisal().getId())) {
                continue;
            }
            if (line.getSelfRubricBand() != null) {
                grouped.computeIfAbsent(line.getSelfRubricBand().getId(), id -> new RubricAccumulator(line.getSelfRubricBand())).self++;
            }
            if (line.getManagerRubricBand() != null) {
                grouped.computeIfAbsent(line.getManagerRubricBand().getId(), id -> new RubricAccumulator(line.getManagerRubricBand())).manager++;
            }
        }
        return grouped.values().stream()
                .filter(acc -> filter == null || filter.getRubricBandId() == null || Objects.equals(acc.id, filter.getRubricBandId()))
                .map(acc -> KpiRubricDistributionDTO.builder()
                        .bandId(acc.id)
                        .bandLabel(acc.label)
                        .minScore(acc.minScore)
                        .maxScore(acc.maxScore)
                        .selfSelections(acc.self)
                        .managerSelections(acc.manager)
                        .totalSelections(acc.self + acc.manager)
                        .build())
                .sorted(Comparator.comparing(KpiRubricDistributionDTO::getBandLabel, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public List<KpiCompletionStatusDTO> getCompletionStatus(KpiReportFilterDTO filter) {
        List<EmployeeAppraisal> appraisals = filteredAppraisals(filter);
        long total = appraisals.size();
        Map<String, List<EmployeeAppraisal>> grouped = appraisals.stream()
                .collect(Collectors.groupingBy(this::completionKey, LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream()
                .map(rows -> {
                    Employee sample = rows.get(0).getEmployee();
                    Department department = sample != null ? sample.getDepartment() : null;
                    AppraisalStatus status = rows.get(0).getStatus();
                    return KpiCompletionStatusDTO.builder()
                            .departmentId(department != null ? department.getId() : null)
                            .departmentName(department != null ? department.getName() : "Unassigned")
                            .managerId(department != null && department.getDepartmentHead() != null ? department.getDepartmentHead().getId() : null)
                            .managerName(department != null && department.getDepartmentHead() != null ? department.getDepartmentHead().getFullName() : null)
                            .status(status)
                            .count(rows.size())
                            .completionRate(rate(rows.size(), total))
                            .build();
                })
                .toList();
    }

    public byte[] export(String type, String format, KpiReportFilterDTO filter) {
        String csv = switch ((type == null ? "executive" : type).toLowerCase(Locale.ROOT)) {
            case "templates" -> templatesCsv();
            case "assignments" -> assignmentsCsv();
            case "lines" -> linesCsv(filter);
            case "departments" -> departmentsCsv(filter);
            default -> executiveCsv(filter);
        };
        if ("pdf".equalsIgnoreCase(format)) {
            return executivePdf(csv);
        }
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    private List<EmployeeAppraisal> filteredAppraisals(KpiReportFilterDTO filter) {
        return appraisalRepository.findAll().stream()
                .filter(appraisal -> filter == null || filter.getCycleId() == null || appraisal.getCycle() != null && Objects.equals(appraisal.getCycle().getId(), filter.getCycleId()))
                .filter(appraisal -> filter == null || filter.getEmployeeId() == null || appraisal.getEmployee() != null && Objects.equals(appraisal.getEmployee().getId(), filter.getEmployeeId()))
                .filter(appraisal -> filter == null || filter.getDepartmentId() == null || appraisal.getEmployee() != null && appraisal.getEmployee().getDepartment() != null && Objects.equals(appraisal.getEmployee().getDepartment().getId(), filter.getDepartmentId()))
                .filter(appraisal -> filter == null || filter.getJobStepId() == null || appraisal.getEmployee() != null && appraisal.getEmployee().getJobStep() != null && Objects.equals(appraisal.getEmployee().getJobStep().getId(), filter.getJobStepId()))
                .filter(appraisal -> filter == null || filter.getCompletionStatus() == null || appraisal.getStatus() == filter.getCompletionStatus())
                .toList();
    }

    private long countTemplates(List<KpiScorecardTemplate> templates, KpiScorecardTemplateStatus status) {
        return templates.stream().filter(template -> template.getStatus() == status).count();
    }

    private long countActiveAssignments(List<KpiTemplateAssignment> assignments, Function<KpiTemplateAssignment, Object> scope) {
        return assignments.stream().filter(KpiTemplateAssignment::isActive).filter(assignment -> scope.apply(assignment) != null).count();
    }

    private List<KpiScorecardMonitoringDTO.TemplateActivity> recentImports(List<KpiScorecardTemplate> templates) {
        return templates.stream()
                .filter(template -> "EXCEL_IMPORT".equalsIgnoreCase(template.getSourceType()))
                .sorted(Comparator.comparing(KpiScorecardTemplate::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(template -> KpiScorecardMonitoringDTO.TemplateActivity.builder()
                        .templateId(template.getId())
                        .templateName(template.getName())
                        .status(template.getStatus() != null ? template.getStatus().name() : null)
                        .actor(template.getImportedBy())
                        .activityAt(format(template.getCreatedAt()))
                        .build())
                .toList();
    }

    private List<KpiScorecardMonitoringDTO.TemplateActivity> recentVersions() {
        return versionRepository.findAll().stream()
                .sorted(Comparator.comparing(KpiScorecardTemplateVersion::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(version -> KpiScorecardMonitoringDTO.TemplateActivity.builder()
                        .templateId(version.getTemplate().getId())
                        .templateName(version.getTemplateName())
                        .versionNumber(version.getVersionNumber())
                        .status("PUBLISHED")
                        .actor(version.getPublishedBy())
                        .activityAt(format(version.getCreatedAt()))
                        .build())
                .toList();
    }

    private List<KpiScorecardMonitoringDTO.TemplateIssue> templateIssues(List<KpiScorecardTemplate> templates) {
        List<KpiScorecardMonitoringDTO.TemplateIssue> issues = new ArrayList<>();
        for (KpiScorecardTemplate template : templates) {
            List<KpiScorecardTemplateItem> items = templateItemRepository.findByTemplate_IdOrderBySortOrderAscIdAsc(template.getId());
            if (items.isEmpty()) {
                issues.add(issue(template, "ERROR", "Template has no KPI items."));
                continue;
            }
            BigDecimal rootWeight = items.stream()
                    .filter(item -> item.getParentItem() == null)
                    .map(KpiScorecardTemplateItem::getWeight)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (template.getStatus() == KpiScorecardTemplateStatus.PUBLISHED && rootWeight.compareTo(BigDecimal.ONE) != 0) {
                issues.add(issue(template, "ERROR", "Published root weights total " + rootWeight + " instead of 1.00."));
            } else if (rootWeight.compareTo(BigDecimal.ONE) != 0) {
                issues.add(issue(template, "WARNING", "Draft root weights total " + rootWeight + "."));
            }
        }
        return issues;
    }

    private KpiScorecardMonitoringDTO.TemplateIssue issue(KpiScorecardTemplate template, String severity, String message) {
        return KpiScorecardMonitoringDTO.TemplateIssue.builder()
                .templateId(template.getId())
                .templateName(template.getName())
                .severity(severity)
                .message(message)
                .build();
    }

    private boolean isPendingSelfReview(EmployeeAppraisal appraisal) {
        return appraisal.getStatus() == null || appraisal.getStatus() == AppraisalStatus.DRAFT;
    }

    private boolean isPendingManagerReview(EmployeeAppraisal appraisal) {
        return appraisal.getStatus() == AppraisalStatus.SELF_REVIEW_SUBMITTED
                || appraisal.getStatus() == AppraisalStatus.UNDER_MANAGER_REVIEW;
    }

    private boolean hasScore(EmployeeAppraisal appraisal) {
        return score(appraisal) != null;
    }

    private BigDecimal score(EmployeeAppraisal appraisal) {
        if (appraisal.getFinalScore() != null) return appraisal.getFinalScore();
        if (appraisal.getKpiScore() != null) return appraisal.getKpiScore();
        return appraisal.getManagerScore();
    }

    private BigDecimal averageScore(List<EmployeeAppraisal> appraisals) {
        BigDecimal total = appraisals.stream()
                .map(this::score)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return divide(total, appraisals.stream().filter(this::hasScore).count());
    }

    private BigDecimal divide(BigDecimal total, long count) {
        if (count == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private AppraisalCycle resolveCycle(KpiReportFilterDTO filter) {
        if (filter != null && filter.getCycleId() != null) {
            return cycleRepository.findById(filter.getCycleId()).orElse(null);
        }
        return cycleRepository.findAll().stream()
                .max(Comparator.comparing(AppraisalCycle::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private List<KpiCyclePerformanceReportDTO.Performer> performers(List<EmployeeAppraisal> rows, Comparator<EmployeeAppraisal> comparator) {
        return rows.stream()
                .sorted(comparator)
                .limit(10)
                .map(appraisal -> {
                    Employee employee = appraisal.getEmployee();
                    return KpiCyclePerformanceReportDTO.Performer.builder()
                            .appraisalId(appraisal.getId())
                            .employeeId(employee != null ? employee.getId() : null)
                            .employeeName(employee != null ? employee.getFullName() : "Unknown")
                            .departmentName(employee != null && employee.getDepartment() != null ? employee.getDepartment().getName() : null)
                            .jobStepName(employee != null && employee.getJobStep() != null ? employee.getJobStep().getName() : null)
                            .score(score(appraisal))
                            .build();
                })
                .toList();
    }

    private List<KpiCyclePerformanceReportDTO.CycleTrend> cycleTrend(KpiReportFilterDTO filter) {
        return cycleRepository.findAll().stream()
                .sorted(Comparator.comparing(AppraisalCycle::getYear).thenComparing(AppraisalCycle::getQuarter))
                .map(cycle -> {
                    KpiReportFilterDTO cycleFilter = new KpiReportFilterDTO();
                    if (filter != null) {
                        cycleFilter.setDepartmentId(filter.getDepartmentId());
                        cycleFilter.setJobStepId(filter.getJobStepId());
                        cycleFilter.setEmployeeId(filter.getEmployeeId());
                        cycleFilter.setCompletionStatus(filter.getCompletionStatus());
                    }
                    cycleFilter.setCycleId(cycle.getId());
                    List<EmployeeAppraisal> rows = filteredAppraisals(cycleFilter);
                    List<EmployeeAppraisal> completed = rows.stream().filter(this::hasScore).toList();
                    return KpiCyclePerformanceReportDTO.CycleTrend.builder()
                            .cycleId(cycle.getId())
                            .cycleName(cycle.getName())
                            .averageScore(averageScore(completed))
                            .completionRate(rate(completed.size(), rows.size()))
                            .build();
                })
                .toList();
    }

    private KpiDefinition topLevelPerspective(KpiDefinition kpi) {
        KpiDefinition cursor = kpi;
        while (cursor.getParentDefinition() != null) {
            cursor = cursor.getParentDefinition();
        }
        return cursor;
    }

    private String completionKey(EmployeeAppraisal appraisal) {
        Employee employee = appraisal.getEmployee();
        Long departmentId = employee != null && employee.getDepartment() != null ? employee.getDepartment().getId() : 0L;
        return departmentId + "|" + appraisal.getStatus();
    }

    private String templatesCsv() {
        StringBuilder csv = new StringBuilder("id,name,role,status,totalWeight,source,importedBy\n");
        templateRepository.findAll().forEach(template -> csv.append(row(
                template.getId(), template.getName(), template.getRoleName(), template.getStatus(),
                template.getTotalWeight(), template.getSourceType(), template.getImportedBy()
        )));
        return csv.toString();
    }

    private String assignmentsCsv() {
        StringBuilder csv = new StringBuilder("id,template,version,scope,owner,validFrom,validTo,active,appliedBy\n");
        assignmentRepository.findAll().forEach(assignment -> csv.append(row(
                assignment.getId(),
                assignment.getTemplate() != null ? assignment.getTemplate().getName() : "",
                assignment.getTemplateVersion() != null ? assignment.getTemplateVersion().getVersionNumber() : "",
                assignmentScope(assignment),
                assignmentOwner(assignment),
                assignment.getValidFrom(),
                assignment.getValidTo(),
                assignment.isActive(),
                assignment.getAppliedBy()
        )));
        return csv.toString();
    }

    private String linesCsv(KpiReportFilterDTO filter) {
        List<Long> appraisalIds = filteredAppraisals(filter).stream().map(EmployeeAppraisal::getId).toList();
        StringBuilder csv = new StringBuilder("appraisalId,employee,kpi,weight,selfScore,managerScore,finalScore,weightedScore,evidenceUrl\n");
        lineRepository.findAll().stream()
                .filter(line -> appraisalIds.contains(line.getAppraisal().getId()))
                .forEach(line -> csv.append(row(
                        line.getAppraisal().getId(),
                        line.getAppraisal().getEmployee() != null ? line.getAppraisal().getEmployee().getFullName() : "",
                        line.getKpi() != null ? line.getKpi().getName() : "",
                        line.getWeight(), line.getSelfScore(), line.getManagerScore(), line.getFinalScore(), line.getWeightedScore(), line.getEvidenceUrl()
                )));
        return csv.toString();
    }

    private String departmentsCsv(KpiReportFilterDTO filter) {
        StringBuilder csv = new StringBuilder("department,totalAppraisals,completedAppraisals,averageScore,completionRate\n");
        getDepartmentPerformance(filter).forEach(row -> csv.append(row(
                row.getDepartmentName(), row.getTotalAppraisals(), row.getCompletedAppraisals(), row.getAverageScore(), row.getCompletionRate()
        )));
        return csv.toString();
    }

    private String executiveCsv(KpiReportFilterDTO filter) {
        KpiCyclePerformanceReportDTO report = getPerformanceReport(filter);
        StringBuilder csv = new StringBuilder("metric,value\n");
        csv.append(row("cycle", report.getCycleName()));
        csv.append(row("averageScore", report.getAverageScore()));
        csv.append(row("totalAppraisals", report.getTotalAppraisals()));
        csv.append(row("completedAppraisals", report.getCompletedAppraisals()));
        csv.append(row("completionRate", report.getCompletionRate()));
        csv.append("\nTop Performers\nemployee,department,jobStep,score\n");
        report.getTopPerformers().forEach(row -> csv.append(row(row.getEmployeeName(), row.getDepartmentName(), row.getJobStepName(), row.getScore())));
        return csv.toString();
    }

    private byte[] executivePdf(String content) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                stream.newLineAtOffset(48, 740);
                stream.showText("KPI Executive Dashboard Export");
                stream.setFont(PDType1Font.HELVETICA, 9);
                stream.setLeading(12);
                stream.newLine();
                int lines = 0;
                for (String line : content.split("\\R")) {
                    if (lines++ > 52) break;
                    stream.newLine();
                    stream.showText(safePdf(line));
                }
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate KPI report PDF.", ex);
        }
    }

    private String row(Object... values) {
        return java.util.Arrays.stream(values)
                .map(value -> value == null ? "" : value.toString())
                .map(this::csv)
                .collect(Collectors.joining(",")) + "\n";
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String assignmentScope(KpiTemplateAssignment assignment) {
        if (assignment.getEmployee() != null) return "employee";
        if (assignment.getJobStep() != null) return "jobStep";
        if (assignment.getDepartment() != null) return "department";
        return "";
    }

    private String assignmentOwner(KpiTemplateAssignment assignment) {
        if (assignment.getEmployee() != null) return assignment.getEmployee().getFullName();
        if (assignment.getJobStep() != null) return assignment.getJobStep().getName();
        if (assignment.getDepartment() != null) return assignment.getDepartment().getName();
        return "";
    }

    private String format(java.time.LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private String safePdf(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static class PerspectiveAccumulator {
        private final Long id;
        private final String name;
        private BigDecimal scoreTotal = BigDecimal.ZERO;
        private BigDecimal weightedTotal = BigDecimal.ZERO;
        private long scoredLines;

        private PerspectiveAccumulator(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class RubricAccumulator {
        private final Long id;
        private final String label;
        private final BigDecimal minScore;
        private final BigDecimal maxScore;
        private long self;
        private long manager;

        private RubricAccumulator(com.justjava.humanresource.kpi.entity.KpiScoringRubricBand band) {
            this.id = band.getId();
            this.label = band.getLabel();
            this.minScore = band.getMinScore();
            this.maxScore = band.getMaxScore();
        }
    }
}
