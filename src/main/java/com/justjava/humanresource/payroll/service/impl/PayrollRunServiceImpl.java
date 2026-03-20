package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.dto.PayrollItemDTO;
import com.justjava.humanresource.payroll.dto.PayrollRunDTO;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollRunServiceImpl implements PayrollRunService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollLineItemRepository payrollLineItemRepository;

    @Override
    public PayrollRunDTO getPayrollRun(Long payrollRunId) {

        PayrollRun run = payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() -> new IllegalStateException("PayrollRun not found"));

        return buildDto(run);
    }

    @Override
    public PayrollRunDTO getEmployeePayrollRun(Long employeeId,Long companyId) {
        PayrollPeriod current =
                payrollPeriodRepository
                        .findByCompanyIdAndStatusIn(
                                companyId,
                                List.of(
                                        PayrollPeriodStatus.OPEN,
                                        PayrollPeriodStatus.LOCKED
                                )
                        )
                        .orElse(null);
        System.out.println("Current period for company " + companyId + ": " + (current != null ? current.getId() : "None"));

/*        if (current == null) {
            return Collections.emptyList();
        }*/
        PayrollRun run = payrollRunRepository
                .findTopByEmployeeIdAndPeriodEndOrderByVersionNumberDesc(
                        employeeId,
                        current.getPeriodEnd()
                )
                .orElseThrow(() -> new IllegalStateException("PayrollRun not found"));

        return buildDto(run);
    }
    @Override
    public List<PayrollRunDTO> getCurrentPeriodPayrollRuns(Long companyId) {

        PayrollPeriod current =
                payrollPeriodRepository
                        .findByCompanyIdAndStatusIn(
                                companyId,
                                List.of(
                                        PayrollPeriodStatus.OPEN,
                                        PayrollPeriodStatus.LOCKED
                                )
                        )
                        .orElse(null);

        if (current == null) {
            return Collections.emptyList();
        }

        return payrollRunRepository
                .findLatestPayrollRunsForPeriod(
                        companyId,
                        current.getPeriodStart(),
                        current.getPeriodEnd()
                );
    }
    @Override
    public List<PayrollRunDTO> getPayrollRunsForPeriod(
            Long companyId,
            LocalDate periodStart,
            LocalDate periodEnd) {

        List<PayrollRun> runs =
                payrollRunRepository
                        .findByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatus(
                                companyId,
                                periodStart,
                                periodEnd,
                                PayrollRunStatus.POSTED
                        );

        return runs.stream()
                .map(this::buildDto)
                .collect(Collectors.toList());
    }

    private PayrollRunDTO buildDto(PayrollRun run) {

        List<PayrollLineItem> items =
                payrollLineItemRepository.findByPayrollRunId(run.getId());

        List<PayrollItemDTO> allowances =
                items.stream()
                        .filter(i -> i.getComponentType() == PayComponentType.EARNING)
                        .map(this::mapItem)
                        .collect(Collectors.toList());


        List<PayrollItemDTO> deductions =
                items.stream()
                        .filter(i -> i.getComponentType() == PayComponentType.DEDUCTION)
                        .map(this::mapItem)
                        .collect(Collectors.toList());

        double paye =
                items.stream()
                        .filter(i -> "PAYE".equals(i.getComponentCode()))
                        .mapToDouble(i -> i.getAmount().doubleValue())
                        .sum();

        double pension =
                items.stream()
                        .filter(i -> "PENSION".equals(i.getComponentCode()))
                        .mapToDouble(i -> i.getAmount().doubleValue())
                        .sum();

        return PayrollRunDTO.builder()
                .payrollRunId(run.getId())
                .employeeId(run.getEmployee().getId())
                .employeeNumber(run.getEmployee().getEmployeeNumber())
                .employeeName(run.getEmployee().getFirstName() + " " + run.getEmployee().getLastName())
                .payrollDate(run.getPayrollDate())
                .periodStart(run.getPeriodStart())
                .periodEnd(run.getPeriodEnd())
                .grossPay(run.getGrossPay())
                .totalDeductions(run.getTotalDeductions())
                .netPay(run.getNetPay())
                .paye(java.math.BigDecimal.valueOf(paye))
                .pension(java.math.BigDecimal.valueOf(pension))
                .ytdGross(run.getYtdGross())
                .ytdNet(run.getYtdNet())
                .ytdPaye(run.getYtdPaye())
                .pensionScheme(run.getAppliedPensionSchemeName())
                .allowances(allowances)
                .deductions(deductions)
                .build();

    }

    private PayrollItemDTO mapItem(PayrollLineItem item) {

        return PayrollItemDTO.builder()
                .code(item.getComponentCode())
                .description(item.getDescription())
                .amount(item.getAmount())
                .taxable(item.isTaxable())
                .build();
    }
}