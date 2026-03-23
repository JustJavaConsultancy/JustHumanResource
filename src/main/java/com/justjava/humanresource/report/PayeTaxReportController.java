package com.justjava.humanresource.report;

import com.justjava.humanresource.payroll.report.dto.PayeReportDTO;
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
public class PayeTaxReportController {

    private static final Long COMPANY_ID = 1L;

    private static final LocalDate START_DATE = LocalDate.of(LocalDate.now().getYear(), 1, 1);
    private static final LocalDate END_DATE   = LocalDate.of(LocalDate.now().getYear(), 12, 31);

    private final PayrollRunService payrollRunService;

    public PayeTaxReportController(PayrollRunService payrollRunService) {
        this.payrollRunService = payrollRunService;
    }

    @GetMapping("/paye-report")
    public String payeReport(Model model) {

        // ── Fetch PAYE records (nulls are possible per DTO contract) ──────────────
        List<PayeReportDTO> payeReport =
                payrollRunService.getPayeReport(COMPANY_ID, START_DATE, END_DATE);

        model.addAttribute("payeReport", payeReport);
        model.addAttribute("totalRecords", payeReport.size());

        // ── Page-level totals (null-safe) ─────────────────────────────────────────
        BigDecimal totalTaxableIncome = payeReport.stream()
                .map(p -> p.getTaxableIncome() != null ? p.getTaxableIncome() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaye = payeReport.stream()
                .map(p -> p.getPaye() != null ? p.getPaye() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalYtdPaye = payeReport.stream()
                .map(p -> p.getYtdPaye() != null ? p.getYtdPaye() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("totalTaxableIncome", totalTaxableIncome);
        model.addAttribute("totalPaye",          totalPaye);
        model.addAttribute("totalYtdPaye",        totalYtdPaye);

        // ── Date / period metadata ────────────────────────────────────────────────
        model.addAttribute("reportYear", LocalDate.now().getYear());
        model.addAttribute("startDate",  START_DATE);
        model.addAttribute("endDate",    END_DATE);
        model.addAttribute("title", "Reporting");
        model.addAttribute("subTitle", "A detailed overview of financial activities for the current year.");

        return "payroll/paye-report";   // → templates/payroll/paye-report.html
    }
}