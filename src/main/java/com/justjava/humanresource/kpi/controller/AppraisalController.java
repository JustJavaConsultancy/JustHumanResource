package com.justjava.humanresource.kpi.controller;

import com.justjava.humanresource.kpi.dto.EmployeeAppraisalDTO;
import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.repositories.AppraisalCycleRepository;
import com.justjava.humanresource.kpi.service.AppraisalService;
import com.justjava.humanresource.workflow.dto.CompleteTaskRequest;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/appraisals")
@RequiredArgsConstructor
public class AppraisalController {

    private final AppraisalService appraisalService;
    private final AppraisalCycleRepository cycleRepository;
    private final FlowableTaskService flowableTaskService;

    /* ============================================================
       1️⃣ CREATE APPRAISAL CYCLE
       ============================================================ */

    @PostMapping("/cycles")
    public AppraisalCycle createCycle(
            @RequestBody CreateCycleRequest request
    ) {
        return appraisalService.createAppraisalCycle(
                request.getYear(),
                request.getQuarter()
        );
    }

    /* ============================================================
       2️⃣ GET ALL CYCLES
       ============================================================ */

    @GetMapping("/cycles")
    public List<AppraisalCycle> getAllCycles() {
        return cycleRepository.findAll();
    }
    @GetMapping("cycle/{id}")
    public AppraisalCycle getCycleById(@PathVariable Long id) {
        return cycleRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Cycle not found"));
    }

    /* ============================================================
       3️⃣ CREATE DRAFT APPRAISAL
       ============================================================ */

    @PostMapping("/draft")
    public EmployeeAppraisal createDraft(
            @RequestBody CreateDraftRequest request
    ) {

        AppraisalCycle cycle = cycleRepository
                .findById(request.getCycleId())
                .orElseThrow(() ->
                        new IllegalStateException("Cycle not found")
                );

        return appraisalService.createDraftAppraisal(
                request.getEmployeeId(),
                cycle
        );
    }

    /* ============================================================
       4️⃣ FINALIZE APPRAISAL
       ============================================================ */

    @PostMapping("/finalize")
    public EmployeeAppraisal finalizeAppraisal(
            @RequestBody FinalizeAppraisalRequest request
    ) {

        return appraisalService.finalizeAppraisal(
                request.getAppraisalId(),
                request.getManagerScore(),
                request.getManagerComment(),
                request.getSelfScore(),
                request.getSelfComment()
        );
    }

    /* ============================================================
       5️⃣ GET ALL ACTIVE APPRAISALS (DTO)
       ============================================================ */

    @GetMapping("/active")
    public List<EmployeeAppraisalDTO> getActiveAppraisals() {
        return appraisalService.getAllActiveAppraisals();
    }

        /* =====================================================
       WORKFLOW - GET MANAGER TASKS
       ===================================================== */

    @GetMapping("/tasks/manager/{username}")
    public List<FlowableTaskDTO> getManagerTasks(
            @PathVariable String username
    ) {

        return flowableTaskService.getTasksForAssignee(
                "mgr",
                "employeeAppraisalProcess"
        );
    }

    /* =====================================================
       WORKFLOW - GET SELF TASKS
       ===================================================== */

    @GetMapping("/tasks/self/{username}")
    public List<FlowableTaskDTO> getSelfTasks(
            @PathVariable String username
    ) {

        return flowableTaskService.getTasksForAssignee(
                username,
                "employeeAppraisalProcess"
        );
    }
    /* =====================================================
       WORKFLOW - COMPLETE TASK (SELF OR MANAGER)
       ===================================================== */

    @PostMapping("/tasks/complete")
    public void completeTask(
            @RequestBody CompleteTaskRequest request
    ) {
        flowableTaskService.completeTask(
                request.getTaskId(),
                request.getVariables()
        );
    }
    /* ============================================================
       REQUEST DTOs
       ============================================================ */

    public static class CreateCycleRequest {
        private int year;
        private int quarter;

        public int getYear() { return year; }
        public int getQuarter() { return quarter; }

        public void setYear(int year) { this.year = year; }
        public void setQuarter(int quarter) { this.quarter = quarter; }
    }

    public static class CreateDraftRequest {
        private Long employeeId;
        private Long cycleId;

        public Long getEmployeeId() { return employeeId; }
        public Long getCycleId() { return cycleId; }

        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    }

    public static class FinalizeAppraisalRequest {
        private Long appraisalId;
        private BigDecimal managerScore;
        private String managerComment;
        private BigDecimal selfScore;
        private String selfComment;
        public Long getAppraisalId() { return appraisalId; }
        public BigDecimal getManagerScore() { return managerScore; }
        public String getManagerComment() { return managerComment; }

        public void setAppraisalId(Long appraisalId) { this.appraisalId = appraisalId; }
        public void setManagerScore(BigDecimal managerScore) { this.managerScore = managerScore; }
        public void setManagerComment(String managerComment) { this.managerComment = managerComment; }

        public BigDecimal getSelfScore() {
            return selfScore;
        }

        public void setSelfScore(BigDecimal selfScore) {
            this.selfScore = selfScore;
        }

        public String getSelfComment() {
            return selfComment;
        }

        public void setSelfComment(String selfComment) {
            this.selfComment = selfComment;
        }
    }
}
