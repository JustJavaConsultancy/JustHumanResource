package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KpiScorecardMonitoringDTO {
    private long draftTemplates;
    private long publishedTemplates;
    private long archivedTemplates;
    private long activeEmployeeAssignments;
    private long activeJobStepAssignments;
    private long activeDepartmentAssignments;
    private long pendingSelfReview;
    private long pendingManagerReview;
    private List<TemplateActivity> recentlyImportedTemplates;
    private List<TemplateActivity> recentlyPublishedVersions;
    private List<TemplateIssue> templatesWithValidationIssues;

    @Getter
    @Builder
    public static class TemplateActivity {
        private Long templateId;
        private String templateName;
        private Integer versionNumber;
        private String status;
        private String actor;
        private String activityAt;
    }

    @Getter
    @Builder
    public static class TemplateIssue {
        private Long templateId;
        private String templateName;
        private String severity;
        private String message;
    }
}
