package com.justjava.humanresource.report;

import com.justjava.humanresource.payroll.report.dto.PensionReportDTO;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/reporting")
public class PensionReportController {

    private static final Long COMPANY_ID = 1L;

    private static final LocalDate START_DATE = LocalDate.of(LocalDate.now().getYear(), 1, 1);
    private static final LocalDate END_DATE   = LocalDate.of(LocalDate.now().getYear(), 12, 31);

    private final PayrollRunService payrollRunService;

    public PensionReportController(PayrollRunService payrollRunService) {
        this.payrollRunService = payrollRunService;
    }

    @GetMapping("/pension-report")
    public String pensionReport(Model model) {

        // ── Fetch pension records ─────────────────────────────────────────────────
        List<PensionReportDTO> pensionReport =
                payrollRunService.getPensionReport(COMPANY_ID, START_DATE, END_DATE);

        model.addAttribute("pensionReport", pensionReport);

        // ── Aggregate totals for the summary cards (null-safe) ───────────────────
        // employeeContribution / employerContribution may be null per DTO contract,
        // so we coerce nulls to ZERO before reducing to avoid NullPointerException.
        BigDecimal totalEmployeeContribution = pensionReport.stream()
                .map(p -> p.getEmployeeContribution() != null
                        ? p.getEmployeeContribution() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEmployerContribution = pensionReport.stream()
                .map(p -> p.getEmployerContribution() != null
                        ? p.getEmployerContribution() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLiability = totalEmployeeContribution.add(totalEmployerContribution);

        model.addAttribute("totalEmployeeContribution", totalEmployeeContribution);
        model.addAttribute("totalEmployerContribution", totalEmployerContribution);
        model.addAttribute("totalLiability",            totalLiability);
        model.addAttribute("totalRecords",              pensionReport.size());

        // ── Date / period metadata ────────────────────────────────────────────────
        model.addAttribute("reportYear",  LocalDate.now().getYear());
        model.addAttribute("startDate",   START_DATE);
        model.addAttribute("endDate",     END_DATE);
        model.addAttribute("title", "Reporting");
        model.addAttribute("subTitle", "A detailed overview of financial activities for the current year.");
        return "payroll/pension-report";   // → templates/payroll/pension-report.html
    }
}