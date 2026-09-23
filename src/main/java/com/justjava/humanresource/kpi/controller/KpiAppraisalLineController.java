package com.justjava.humanresource.kpi.controller;

import com.justjava.humanresource.kpi.dto.BalancedScorecardSummaryDTO;
import com.justjava.humanresource.kpi.dto.KpiAppraisalLineCommand;
import com.justjava.humanresource.kpi.entity.KpiAppraisalLine;
import com.justjava.humanresource.kpi.service.KpiAppraisalLineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/appraisals")
@RequiredArgsConstructor
public class KpiAppraisalLineController {

    private final KpiAppraisalLineService lineService;

    @PostMapping("/{appraisalId}/lines/generate")
    @PreAuthorize("@kpiAuthorization.canManageAppraisalLines(#appraisalId, authentication)")
    public List<KpiAppraisalLine> generateLines(@PathVariable Long appraisalId) {
        return lineService.createMissingLines(appraisalId);
    }

    @GetMapping("/{appraisalId}/lines")
    @PreAuthorize("@kpiAuthorization.canReadAppraisalLines(#appraisalId, authentication)")
    public List<KpiAppraisalLine> getLines(@PathVariable Long appraisalId) {
        return lineService.getLines(appraisalId);
    }

    @PutMapping("/lines/{lineId}/self")
    @PreAuthorize("@kpiAuthorization.canUpdateSelfLine(#lineId, authentication)")
    public KpiAppraisalLine updateSelfLine(
            @PathVariable Long lineId,
            @RequestBody KpiAppraisalLineCommand command
    ) {
        return lineService.updateSelfInput(lineId, command);
    }

    @PutMapping("/lines/{lineId}/manager")
    @PreAuthorize("@kpiAuthorization.canUpdateManagerLine(#lineId, authentication)")
    public KpiAppraisalLine updateManagerLine(
            @PathVariable Long lineId,
            @RequestBody KpiAppraisalLineCommand command
    ) {
        return lineService.updateManagerInput(lineId, command);
    }

    @GetMapping("/{appraisalId}/lines/weighted-score")
    @PreAuthorize("@kpiAuthorization.canReadAppraisalLines(#appraisalId, authentication)")
    public BigDecimal getWeightedLineScore(@PathVariable Long appraisalId) {
        return lineService.calculateWeightedFinalScore(appraisalId);
    }

    @GetMapping("/{appraisalId}/balanced-scorecard-summary")
    @PreAuthorize("@kpiAuthorization.canReadAppraisalLines(#appraisalId, authentication)")
    public List<BalancedScorecardSummaryDTO> getBalancedScorecardSummary(@PathVariable Long appraisalId) {
        return lineService.getPerspectiveSummary(appraisalId);
    }
}
