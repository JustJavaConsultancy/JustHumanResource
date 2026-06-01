package com.justjava.humanresource.payroll.report.controller;

import com.justjava.humanresource.hr.service.JobHrEmployeeAccessService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.report.dto.PayrollVarianceDTO;
import com.justjava.humanresource.payroll.report.services.PayrollVarianceService;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/reporting")
@RequiredArgsConstructor
public class VarianceReportController {

    private static final Long COMPANY_ID = 1L;

    private final PayrollVarianceService      payrollVarianceService;
    private final PayrollPeriodRepository     payrollPeriodRepository;
    private final JobHrEmployeeAccessService  jobHrEmployeeAccessService;
    private final EmployeeOnboardingService   employeeOnboardingService;

    @GetMapping("/variance")
    public String varianceReport(Model model) {

        // ── Resolve the most recently closed (or current open) period ────────────
        List<PayrollPeriod> periods =
                payrollPeriodRepository.findByCompanyIdOrderByPeriodStartDesc(COMPANY_ID);

        // Prefer the OPEN period; fall back to the most recently CLOSED one.
        Optional<PayrollPeriod> periodOpt = periods.stream()
                .filter(p -> p.getStatus() == PayrollPeriodStatus.OPEN)
                .findFirst();
        if (periodOpt.isEmpty()) {
            periodOpt = periods.stream()
                    .filter(p -> p.getStatus() == PayrollPeriodStatus.CLOSED)
                    .findFirst();
        }

        LocalDate periodStart = periodOpt.map(PayrollPeriod::getPeriodStart)
                .orElse(LocalDate.of(LocalDate.now().getYear(), 1, 1));
        LocalDate periodEnd   = periodOpt.map(PayrollPeriod::getPeriodEnd)
                .orElse(LocalDate.of(LocalDate.now().getYear(), 12, 31));

        // ── Generate variance data ────────────────────────────────────────────────
        List<PayrollVarianceDTO> report = payrollVarianceService.generateVarianceReport(
                COMPANY_ID, periodStart, periodEnd, null, null);

        // ── JobHR scoping ────────────────────────────────────────────────────────
        if (jobHrEmployeeAccessService.isJobHrScopedUser()) {
            Set<Long> scopedIds = employeeOnboardingService.getAllOnboardings()
                    .stream().map(e -> e.getId()).collect(Collectors.toSet());
            report = report.stream()
                    .filter(r -> scopedIds.contains(r.getEmployeeId()))
                    .collect(Collectors.toList());
        }

        // ── Aggregates ───────────────────────────────────────────────────────────
        long totalIncreased = report.stream()
                .filter(r -> r.getNetVariance() != null
                        && r.getNetVariance().compareTo(BigDecimal.ZERO) > 0)
                .count();
        long totalDecreased = report.stream()
                .filter(r -> r.getNetVariance() != null
                        && r.getNetVariance().compareTo(BigDecimal.ZERO) < 0)
                .count();
        long totalUnchanged = report.stream()
                .filter(r -> r.getNetVariance() == null
                        || r.getNetVariance().compareTo(BigDecimal.ZERO) == 0)
                .count();
        long amendmentCount = report.stream().filter(PayrollVarianceDTO::isAmendment).count();

        BigDecimal totalNetVariance = report.stream()
                .map(r -> r.getNetVariance() != null ? r.getNetVariance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Highest single net variance row (for insight card)
        PayrollVarianceDTO highestVariance = report.stream()
                .filter(r -> r.getNetVariance() != null)
                .max((a, b) -> a.getNetVariance().abs().compareTo(b.getNetVariance().abs()))
                .orElse(null);

        // Distinct reasons across all rows (for insight card)
        long distinctReasonCount = report.stream()
                .filter(r -> r.getReasons() != null)
                .flatMap(r -> r.getReasons().stream())
                .filter(reason -> !"No change".equals(reason))
                .distinct()
                .count();

        // ── Model attributes ─────────────────────────────────────────────────────
        model.addAttribute("varianceReport",      report);
        model.addAttribute("totalEmployees",      report.size());
        model.addAttribute("totalIncreased",      totalIncreased);
        model.addAttribute("totalDecreased",      totalDecreased);
        model.addAttribute("totalUnchanged",      totalUnchanged);
        model.addAttribute("amendmentCount",      amendmentCount);
        model.addAttribute("totalNetVariance",    totalNetVariance);
        model.addAttribute("highestVariance",     highestVariance);
        model.addAttribute("distinctReasonCount", distinctReasonCount);
        model.addAttribute("periodStart",         periodStart);
        model.addAttribute("periodEnd",           periodEnd);
        model.addAttribute("reportYear",          LocalDate.now().getYear());
        model.addAttribute("title",    "Reporting");
        model.addAttribute("subTitle", "Payroll variance analysis between current and previous period.");

        return "payroll/variance-report";
    }
}
