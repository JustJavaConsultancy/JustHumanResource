package com.justjava.humanresource.leave;

import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.leave.dto.LeaveApprovalActionCommand;
import com.justjava.humanresource.leave.dto.LeaveRequestCreateCommand;
import com.justjava.humanresource.leave.entity.LeaveApprovalStep;
import com.justjava.humanresource.leave.entity.LeaveRequest;
import com.justjava.humanresource.leave.service.LeaveWorkflowService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveWorkflowService leaveWorkflowService;
    private final EmployeeService employeeService;

    @GetMapping("/leave")
    public String leavePage(Model model) {
        model.addAttribute("title","Leave Management");
        model.addAttribute("subTitle","Approve and manage employee leave requests");
        model.addAttribute("employees", employeeService.getAllEmployees());
        return "leave/main";
    }

    @PostMapping("/leave/requests")
    @ResponseBody
    public LeaveRequest submitLeaveRequest(@RequestBody LeaveRequestCreateCommand command) {
        return leaveWorkflowService.submitLeaveRequest(command);
    }

    @GetMapping("/leave/requests/me")
    @ResponseBody
    public List<LeaveRequest> myLeaveRequests() {
        return leaveWorkflowService.getMyLeaveRequests();
    }

    @GetMapping("/leave/stand-in-options")
    @ResponseBody
    public List<EmployeeDTO> getStandInOptions() {
        return employeeService.getAllEmployees();
    }

    @GetMapping("/leave/approvals/tasks")
    @ResponseBody
    public List<FlowableTaskDTO> getMyApprovalTasks() {
        return leaveWorkflowService.getMyPendingApprovalTasks();
    }

    @PostMapping("/leave/approvals/approve")
    @ResponseBody
    public void approveLeave(@RequestBody LeaveApprovalActionCommand command) {
        leaveWorkflowService.approveTask(command.getTaskId(), command.getComment());
    }

    @PostMapping("/leave/approvals/reject")
    @ResponseBody
    public void rejectLeave(@RequestBody LeaveApprovalActionCommand command) {
        leaveWorkflowService.rejectTask(command.getTaskId(), command.getComment());
    }

    @GetMapping("/leave/requests/{id}/steps")
    @ResponseBody
    public List<LeaveApprovalStep> getLeaveSteps(@PathVariable Long id) {
        return leaveWorkflowService.getApprovalSteps(id);
    }
}
