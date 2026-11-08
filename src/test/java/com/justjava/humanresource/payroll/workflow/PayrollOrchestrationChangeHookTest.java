package com.justjava.humanresource.payroll.workflow;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollAccountingChangeDetector;
import com.justjava.humanresource.kpi.service.KpiMeasurementService;
import com.justjava.humanresource.loan.service.LoanPayrollDeductionService;
import com.justjava.humanresource.loan.service.LoanPayrollDisbursementService;
import com.justjava.humanresource.orgStructure.entity.Company;
import com.justjava.humanresource.payroll.calculation.PayGroupResolutionService;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import com.justjava.humanresource.payroll.service.PayrollAuditService;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.service.PayrollRunKpiSnapshotService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.repositories.PensionSchemeRepository;
import com.justjava.humanresource.payroll.statutory.service.PayeCalculatorService;
import com.justjava.humanresource.payroll.statutory.service.PensionCalculatorService;
import com.justjava.humanresource.payroll.workflow.impl.PayrollOrchestrationServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;




@ExtendWith(MockitoExtension.class)
class PayrollOrchestrationChangeHookTest {

    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PayrollSetupService payrollSetupService;
    @Mock private PayrollLineItemRepository payrollLineItemRepository;
    @Mock private PayGroupResolutionService payGroupResolutionService;
    @Mock private PayeCalculatorService payeCalculatorService;
    @Mock private PensionSchemeRepository pensionSchemeRepository;
    @Mock private PensionCalculatorService pensionCalculatorService;
    @Mock private EmployeePositionHistoryService employeePositionHistoryService;
    @Mock private PayrollPeriodService payrollPeriodService;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private PayrollAccountingChangeDetector payrollAccountingChangeDetector;
    @Mock private KpiMeasurementService kpiMeasurementService;
    @Mock private PayrollRunKpiSnapshotService payrollRunKpiSnapshotService;
    @Mock private PayrollAuditService payrollAuditService;
    @Mock private LoanPayrollDeductionService loanPayrollDeductionService;
    @Mock private LoanPayrollDisbursementService loanPayrollDisbursementService;

    @InjectMocks
    private PayrollOrchestrationServiceImpl service;

    @Test
    void finalizingPayrollRunInvokesClosedPeriodChangeDetector() {
        Long runId = 51L;
        Long employeeId = 71L;
        Long companyId = 91L;
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        Company company = new Company();
        company.setId(companyId);
        Department department = new Department();
        department.setCompany(company);
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setDepartment(department);
        PayrollRun run = new PayrollRun();
        run.setId(runId);
        run.setEmployee(employee);
        run.setPayrollDate(end);
        run.setPeriodStart(start);
        run.setPeriodEnd(end);
        run.setStatus(PayrollRunStatus.IN_PROGRESS);
        run.setGrossPay(new BigDecimal("100.00"));
        run.setTotalDeductions(BigDecimal.ZERO);
        run.setNetPay(new BigDecimal("100.00"));

        when(payrollRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(payrollRunRepository.findTopByEmployee_IdAndPayrollYearAndStatusOrderByPayrollDateDesc(
                employeeId, 2026, PayrollRunStatus.POSTED)).thenReturn(Optional.empty());
        when(payrollLineItemRepository.sumTaxableEarnings(runId)).thenReturn(BigDecimal.ZERO);
        when(payrollLineItemRepository.sumByRunAndCode(runId, "PAYE")).thenReturn(BigDecimal.ZERO);

        service.finalizePayroll(runId);

        verify(payrollAccountingChangeDetector).detect(runId, companyId, start, end);
    }
}
