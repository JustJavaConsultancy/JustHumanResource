package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class JobHrEmployeeAccessService {

    private final AuthenticationManager authenticationManager;
    private final EmployeeService employeeService;
    private final JobStepRepository jobStepRepository;

    public JobHrEmployeeAccessService(AuthenticationManager authenticationManager,
                                      EmployeeService employeeService,
                                      JobStepRepository jobStepRepository) {
        this.authenticationManager = authenticationManager;
        this.employeeService = employeeService;
        this.jobStepRepository = jobStepRepository;
    }

    public boolean isJobHrScopedUser() {
        return authenticationManager.isJobHR();
    }

    public Long getLoggedInJobGradeId() {
        String email = (String) authenticationManager.get("email");
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Unable to resolve logged-in user email.");
        }
        Employee actor = employeeService.getByEmail(email);
        Long jobGradeId = extractJobGradeId(actor);
        if (jobGradeId == null) {
            throw new AccessDeniedException("Logged-in jobHR user does not have a job grade.");
        }
        return jobGradeId;
    }

    public void assertCanAccessEmployee(Long employeeId) {
        if (!isJobHrScopedUser()) {
            return;
        }
        Employee target = employeeService.getById(employeeId);
        Long actorJobGradeId = getLoggedInJobGradeId();
        Long targetJobGradeId = extractJobGradeId(target);
        if (!Objects.equals(actorJobGradeId, targetJobGradeId)) {
            throw new AccessDeniedException("Access denied: employee is outside your job grade scope.");
        }
    }

    public List<Employee> filterEmployeesByScope(List<Employee> employees) {
        if (!isJobHrScopedUser()) {
            return employees;
        }
        Long actorJobGradeId = getLoggedInJobGradeId();
        return employees.stream()
                .filter(employee -> Objects.equals(actorJobGradeId, extractJobGradeId(employee)))
                .collect(Collectors.toList());
    }

    public void assertCanUseJobStep(Long jobStepId) {
        if (!isJobHrScopedUser() || jobStepId == null) {
            return;
        }
        JobStep jobStep = jobStepRepository.findById(jobStepId)
                .orElseThrow(() -> new AccessDeniedException("Job step not found for scoped access check."));
        Long requestedJobGradeId = jobStep.getJobGrade() != null ? jobStep.getJobGrade().getId() : null;
        if (!Objects.equals(getLoggedInJobGradeId(), requestedJobGradeId)) {
            throw new AccessDeniedException("Access denied: requested job step is outside your job grade scope.");
        }
    }

    private Long extractJobGradeId(Employee employee) {
        if (employee == null || employee.getJobStep() == null || employee.getJobStep().getJobGrade() == null) {
            return null;
        }
        return employee.getJobStep().getJobGrade().getId();
    }
}
