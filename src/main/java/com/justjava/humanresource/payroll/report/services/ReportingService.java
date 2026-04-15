package com.justjava.humanresource.payroll.report.services;

import com.justjava.humanresource.payroll.dto.EmployeeGroupedReportDTO;
import com.justjava.humanresource.payroll.dto.EmployeeReportItemDTO;
import com.justjava.humanresource.payroll.enums.EmployeeGroupBy;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class ReportingService {
    private final PayrollRunRepository payrollRunRepository;

    public List<EmployeeGroupedReportDTO> getGroupedReport(
            Long companyId,
            LocalDate start,
            LocalDate end,
            EmployeeGroupBy groupBy
    ) {

        List<EmployeeReportItemDTO> raw =
                payrollRunRepository.getEmployeeReportRaw(
                        companyId,
                        start,
                        end,
                        groupBy.name()
                );

        Map<String, EmployeeGroupedReportDTO> map = new LinkedHashMap<>();

        for (EmployeeReportItemDTO row : raw) {

            // ── Null-safe values from this row ──────────────────────────
            BigDecimal rowGross   = row.getGross()   != null ? row.getGross()   : BigDecimal.ZERO;
            BigDecimal rowNet     = row.getNet()     != null ? row.getNet()     : BigDecimal.ZERO;
            BigDecimal rowPaye    = row.getPaye()    != null ? row.getPaye()    : BigDecimal.ZERO;
            BigDecimal rowPension = row.getPension() != null ? row.getPension() : BigDecimal.ZERO;

            // Write safe values back onto the row so the template also gets clean data
            row.setGross(rowGross);
            row.setNet(rowNet);
            row.setPaye(rowPaye);
            row.setPension(rowPension);

            // ── Get or create the group ──────────────────────────────────
            EmployeeGroupedReportDTO group =
                    map.computeIfAbsent(row.getGroupName(), g -> {
                        EmployeeGroupedReportDTO dto = new EmployeeGroupedReportDTO();
                        dto.setGroupName(g);
                        dto.setEmployeeCount(0L);
                        dto.setTotalGross(BigDecimal.ZERO);
                        dto.setTotalDeductions(BigDecimal.ZERO);
                        dto.setTotalNet(BigDecimal.ZERO);
                        dto.setPaye(BigDecimal.ZERO);
                        dto.setPension(BigDecimal.ZERO);
                        return dto;
                    });

            // ── Add employee to group ────────────────────────────────────
            group.getEmployees().add(row);

            // ── Aggregates (all null-safe) ───────────────────────────────
            group.setEmployeeCount(group.getEmployeeCount() + 1);
            group.setTotalGross(group.getTotalGross().add(rowGross));
            group.setTotalNet(group.getTotalNet().add(rowNet));
            group.setPaye(group.getPaye().add(rowPaye));
            group.setPension(group.getPension().add(rowPension));

            BigDecimal deductions = rowPaye.add(rowPension);
            group.setTotalDeductions(group.getTotalDeductions().add(deductions));
        }

        return new ArrayList<>(map.values());
    }
}
// Sample Response
//[
//        {
//        "groupName": "Management",
//        "employeeCount": 3,
//        "totalGross": 1800000,
//        "totalDeductions": 270000,
//        "totalNet": 1530000,
//        "paye": 180000,
//        "pension": 90000,
//        "employees": [
//        {
//        "employeeId": 1,
//        "firstName": "John",
//        "secondName": "Doe",
//        "gross": 600000,
//        "net": 510000,
//        "paye": 60000,
//        "pension": 30000,
//        "groupName": "Management"
//        },
//        {
//        "employeeId": 2,
//        "firstName": "Jane",
//        "secondName": "Smith",
//        "gross": 700000,
//        "net": 595000,
//        "paye": 70000,
//        "pension": 35000,
//        "groupName": "Management"
//        },
//        {
//        "employeeId": 3,
//        "firstName": "Michael",
//        "secondName": "Johnson",
//        "gross": 500000,
//        "net": 425000,
//        "paye": 50000,
//        "pension": 25000,
//        "groupName": "Management"
//        }
//        ]
//        },
//        {
//        "groupName": "Operations",
//        "employeeCount": 2,
//        "totalGross": 900000,
//        "totalDeductions": 135000,
//        "totalNet": 765000,
//        "paye": 90000,
//        "pension": 45000,
//        "employees": [
//        {
//        "employeeId": 4,
//        "firstName": "Aisha",
//        "secondName": "Bello",
//        "gross": 400000,
//        "net": 340000,
//        "paye": 40000,
//        "pension": 20000,
//        "groupName": "Operations"
//        },
//        {
//        "employeeId": 5,
//        "firstName": "Chinedu",
//        "secondName": "Okafor",
//        "gross": 500000,
//        "net": 425000,
//        "paye": 50000,
//        "pension": 25000,
//        "groupName": "Operations"
//        }
//        ]
//        }
//        ]