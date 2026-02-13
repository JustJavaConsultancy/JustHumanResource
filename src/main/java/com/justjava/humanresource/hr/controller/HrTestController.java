package com.justjava.humanresource.hr.controller;
import com.justjava.humanresource.hr.dto.CreateJobGradeWithStepsCommand;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.dto.PayGroupResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import com.justjava.humanresource.kpi.service.KpiDefinitionService;
import com.justjava.humanresource.kpi.service.KpiMeasurementService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hr/test")
@RequiredArgsConstructor
public class HrTestController {

    private final EmployeeOnboardingService onboardingService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final SetupService setupService;
    private final KpiDefinitionService kpiDefinitionService;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiMeasurementService kpiMeasurementService;

/*
    private final PromotionService promotionService;
    private final EmployeeTransferService transferService;

    private final LeaveApplicationService leaveApplicationService;
    private final LeaveTypeRepository leaveTypeRepository;
*/

    private final RuntimeService runtimeService;

    /* ============================================================
       SETUP ENDPOINTS
       ============================================================ */

    @PostMapping("/setup/department")
    public Department createDepartment(@RequestParam String name) {
        return setupService.createDepartment(name);
    }

/* ============================================================
   SETUP JOB GRADE WITH MULTIPLE JOB STEPS
   ============================================================ */

    @PostMapping("/setup/job-grade")
    public JobGradeResponseDTO createJobGradeWithSteps(
            @RequestBody CreateJobGradeWithStepsCommand command) {
        return setupService.createJobGradeWithSteps(command);
    }
    @PostMapping("/setup/pay-group")
    public PayGroupResponseDTO createPayGroup(
            @RequestBody CreatePayGroupCommand command) {
        return setupService.createPayGroup(command);
    }

/*
    @PostMapping("/setup/leave-type")
    public LeaveType createLeaveType(@RequestParam String code,
                                     @RequestParam String name,
                                     @RequestParam int entitlementDays,
                                     @RequestParam boolean paid,
                                     @RequestParam boolean requiresApproval) {

        LeaveType type = LeaveType.builder()
                .code(code)
                .name(name)
                .annualEntitlementDays(entitlementDays)
                .paid(paid)
                .requiresApproval(requiresApproval)
                .build();

        return leaveTypeRepository.save(type);
    }
*/

/*    @PostMapping("/setup/kpi")
    public KpiDefinition createKpi(@RequestParam String code,
                                   @RequestParam String name,
                                   @RequestParam BigDecimal targetValue) {

        KpiDefinition kpi = KpiDefinition.builder()
                .code(code)
                .name(name)
                .targetValue(targetValue)
                .active(true)
                .build();

        return kpiDefinitionService.create(kpi);
    }*/

    /* ============================================================
       EMPLOYEE ONBOARDING
       ============================================================ */

/*
    @PostMapping("/onboarding")
    public Object onboardEmployee(@RequestParam String firstName,
                                  @RequestParam String lastName,
                                  @RequestParam String email,
                                  @RequestParam Long departmentId,
                                  @RequestParam Long jobRoleId) {

        StartEmployeeOnboardingCommand cmd = new StartEmployeeOnboardingCommand();
        cmd.setFirstName(firstName);
        cmd.setLastName(lastName);
        cmd.setEmail(email);
        cmd.setDepartmentId(departmentId);
        cmd.setJobRoleId(jobRoleId);

        return onboardingService.startOnboarding(cmd, "TEST_ADMIN");
    }
*/

    /* ============================================================
       KPI MEASUREMENT
       ============================================================ */

/*
    @PostMapping("/kpi/measure")
    public KpiMeasurement recordKpi(@RequestParam Long employeeId,
                                    @RequestParam String kpiCode,
                                    @RequestParam BigDecimal actualValue,
                                    @RequestParam int year,
                                    @RequestParam int month) {

        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        KpiDefinition kpi = kpiDefinitionRepository.findByCode(kpiCode).orElseThrow();

        return kpiMeasurementService.recordMeasurement(
                employee,
                kpi,
                actualValue,
                YearMonth.of(year, month)
        );
    }
*/

    /* ============================================================
       APPRAISAL
       ============================================================ */

/*
    @PostMapping("/appraisal/start")
    public void startAppraisal(@RequestParam Long employeeId,
                               @RequestParam int year,
                               @RequestParam int month) {

        runtimeService.startProcessInstanceByKey(
                "employeeAppraisalProcess",
                Map.of(
                        "employeeId", employeeId,
                        "period", YearMonth.of(year, month)
                )
        );
    }
*/

    /* ============================================================
       LEAVE
       ============================================================ */

/*
    @PostMapping("/leave/apply")
    public LeaveRequest applyLeave(@RequestParam Long employeeId,
                                   @RequestParam String leaveCode,
                                   @RequestParam String startDate,
                                   @RequestParam String endDate,
                                   @RequestParam String reason) {

        Employee employee = employeeRepository.findById(employeeId).orElseThrow();

        return leaveApplicationService.applyForLeave(
                employee,
                leaveCode,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                reason
        );
    }
*/

    /* ============================================================
       PROMOTION
       ============================================================ */

/*
    @PostMapping("/promotion")
    public PromotionRequest promote(@RequestParam Long employeeId,
                                    @RequestParam Long newRoleId,
                                    @RequestParam BigDecimal appraisalScore,
                                    @RequestParam String justification) {

        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        JobRole newRole = jobRoleRepository.findById(newRoleId).orElseThrow();

        return promotionService.initiatePromotion(
                employee,
                newRole,
                appraisalScore,
                justification
        );
    }
*/

    /* ============================================================
       TRANSFER (LATERAL)
       ============================================================ */

/*    @PostMapping("/transfer")
    public EmployeeTransfer transfer(@RequestParam Long employeeId,
                                     @RequestParam Long departmentId,
                                     @RequestParam Long roleId,
                                     @RequestParam String effectiveDate,
                                     @RequestParam String reason) {

        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        Department dept = departmentRepository.findById(departmentId).orElseThrow();
        JobRole role = jobRoleRepository.findById(roleId).orElseThrow();

        return transferService.initiateTransfer(
                employee,
                dept,
                role,
                LocalDate.parse(effectiveDate),
                reason
        );
    }*/
}
