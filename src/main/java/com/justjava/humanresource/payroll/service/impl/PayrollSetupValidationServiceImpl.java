package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.repository.*;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.entity.PayrollReadinessResult;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.service.PayrollSetupValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollSetupValidationServiceImpl
        implements PayrollSetupValidationService {

    private final DepartmentRepository departmentRepository;
    private final PayGroupRepository payGroupRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeePositionHistoryRepository positionRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    //private final TaxConfigurationRepository taxConfigurationRepository;

/*
    @Override
    public PayrollReadinessResult validateOrganizationReadiness(YearMonth period) {

        List<String> missing = new ArrayList<>();

        //PayrollReadinessResult readinessResult =
        */
/* ========================================================
           1️⃣ PAYROLL PERIOD OPEN
           ======================================================== *//*


        boolean periodOpen =
                payrollPeriodRepository
                        .existsByYearAndMonthAndStatus(period.getYear(), period.getMonthValue(), PayrollPeriodStatus.OPEN);

        if (!periodOpen) {
            missing.add("Payroll period is not open for " + period);
        }

        */
/* ========================================================
           2️⃣ PAYE / TAX CONFIGURATION
           ======================================================== *//*


*/
/*
        boolean taxConfigured =
                taxConfigurationRepository.existsByActiveTrue();

        if (!taxConfigured) {
            missing.add("PAYE / Tax configuration is not set up");
        }
*//*


        */
/* ========================================================
           3️⃣ DEPARTMENT SETUP
           ======================================================== *//*


        if (departmentRepository.countByStatus(RecordStatus.ACTIVE) == 0) {
            missing.add("No active departments configured");
        }

        */
/* ========================================================
           4️⃣ PAYGROUP SETUP
           ======================================================== *//*


        if (payGroupRepository.countByStatus(RecordStatus.ACTIVE) == 0) {
            missing.add("No active PayGroups configured");
        }

        */
/* ========================================================
           5️⃣ ACTIVE EMPLOYEES HAVE ACTIVE POSITIONS
           ======================================================== *//*


        long activeEmployees =
                employeeRepository.countByEmploymentStatusActive();

        long employeesWithPositions =
                positionRepository.countByCurrentTrue();

        if (activeEmployees != employeesWithPositions) {
            missing.add("Some active employees do not have current active positions");
        }

        */
/* ========================================================
           6️⃣ AT LEAST ONE ACTIVE EMPLOYEE EXISTS
           ======================================================== *//*


        if (activeEmployees == 0) {
            missing.add("No active employees available for payroll");
        }

        return new PayrollReadinessResult((missing.isEmpty()),missing);
    }
*/
}
