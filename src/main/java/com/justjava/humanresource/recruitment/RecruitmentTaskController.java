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
        if (!authenticationManager.isHumanResource() && !authenticationManager.isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Recruitment task access is required."));
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || task.getProcessDefinitionId() == null
                || (!task.getProcessDefinitionId().startsWith("candidateApplicationProcess:")
                && !task.getProcessDefinitionId().startsWith("recruitmentJobOpeningProcess:")
                && !task.getProcessDefinitionId().startsWith("employmentOfferProcess:"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recruitment task not found."));
        }
        Map<String, Object> variables = new HashMap<>(command);
        variables.put("flowableTaskId", taskId);
        taskService.complete(taskId, variables);
        return ResponseEntity.ok(Map.of("completed", true));
    }
}
