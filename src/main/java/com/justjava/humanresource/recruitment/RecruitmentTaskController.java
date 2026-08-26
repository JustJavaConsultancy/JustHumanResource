package com.justjava.humanresource.recruitment;

import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController @RequestMapping("/api/recruitment/tasks") @RequiredArgsConstructor
public class RecruitmentTaskController {
    private final TaskService taskService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/{taskId}/complete")
    public ResponseEntity<?> complete(@PathVariable String taskId, @RequestBody Map<String, Object> command) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || task.getProcessDefinitionId() == null
                || (!task.getProcessDefinitionId().startsWith("candidateApplicationProcess:")
                && !task.getProcessDefinitionId().startsWith("recruitmentJobOpeningProcess:")
                && !task.getProcessDefinitionId().startsWith("employmentOfferProcess:"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recruitment task not found."));
        }
        if (!canComplete(task)) {
            return ResponseEntity.status(403).body(Map.of("error", "Recruitment task access is required."));
        }
        Map<String, Object> variables = new HashMap<>(command);
        variables.put("flowableTaskId", taskId);
        taskService.complete(taskId, variables);
        return ResponseEntity.ok(Map.of("completed", true));
    }

    private boolean canComplete(Task task) {
        if (authenticationManager.isAdmin() || authenticationManager.isHumanResource()) return true;
        if (task.getProcessDefinitionId().startsWith("employmentOfferProcess:")) {
            return authenticationManager.isFinancialOfficer();
        }
        if (task.getProcessDefinitionId().startsWith("recruitmentJobOpeningProcess:")) {
            Long currentEmployeeId = currentEmployeeId();
            return currentEmployeeId != null && String.valueOf(currentEmployeeId).equals(task.getAssignee());
        }
        return false;
    }

    private Long currentEmployeeId() {
        for (String claim : java.util.List.of("employeeId", "employee_id", "employeeNumber", "employee_number")) {
            Object value = authenticationManager.get(claim);
            if (value instanceof Number number) return number.longValue();
            if (value instanceof String text && !text.isBlank()) {
                try { return Long.valueOf(text); } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }
}
