package com.justjava.humanresource.leave.service;

import com.justjava.humanresource.approval.enums.ApprovalModuleType;
import com.justjava.humanresource.approval.model.ApprovalContext;
import com.justjava.humanresource.approval.model.ApproverRef;
import com.justjava.humanresource.approval.service.ApprovalRouteResolverFactory;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.leave.dto.LeaveRequestCreateCommand;
import com.justjava.humanresource.leave.dto.LeaveRequestDetailDTO;
import com.justjava.humanresource.leave.dto.PublicHolidayCreateCommand;
import com.justjava.humanresource.leave.entity.LeaveApprovalStep;
import com.justjava.humanresource.leave.entity.LeaveRequest;
import com.justjava.humanresource.leave.entity.PublicHoliday;
import com.justjava.humanresource.leave.enums.LeaveApprovalDecision;
import com.justjava.humanresource.leave.enums.LeaveRequestStatus;
import com.justjava.humanresource.leave.repository.LeaveApprovalStepRepository;
import com.justjava.humanresource.leave.repository.LeaveRequestRepository;
import com.justjava.humanresource.leave.repository.PublicHolidayRepository;
import com.justjava.humanresource.utils.AfterCommitExecutor;
import com.justjava.humanresource.utils.LeaveEmailService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveWorkflowService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveApprovalStepRepository leaveApprovalStepRepository;
    private final EmployeeService employeeService;
    private final AuthenticationManager authenticationManager;
    private final ApprovalRouteResolverFactory routeResolverFactory;
    private final RuntimeService runtimeService;
    private final FlowableTaskService flowableTaskService;
    private final LeaveEmailService leaveEmailService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PublicHolidayRepository publicHolidayRepository;

    @Transactional
    public LeaveRequest submitLeaveRequest(LeaveRequestCreateCommand command) {
        String applicantEmail = getCurrentUserEmail();
        Long applicantId = null;
        Long leaveRequestId = null;

        try {
            log.info(
                    "Submitting leave request. applicantEmail={}, leaveType={}, startDate={}, endDate={}, standInEmployeeId={}",
                    applicantEmail,
                    command != null ? command.getLeaveType() : null,
                    command != null ? command.getStartDate() : null,
                    command != null ? command.getEndDate() : null,
                    command != null ? command.getStandInEmployeeId() : null
            );

            Employee applicant = getCurrentEmployee();
            applicantId = applicant != null ? applicant.getId() : null;
            log.info("Resolved leave applicant. applicantEmail={}, applicantId={}", applicantEmail, applicantId);

            validateLeaveSubmission(command, applicant);
            log.info("Leave request validation passed. applicantId={}, standInEmployeeId={}", applicantId, command.getStandInEmployeeId());

            Employee standIn = employeeService.getById(command.getStandInEmployeeId());
            log.info("Resolved leave stand-in employee. applicantId={}, standInEmployeeId={}", applicantId, standIn.getId());

            LeaveRequest request = new LeaveRequest();
            request.setEmployeeId(applicant.getId());
            request.setStandInEmployeeId(command.getStandInEmployeeId());
            request.setLeaveType(command.getLeaveType().trim());
            request.setStartDate(command.getStartDate());
            request.setEndDate(command.getEndDate());
            request.setTotalDays(countWeekdays(command.getStartDate(), command.getEndDate()));
            request.setReason(command.getReason());
            request.setStatus(LeaveRequestStatus.SUBMITTED);

            request = leaveRequestRepository.save(request);
            leaveRequestId = request.getId();
            log.info("Saved submitted leave request. leaveRequestId={}, applicantId={}", leaveRequestId, applicantId);

            ApprovalContext context = ApprovalContext.builder()
                    .moduleType(ApprovalModuleType.LEAVE)
                    .requesterEmployeeId(applicant.getId())
                    .moduleRefId(request.getId())
                    .build();

            log.info("Resolving leave approval route. leaveRequestId={}, applicantId={}", leaveRequestId, applicantId);
            List<ApproverRef> route = routeResolverFactory.getResolver(context).resolveApprovers(context);
            if (route.isEmpty()) {
                throw new IllegalStateException("No line manager route found for this employee.");
            }

            List<String> approverIds = route.stream()
                    .map(r -> String.valueOf(r.getEmployeeId()))
                    .toList();
            log.info("Resolved leave approval route. leaveRequestId={}, applicantId={}, approverIds={}",
                    leaveRequestId, applicantId, approverIds);

            request.setTotalApprovalLevels(route.size());
            request.setCurrentApprovalLevel(1);
            request.setStatus(LeaveRequestStatus.IN_APPROVAL);
            request = leaveRequestRepository.save(request);

            Map<String, Object> vars = new HashMap<>();
            vars.put("leaveRequestId", request.getId());
            vars.put("requesterEmployeeId", request.getEmployeeId());
            vars.put("approverIds", approverIds);
            vars.put("totalLevels", approverIds.size());

            log.info("Starting leave approval workflow. leaveRequestId={}, businessKey={}",
                    leaveRequestId, "LEAVE_" + request.getId());
            var processInstance = runtimeService.startProcessInstanceByKey(
                    "leaveApprovalProcess",
                    "LEAVE_" + request.getId(),
                    vars
            );
            request.setWorkflowInstanceId(processInstance.getProcessInstanceId());
            LeaveRequest saved = leaveRequestRepository.save(request);
            log.info("Leave request submitted successfully. leaveRequestId={}, workflowInstanceId={}",
                    saved.getId(), saved.getWorkflowInstanceId());

            Long firstApproverId = route.get(0).getEmployeeId();
            afterCommitExecutor.runAfterCommit(() -> {
                leaveEmailService.notifyLeaveSubmitted(saved, firstApproverId);
                leaveEmailService.notifyPendingApproval(saved, firstApproverId);
            });

            return saved;
        } catch (Exception ex) {
            log.error(
                    "Leave request submission failed. applicantEmail={}, applicantId={}, leaveRequestId={}, leaveType={}, startDate={}, endDate={}, standInEmployeeId={}",
                    applicantEmail,
                    applicantId,
                    leaveRequestId,
                    command != null ? command.getLeaveType() : null,
                    command != null ? command.getStartDate() : null,
                    command != null ? command.getEndDate() : null,
                    command != null ? command.getStandInEmployeeId() : null,
                    ex
            );
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getMyLeaveRequests() {
        if (isHrUser()) {
            return leaveRequestRepository.findAllByOrderByCreatedAtDesc();
        }
        Employee current = tryGetCurrentEmployee();
        if (current == null) {
            return List.of();
        }
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(current.getId());
    }

    @Transactional(readOnly = true)
    public List<FlowableTaskDTO> getMyPendingApprovalTasks() {
        if (isHrUser()) {
            return flowableTaskService.getTasksByProcessDefinition("leaveApprovalProcess");
        }
        Employee current = getCurrentEmployee();
        return flowableTaskService.getTasksForAssignee(
                String.valueOf(current.getId()),
                "leaveApprovalProcess"
        );
    }

    @Transactional(readOnly = true)
    public List<Employee> getStandInOptions() {
        Employee current = getCurrentEmployee();
        return employeeService.getAllEmployees().stream()
                .filter(e -> e.getId() != null && !e.getId().equals(current.getId()))
                .map(dto -> employeeService.getById(dto.getId()))
                .toList();
    }

    @Transactional
    public void approveTask(String taskId, String comment) {
        completeApprovalTask(taskId, "APPROVE", comment);
    }

    @Transactional
    public void rejectTask(String taskId, String comment) {
        completeApprovalTask(taskId, "REJECT", comment);
    }

    @Transactional(readOnly = true)
    public List<LeaveApprovalStep> getApprovalSteps(Long leaveRequestId) {
        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new IllegalStateException("Leave request not found"));
        Employee current = getCurrentEmployee();
        if (!request.getEmployeeId().equals(current.getId())) {
            throw new IllegalStateException("You are not authorized to view this leave approval timeline.");
        }
        return leaveApprovalStepRepository.findByLeaveRequestIdOrderBySequenceNoAsc(leaveRequestId);
    }

    @Transactional(readOnly = true)
    public LeaveRequestDetailDTO getLeaveRequestDetail(Long leaveRequestId) {
        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new IllegalStateException("Leave request not found"));

        if (!isHrUser()) {
            Employee current = getCurrentEmployee();
            if (!canEmployeeViewLeaveRequest(request, current)) {
                throw new IllegalStateException("You are not authorized to view this leave request.");
            }
        }

        Employee requester = employeeService.getById(request.getEmployeeId());
        Employee standIn = employeeService.getById(request.getStandInEmployeeId());

        return LeaveRequestDetailDTO.builder()
                .id(request.getId())
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalDays(request.getTotalDays())
                .reason(request.getReason())
                .status(request.getStatus())
                .currentApprovalLevel(request.getCurrentApprovalLevel())
                .totalApprovalLevels(request.getTotalApprovalLevels())
                .createdAt(request.getCreatedAt())
                .requesterId(requester.getId())
                .requesterName(requester.getFullName())
                .standInId(standIn.getId())
                .standInName(standIn.getFullName())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PublicHoliday> getPublicHolidays() {
        return publicHolidayRepository.findAllByOrderByDateAsc();
    }

    @Transactional
    public PublicHoliday addPublicHoliday(PublicHolidayCreateCommand command) {
        if (!isHrUser()) {
            throw new IllegalStateException("You are not authorized to manage public holidays.");
        }
        if (command.getDate() == null) {
            throw new IllegalArgumentException("Holiday date is required.");
        }
        if (command.getName() == null || command.getName().isBlank()) {
            throw new IllegalArgumentException("Holiday name is required.");
        }
        if (publicHolidayRepository.existsByDate(command.getDate())) {
            throw new IllegalArgumentException("A holiday is already defined for this date.");
        }
        PublicHoliday holiday = new PublicHoliday();
        holiday.setDate(command.getDate());
        holiday.setName(command.getName().trim());
        return publicHolidayRepository.save(holiday);
    }

    @Transactional
    public void deletePublicHoliday(Long id) {
        if (!isHrUser()) {
            throw new IllegalStateException("You are not authorized to manage public holidays.");
        }
        publicHolidayRepository.deleteById(id);
    }

    private int countWeekdays(LocalDate start, LocalDate end) {
        Set<LocalDate> holidays = publicHolidayRepository.findByDateBetween(start, end)
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        int weekdays = 0;
        LocalDate date = start;
        while (!date.isAfter(end)) {
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date)) {
                weekdays++;
            }
            date = date.plusDays(1);
        }
        return weekdays;
    }

    private void completeApprovalTask(String taskId, String decision, String comment) {
        Employee current = getCurrentEmployee();
        if (!flowableTaskService.isTaskAssignedTo(taskId, String.valueOf(current.getId()))) {
            throw new IllegalStateException("Task is not assigned to current user.");
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("approvalDecision", decision);
        vars.put("approvalComment", comment);
        vars.put("approvalActorId", current.getId());
        flowableTaskService.completeTask(taskId, vars);
    }

    private void validateLeaveSubmission(LeaveRequestCreateCommand command, Employee applicant) {
        if (command.getLeaveType() == null || command.getLeaveType().isBlank()) {
            throw new IllegalArgumentException("Leave type is required.");
        }
        if (command.getStartDate() == null || command.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }
        if (command.getEndDate().isBefore(command.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }
        if (command.getStandInEmployeeId() == null) {
            throw new IllegalArgumentException("Stand-in colleague is required.");
        }
        if (applicant.getId().equals(command.getStandInEmployeeId())) {
            throw new IllegalArgumentException("Employee cannot choose self as stand-in.");
        }
    }

    private boolean canEmployeeViewLeaveRequest(LeaveRequest request, Employee current) {
        if (current == null || current.getId() == null) {
            return false;
        }

        if (Objects.equals(request.getEmployeeId(), current.getId())) {
            return true;
        }

        boolean hasPendingTask = flowableTaskService
                .getTasksForAssignee(String.valueOf(current.getId()), "leaveApprovalProcess")
                .stream()
                .anyMatch(task -> {
                    Map<String, Object> variables = task.getVariables();
                    Object taskLeaveRequestId = variables != null ? variables.get("leaveRequestId") : null;
                    return taskLeaveRequestId != null
                            && String.valueOf(taskLeaveRequestId).equals(String.valueOf(request.getId()));
                });

        if (hasPendingTask) {
            return true;
        }

        return leaveApprovalStepRepository.findByLeaveRequestIdOrderBySequenceNoAsc(request.getId())
                .stream()
                .anyMatch(step -> Objects.equals(step.getApproverEmployeeId(), current.getId()));
    }

    private Employee getCurrentEmployee() {
        String email = getCurrentUserEmail();
        return employeeService.getByEmail(email);
    }

    private String getCurrentUserEmail() {
        return (String) authenticationManager.get("email");
    }

    private Employee tryGetCurrentEmployee() {
        try {
            String email = (String) authenticationManager.get("email");
            if (email == null || email.isBlank()) {
                return null;
            }
            return employeeService.getByEmail(email);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isHrUser() {
        return authenticationManager.isHumanResource()
                || authenticationManager.isJobHR()
                || authenticationManager.isAdmin()
                || authenticationManager.isRestrictedHr();
    }
}