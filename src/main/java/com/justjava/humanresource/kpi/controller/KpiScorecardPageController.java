package com.justjava.humanresource.kpi.controller;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.dto.KpiScorecardImportResultDTO;
import com.justjava.humanresource.kpi.dto.KpiTemplateAssignmentCommand;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplate;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateItem;
import com.justjava.humanresource.kpi.service.KpiScorecardImportService;
import com.justjava.humanresource.kpi.service.KpiScorecardTemplateService;
import com.justjava.humanresource.kpi.service.KpiDefinitionService;
import com.justjava.humanresource.kpi.service.KpiScoringRubricService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class KpiScorecardPageController {

    private final KpiScorecardTemplateService templateService;
    private final KpiScorecardImportService importService;
    private final KpiDefinitionService kpiDefinitionService;
    private final KpiScoringRubricService rubricService;
    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;
    private final DepartmentRepository departmentRepository;

    @GetMapping("/kpi/scorecards")
    @PreAuthorize("@kpiAuthorization.canManageKpi(authentication)")
    public String scorecards(Model model) {
        List<KpiScorecardTemplate> templates = templateService.getManageableTemplates();
        Map<Long, List<KpiScorecardTemplateItem>> itemsByTemplate = new LinkedHashMap<>();
        for (KpiScorecardTemplate template : templates) {
            itemsByTemplate.put(template.getId(), templateService.getTemplateItems(template.getId()));
        }

        model.addAttribute("templates", templates);
        model.addAttribute("activeTemplates", templateService.getActiveTemplates());
        model.addAttribute("itemsByTemplate", itemsByTemplate);
        model.addAttribute("employees", employeeRepository.findAll());
        model.addAttribute("jobSteps", jobStepRepository.findAllWithJobGrade());
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("kpiDefinitions", kpiDefinitionService.getAll());
        model.addAttribute("rubrics", rubricService.getActiveRubrics());
        model.addAttribute("title", "Balanced Scorecards");
        model.addAttribute("subTitle", "Manage balanced-scorecard templates and assignments");
        return "kpi/scorecards";
    }

    @GetMapping("/mobile/kpi-scorecards")
    @PreAuthorize("@kpiAuthorization.canManageKpi(authentication)")
    public String mobileScorecards(Model model) {
        List<KpiScorecardTemplate> templates = templateService.getActiveTemplates();
        Map<Long, List<KpiScorecardTemplateItem>> itemsByTemplate = new LinkedHashMap<>();
        for (KpiScorecardTemplate template : templates) {
            itemsByTemplate.put(template.getId(), templateService.getTemplateItems(template.getId()));
        }
        model.addAttribute("templates", templates);
        model.addAttribute("itemsByTemplate", itemsByTemplate);
        model.addAttribute("assignmentHistory", templateService.getAssignmentHistory());
        model.addAttribute("title", "Balanced Scorecards");
        return "mobile/kpi-scorecards";
    }

    @PostMapping("/kpi/scorecards/import")
    @PreAuthorize("@kpiAuthorization.canManageKpi(authentication)")
    public String importScorecard(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String templateName,
            @RequestParam(required = false) String roleName,
            RedirectAttributes redirectAttributes
    ) {
        try {
            KpiScorecardImportResultDTO result = importService.importBalancedScorecard(file, templateName, roleName);
            redirectAttributes.addFlashAttribute(
                    "scorecardMessage",
                    "Imported " + result.getTemplateName() + " with " + result.getTemplateItemsCreated() + " template items."
            );
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("scorecardError", ex.getMessage());
        }
        return "redirect:/kpi/scorecards";
    }

    @PostMapping("/kpi/scorecards/apply")
    @PreAuthorize("@kpiAuthorization.canManageKpi(authentication)")
    public String applyTemplate(
            KpiTemplateAssignmentCommand command,
            RedirectAttributes redirectAttributes
    ) {
        try {
            templateService.applyTemplate(command);
            redirectAttributes.addFlashAttribute("scorecardMessage", "Scorecard template applied successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("scorecardError", ex.getMessage());
        }
        return "redirect:/kpi/scorecards";
    }
}
