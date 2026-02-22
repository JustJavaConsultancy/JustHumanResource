package com.justjava.humanresource.kpi.dto;


import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;

public class AppraisalTaskViewDTO {

    private FlowableTaskDTO task;
    private EmployeeAppraisal appraisal;

    public AppraisalTaskViewDTO(FlowableTaskDTO task, EmployeeAppraisal appraisal) {
        this.task = task;
        this.appraisal = appraisal;
    }

    public FlowableTaskDTO getTask() {
        return task;
    }

    public EmployeeAppraisal getAppraisal() {
        return appraisal;
    }
}