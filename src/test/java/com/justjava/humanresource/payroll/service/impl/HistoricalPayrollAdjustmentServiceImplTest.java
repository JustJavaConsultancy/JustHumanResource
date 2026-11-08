package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollAccountingChangeDetector;
import com.justjava.humanresource.orgStructure.entity.Company;
import com.justjava.humanresource.payroll.dto.HistoricalPayrollAdjustmentCommand;
import com.justjava.humanresource.payroll.dto.HistoricalPayrollAdjustmentLineCommand;
import com.justjava.humanresource.payroll.dto.HistoricalPayrollPayItemDTO;
import com.justjava.humanresource.payroll.dto.PayrollOriginalVsAdjustedDTO;
import com.justjava.humanresource.payroll.dto.PayrollVersionHistoryDTO;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.entity.PaySlip;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.enums.PayrollRunType;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import com.justjava.humanresource.payroll.service.PayrollAuditService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;




@ExtendWith(MockitoExtension.class)
class HistoricalPayrollAdjustmentServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PayrollPeriodRepository payrollPeriodRepository;
    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private PayrollLineItemRepository payrollLineItemRepository;
    @Mock
    private PaySlipRepository paySlipRepository;
    @Mock
    private PayrollAccountingChangeDetector payrollAccountingChangeDetector;
    @Mock
    private PayrollAuditService payrollAuditService;

    private HistoricalPayrollAdjustmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HistoricalPayrollAdjustmentServiceImpl(
                employeeRepository,
                payrollPeriodRepository,
                payrollRunRepository,
                payrollLineItemRepository,
                paySlipRepository,
                payrollAccountingChangeDetector,
                payrollAuditService
        );
    }

    @Test
    void createPostedAmendmentCreatesNextVersionWithoutChangingOriginalRun() {
        PayrollPeriod period = closedPeriod();
        Employee employee = employee();
        PayrollRun original = postedRun(10L, employee, 1, PayrollRunType.ORIGINAL);
        List<PayrollLineItem> originalLines = List.of(
                line(original, employee, "BASIC", "Basic Salary", "1000.00", PayComponentType.EARNING, true),
                line(original, employee, "PAYE", "PAYE Tax", "100.00", PayComponentType.DEDUCTION, false)
        );

        when(payrollPeriodRepository.findById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(payrollRunRepository.findLatestPostedRunForEmployeeAndPeriod(5L, period.getPeriodStart(), period.getPeriodEnd()))
                .thenReturn(Optional.of(original));
        when(payrollRunRepository.findMaxVersionForEmployeeAndPeriod(5L, period.getPeriodStart(), period.getPeriodEnd()))
                .thenReturn(1);
        when(payrollLineItemRepository.findByPayrollRunId(10L)).thenReturn(originalLines);
        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(invocation -> {
            PayrollRun run = invocation.getArgument(0);
            run.setId(11L);
            return run;
        });
        when(paySlipRepository.existsByPayrollRunIdAndVersionNumber(11L, 2)).thenReturn(false);

        PayrollVersionHistoryDTO result = service.createPostedAmendment(command());

        assertEquals(11L, result.getPayrollRunId());
        assertEquals(2, result.getVersionNumber());
        assertEquals(PayrollRunType.AMENDMENT, result.getRunType());
        verify(payrollAccountingChangeDetector).detect(
                11L, period.getCompanyId(), original.getPeriodStart(), original.getPeriodEnd());

        ArgumentCaptor<PayrollRun> runCaptor = ArgumentCaptor.forClass(PayrollRun.class);
        verify(payrollRunRepository).save(runCaptor.capture());
        PayrollRun amendment = runCaptor.getValue();
        assertEquals(original, amendment.getParentRun());
        assertEquals(new BigDecimal("1200.00"), amendment.getGrossPay());
        assertEquals(new BigDecimal("100.00"), amendment.getTotalDeductions());
        assertEquals(new BigDecimal("1100.00"), amendment.getNetPay());
        assertEquals(new BigDecimal("1000.00"), original.getGrossPay());

        ArgumentCaptor<PayrollLineItem> lineCaptor = ArgumentCaptor.forClass(PayrollLineItem.class);
        verify(payrollLineItemRepository, org.mockito.Mockito.times(2)).save(lineCaptor.capture());
        PayrollLineItem adjustedBasic = lineCaptor.getAllValues().stream()
                .filter(line -> "BASIC".equals(line.getComponentCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("1200.00"), adjustedBasic.getAmount());
        assertEquals("Basic Salary", adjustedBasic.getDescription());
        assertEquals(PayComponentType.EARNING, adjustedBasic.getComponentType());
        assertEquals(true, adjustedBasic.isTaxable());
        assertEquals(true, adjustedBasic.isPensionable());
        assertEquals(true, adjustedBasic.isPartOfGross());

        ArgumentCaptor<PaySlip> slipCaptor = ArgumentCaptor.forClass(PaySlip.class);
        verify(paySlipRepository).save(slipCaptor.capture());
        assertEquals(2, slipCaptor.getValue().getVersionNumber());
        assertEquals(11L, slipCaptor.getValue().getPayrollRun().getId());

        verify(payrollAuditService).log(
                org.mockito.ArgumentMatchers.eq("PayrollRun"),
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq("HISTORICAL_PAYROLL_VERSION_CREATED"),
                org.mockito.ArgumentMatchers.contains("Correction approved")
        );
    }

    @Test
    void createPostedAmendmentDoesNotAddResidualAdjustmentToNetPay() {
        PayrollPeriod period = closedPeriod();
        Employee employee = employee();
        PayrollRun original = postedRun(10L, employee, 1, PayrollRunType.ORIGINAL);
        original.setGrossPay(new BigDecimal("1000.00"));
        original.setGrossDifference(new BigDecimal("500.00"));
        original.setTotalDeductions(new BigDecimal("100.00"));
        original.setNetPay(new BigDecimal("900.00"));

        List<PayrollLineItem> originalLines = List.of(
                line(original, employee, "BASIC", "Basic Salary", "1000.00", PayComponentType.EARNING, true),
                line(original, employee, "RESIDUAL", "Residual Adjustment", "500.00", PayComponentType.EARNING, false),
                line(original, employee, "PAYE", "PAYE Tax", "100.00", PayComponentType.DEDUCTION, false)
        );

        when(payrollPeriodRepository.findById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(payrollRunRepository.findLatestPostedRunForEmployeeAndPeriod(5L, period.getPeriodStart(), period.getPeriodEnd()))
                .thenReturn(Optional.of(original));
        when(payrollRunRepository.findMaxVersionForEmployeeAndPeriod(5L, period.getPeriodStart(), period.getPeriodEnd()))
                .thenReturn(1);
        when(payrollLineItemRepository.findByPayrollRunId(10L)).thenReturn(originalLines);
        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(invocation -> {
            PayrollRun run = invocation.getArgument(0);
            run.setId(11L);
            return run;
        });
        when(paySlipRepository.existsByPayrollRunIdAndVersionNumber(11L, 2)).thenReturn(false);

        PayrollVersionHistoryDTO result = service.createPostedAmendment(command());

        assertEquals(new BigDecimal("1100.00"), result.getNetPay());

        ArgumentCaptor<PayrollRun> runCaptor = ArgumentCaptor.forClass(PayrollRun.class);
        verify(payrollRunRepository).save(runCaptor.capture());
        PayrollRun amendment = runCaptor.getValue();
        assertEquals(new BigDecimal("1200.00"), amendment.getGrossPay());
        assertEquals(BigDecimal.ZERO, amendment.getNonGrossEarnings());
        assertEquals(new BigDecimal("500.00"), amendment.getGrossDifference());
        assertEquals(new BigDecimal("100.00"), amendment.getTotalDeductions());
        assertEquals(new BigDecimal("1100.00"), amendment.getNetPay());
    }

    @Test
    void rejectsHistoricalAdjustmentForOpenPeriod() {
        PayrollPeriod period = closedPeriod();
        period.setStatus(PayrollPeriodStatus.OPEN);
        when(payrollPeriodRepository.findById(20L)).thenReturn(Optional.of(period));

        assertThrows(IllegalStateException.class, () -> service.createPostedAmendment(command()));

        verify(payrollRunRepository, never()).save(any(PayrollRun.class));
    }

    @Test
    void compareOriginalToLatestShowsSummaryAndLineDifferences() {
        PayrollPeriod period = closedPeriod();
        Employee employee = employee();
        PayrollRun original = postedRun(10L, employee, 1, PayrollRunType.ORIGINAL);
        PayrollRun adjusted = postedRun(11L, employee, 2, PayrollRunType.AMENDMENT);
        adjusted.setGrossPay(new BigDecimal("1200.00"));
        adjusted.setNetPay(new BigDecimal("1100.00"));
        adjusted.setParentRun(original);

        when(payrollPeriodRepository.findById(20L)).thenReturn(Optional.of(period));
        when(payrollRunRepository.findOriginalPostedRunForEmployeeAndPeriod(5L, period.getPeriodStart(), period.getPeriodEnd()))
                .thenReturn(Optional.of(original));
        when(payrollRunRepository.findLatestPostedRunForEmployeeAndPeriod(5L, period.getPeriodStart(), period.getPeriodEnd()))
                .thenReturn(Optional.of(adjusted));
        when(payrollLineItemRepository.findByPayrollRunId(10L)).thenReturn(List.of(
                line(original, employee, "BASIC", "Basic Salary", "1000.00", PayComponentType.EARNING, true)
        ));
        when(payrollLineItemRepository.findByPayrollRunId(11L)).thenReturn(List.of(
                line(adjusted, employee, "BASIC", "Basic Salary", "1200.00", PayComponentType.EARNING, true)
        ));

        PayrollOriginalVsAdjustedDTO result = service.compareOriginalToLatest(5L, 20L);

        assertEquals(new BigDecimal("200.00"), result.getGrossDifference());
        assertEquals(new BigDecimal("200.00"), result.getNetDifference());
        assertEquals(1, result.getLineDifferences().size());
        assertEquals(new BigDecimal("200.00"), result.getLineDifferences().get(0).getDifferenceAmount());
    }

    @Test
    void getAdjustablePayItemsReturnsLatestRunLineMetadata() {
        PayrollPeriod period = closedPeriod();
        Employee employee = employee();
        PayrollRun original = postedRun(10L, employee, 1, PayrollRunType.ORIGINAL);

        when(payrollPeriodRepository.findById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(payrollRunRepository.findLatestPostedRunForEmployeeAndPeriod(5L, period.getPeriodStart(), period.getPeriodEnd()))
                .thenReturn(Optional.of(original));
        when(payrollLineItemRepository.findByPayrollRunId(10L)).thenReturn(List.of(
                line(original, employee, "BASIC", "Basic Salary", "1000.00", PayComponentType.EARNING, true)
        ));

        List<HistoricalPayrollPayItemDTO> items = service.getAdjustablePayItems(5L, 20L);

        assertEquals(1, items.size());
        assertEquals("BASIC", items.get(0).getComponentCode());
        assertEquals(new BigDecimal("1000.00"), items.get(0).getCurrentAmount());
        assertEquals(true, items.get(0).isTaxable());
        assertEquals(true, items.get(0).isPensionable());
        assertEquals(true, items.get(0).isPartOfGross());
    }

    @Test
    void rejectsUnknownComponentCode() {
        PayrollPeriod period = closedPeriod();
        Employee employee = employee();
        PayrollRun original = postedRun(10L, employee, 1, PayrollRunType.ORIGINAL);

        when(payrollPeriodRepository.findById(20L)).thenReturn(Optional.of(period));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(payrollRunRepository.findLatestPostedRunForEmployeeAndPeriod(5L, period.getPeriodStart(), period.getPeriodEnd()))
                .thenReturn(Optional.of(original));
        when(payrollLineItemRepository.findByPayrollRunId(10L)).thenReturn(List.of(
                line(original, employee, "BASIC", "Basic Salary", "1000.00", PayComponentType.EARNING, true)
        ));

        HistoricalPayrollAdjustmentCommand command = command();
        command.getLines().get(0).setComponentCode("UNKNOWN");

        assertThrows(IllegalStateException.class, () -> service.createPostedAmendment(command));
    }

    private static HistoricalPayrollAdjustmentCommand command() {
        HistoricalPayrollAdjustmentLineCommand line = new HistoricalPayrollAdjustmentLineCommand();
        line.setComponentCode("BASIC");
        line.setDescription("Basic Salary");
        line.setComponentType(PayComponentType.EARNING);
        line.setCorrectedAmount(new BigDecimal("1200.00"));
        line.setPartOfGross(true);
        line.setTaxable(true);
        line.setPensionable(true);

        HistoricalPayrollAdjustmentCommand command = new HistoricalPayrollAdjustmentCommand();
        command.setEmployeeId(5L);
        command.setPeriodId(20L);
        command.setReason("Correction approved by Finance");
        command.setLines(List.of(line));
        return command;
    }

    private static PayrollPeriod closedPeriod() {
        PayrollPeriod period = new PayrollPeriod();
        period.setId(20L);
        period.setCompanyId(1L);
        period.setPeriodStart(LocalDate.of(2026, 1, 1));
        period.setPeriodEnd(LocalDate.of(2026, 1, 31));
        period.setStatus(PayrollPeriodStatus.CLOSED);
        return period;
    }

    private static Employee employee() {
        Company company = new Company();
        company.setId(1L);
        company.setName("Acme");
        company.setCode("ACME");
        company.setStatus(RecordStatus.ACTIVE);

        Department department = new Department();
        department.setId(2L);
        department.setName("Finance");
        department.setCode("FIN001");
        department.setCompany(company);

        Employee employee = new Employee();
        employee.setId(5L);
        employee.setEmployeeNumber("EMP001");
        employee.setFirstName("Ada");
        employee.setLastName("Lovelace");
        employee.setDepartment(department);
        return employee;
    }

    private static PayrollRun postedRun(Long id, Employee employee, int version, PayrollRunType runType) {
        PayrollRun run = new PayrollRun();
        run.setId(id);
        run.setEmployee(employee);
        run.setPayrollDate(LocalDate.of(2026, 1, 31));
        run.setPeriodStart(LocalDate.of(2026, 1, 1));
        run.setPeriodEnd(LocalDate.of(2026, 1, 31));
        run.setStatus(PayrollRunStatus.POSTED);
        run.setRunType(runType);
        run.setVersionNumber(version);
        run.setFlowableProcessInstanceId("manual-test");
        run.setPayrollYear(2026);
        run.setGrossPay(new BigDecimal("1000.00"));
        run.setNonGrossEarnings(BigDecimal.ZERO);
        run.setTotalDeductions(new BigDecimal("100.00"));
        run.setNetPay(new BigDecimal("900.00"));
        run.setYtdGross(new BigDecimal("1000.00"));
        run.setYtdTaxable(new BigDecimal("1000.00"));
        run.setYtdDeductions(new BigDecimal("100.00"));
        run.setYtdNet(new BigDecimal("900.00"));
        run.setYtdPaye(new BigDecimal("100.00"));
        return run;
    }

    private static PayrollLineItem line(
            PayrollRun run,
            Employee employee,
            String code,
            String description,
            String amount,
            PayComponentType type,
            boolean partOfGross) {
        PayrollLineItem line = new PayrollLineItem();
        line.setPayrollRun(run);
        line.setEmployee(employee);
        line.setComponentCode(code);
        line.setDescription(description);
        line.setAmount(new BigDecimal(amount));
        line.setComponentType(type);
        line.setPartOfGross(partOfGross);
        line.setTaxable(partOfGross);
        line.setPensionable(partOfGross);
        return line;
    }
}
