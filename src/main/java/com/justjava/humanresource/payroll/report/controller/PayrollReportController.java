package com.justjava.humanresource.payroll.report.controller;

import com.justjava.humanresource.payroll.report.dto.PayrollVarianceDTO;
import com.justjava.humanresource.payroll.report.services.PayrollVarianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST endpoints for payroll reports.
 *
 * <p>Base path: {@code /api/payroll/reports}</p>
 */
@RestController
@RequestMapping("/api/payroll/reports")
@RequiredArgsConstructor
public class PayrollReportController {

    private final PayrollVarianceService varianceService;

    /**
     * Payroll Variance Report.
     *
     * <p>Returns one row per employee who has a POSTED run in the requested
     * period, showing current vs. previous-period gross/deductions/net,
     * component-level breakdown, and detected reasons for any variance.</p>
     *
     * <p>All employees are included — even those with zero net variance.</p>
     *
     * <p>Example:</p>
     * <pre>
     * GET /api/payroll/reports/variance
     *     ?companyId=1
     *     &amp;periodStart=2026-05-01
     *     &amp;periodEnd=2026-05-31
     * </pre>
     *
     * @param companyId    required — scopes the report to this company
     * @param periodStart  required — first day of the current period (yyyy-MM-dd)
     * @param periodEnd    required — last day of the current period (yyyy-MM-dd)
     * @param employeeId   optional — drill down to a single employee
     * @param departmentId optional — filter by department
     */
    @GetMapping("/variance")
    public ResponseEntity<List<PayrollVarianceDTO>> getVarianceReport(
            @RequestParam                                            Long      companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(required = false)                          Long      employeeId,
            @RequestParam(required = false)                          Long      departmentId) {

        if (periodEnd.isBefore(periodStart)) {
            return ResponseEntity.badRequest().build();
        }

        List<PayrollVarianceDTO> report = varianceService.generateVarianceReport(
                companyId,
                periodStart,
                periodEnd,
                employeeId,
                departmentId
        );

        return ResponseEntity.ok(report);
    }
}
