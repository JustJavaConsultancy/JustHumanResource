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

            // add employee
            group.getEmployees().add(row);

            // aggregates
            group.setEmployeeCount(group.getEmployeeCount() + 1);
            group.setTotalGross(group.getTotalGross().add(row.getGross()));
            group.setTotalNet(group.getTotalNet().add(row.getNet()));
            group.setPaye(group.getPaye().add(row.getPaye()));
            group.setPension(group.getPension().add(row.getPension()));

            BigDecimal deductions =
                    row.getPaye().add(row.getPension());

            group.setTotalDeductions(
                    group.getTotalDeductions().add(deductions)
            );
        }

        return new ArrayList<>(map.values());
    }
}
