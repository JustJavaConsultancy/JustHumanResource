package com.justjava.humanresource.kpi.controller;

import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.security.KpiAuthorizationService;
import com.justjava.humanresource.kpi.service.AppraisalService;
import com.justjava.humanresource.kpi.service.KpiAppraisalLineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class KpiAppraisalPageController {

    private final AppraisalService appraisalService;
    private final KpiAppraisalLineService lineService;
    private final KpiAuthorizationService authorizationService;

    @GetMapping("/kpi/appraisals/{id}/scorecard")
    @PreAuthorize("@kpiAuthorization.canViewAppraisal(#id, authentication)")
    public String scorecard(@PathVariable Long id, Model model, Authentication authentication) {
        EmployeeAppraisal appraisal = appraisalService.findAppraisalById(id)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found: " + id));

        lineService.createMissingLines(id);
        model.addAttribute("appraisal", appraisal);
        model.addAttribute("lines", lineService.getLines(id));
        model.addAttribute("bandsByLine", lineService.getRubricBandsByLine(id));
        model.addAttribute("summary", lineService.getPerspectiveSummary(id));
        model.addAttribute("weightedScore", lineService.calculateWeightedFinalScore(id));
        model.addAttribute("canManageScorecard", authorizationService.canManageKpi(authentication));
        model.addAttribute("title", "Balanced Scorecard Appraisal");
        model.addAttribute("subTitle", "Review scorecard lines and weighted performance");
        return "kpi/appraisal-scorecard";
    }
}
