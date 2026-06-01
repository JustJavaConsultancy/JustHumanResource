package com.justjava.humanresource.leave.service;

import com.justjava.humanresource.approval.enums.ApprovalModuleType;
import com.justjava.humanresource.approval.model.ApprovalContext;
import com.justjava.humanresource.approval.model.ApproverRef;
import com.justjava.humanresource.approval.service.ApprovalRouteResolverFactory;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.leave.dto.LeaveRequestCreateCommand;
import com.justjava.humanresource.leave.entity.LeaveApprovalStep;
import com.justjava.humanresource.leave.entity.LeaveRequest;
import com.justjava.humanresource.leave.enums.LeaveApprovalDecision;
import com.justjava.humanresource.leave.enums.LeaveRequestStatus;
import com.justjava.humanresource.leave.repository.LeaveApprovalStepRepository;
import com.justjava.humanresource.leave.repository.LeaveRequestRepository;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeaveWorkflowService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveApprovalStepRepository leaveApprovalStepRepository;
    private final EmployeeService employeeService;
    private final AuthenticationManager authenticationManager;
    private final ApprovalRouteResolverFactory routeResolverFactory;
    private final RuntimeService runtimeService;
    private final FlowableTaskService flowableTaskService;

    @Transactional
    public LeaveRequest submitLeaveRequest(LeaveRequestCreateCommand command) {
        Employee applicant = getCurrentEmployee();
        validateLeaveSubmission(command, applicant);

        employeeService.getById(command.getStandInEmployeeId());

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(applicant.getId());
        request.setStandInEmployeeId(command.getStandInEmployeeId());
        request.setLeaveType(command.getLeaveType().trim());
        request.setStartDate(command.getStartDate());
        request.setEndDate(command.getEndDate());
        request.setTotalDays((int) ChronoUnit.DAYS.between(command.getStartDate(), command.getEndDate()) + 1);
        request.setReason(command.getReason());
        request.setStatus(LeaveRequestStatus.SUBMITTED);

        request = leaveRequestRepository.save(request);

        ApprovalContext context = ApprovalContext.builder()
                .moduleType(ApprovalModuleType.LEAVE)
                .requesterEmployeeId(applicant.getId())
                .moduleRefId(request.getId())
                .build();

        List<ApproverRef> route = routeResolverFactory.getResolver(context).resolveApprovers(context);
        if (route.isEmpty()) {
            throw new IllegalStateException("No line manager route found for this employee.");
        }

        request.setTotalApprovalLevels(route.size());
        request.setCurrentApprovalLevel(1);
        request.setStatus(LeaveRequestStatus.IN_APPROVAL);
        request = leaveRequestRepository.save(request);

        List<String> approverIds = route.stream()
                .map(r -> String.valueOf(r.getEmployeeId()))
                .toList();

        Map<String, Object> vars = new HashMap<>();
        vars.put("leaveRequestId", request.getId());
        vars.put("requesterEmployeeId", request.getEmployeeId());
        vars.put("approverIds", approverIds);
        vars.put("totalLevels", approverIds.size());

        var processInstance = runtimeService.startProcessInstanceByKey(
                "leaveApprovalProcess",
                "LEAVE_" + request.getId(),
                vars
        );
        request.setWorkflowInstanceId(processInstance.getProcessInstanceId());
        return leaveRequestRepository.save(request);
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

    private Employee getCurrentEmployee() {
        String email = (String) authenticationManager.get("email");
        return employeeService.getByEmail(email);
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
