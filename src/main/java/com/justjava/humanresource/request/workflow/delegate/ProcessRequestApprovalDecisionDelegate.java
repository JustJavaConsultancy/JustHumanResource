package com.justjava.humanresource.request.workflow.delegate;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.*;
import com.justjava.humanresource.request.repository.*;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.*;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
@Component("processRequestApprovalDecisionDelegate") @RequiredArgsConstructor public class ProcessRequestApprovalDecisionDelegate implements JavaDelegate {
 private final WorkflowRequestRepository requests; private final WorkflowRequestApprovalStepRepository steps; private final WorkflowRequestActivityRepository activities;
 public void execute(DelegateExecution e){Long id=((Number)e.getVariable("workflowRequestId")).longValue();int level=((Number)e.getVariable("currentLevel")).intValue();int base=((Number)e.getVariable("approvalSequenceBase")).intValue();String action=String.valueOf(e.getVariable("approvalDecision"));Long actor=((Number)e.getVariable("approvalActorId")).longValue();WorkflowRequestApprovalStep step=steps.findByWorkflowRequestIdAndSequenceNo(id,base+level).orElseThrow();if(!step.getApproverEmployeeId().equals(actor))throw new IllegalStateException("Approval actor does not match configured approver.");step.setDecision(switch(action){case "APPROVE"->ApprovalDecision.APPROVED;case "REJECT"->ApprovalDecision.REJECTED;case "RETURN"->ApprovalDecision.RETURNED;default->throw new IllegalArgumentException("Unknown approval decision: "+action);});step.setComments((String)e.getVariable("approvalComment"));step.setDecisionAt(LocalDateTime.now());step.setFlowableTaskId((String)e.getVariable("flowableTaskId"));steps.save(step);WorkflowRequest r=requests.findById(id).orElseThrow();if("APPROVE".equals(action)){List<?> approvers=(List<?>)e.getVariable("approverIds");boolean more=level<approvers.size();e.setVariable("hasMoreApprovers",more);if(more){int next=level+1;e.setVariable("currentLevel",next);e.setVariable("currentApproverId",String.valueOf(approvers.get(next-1)));r.setCurrentApprovalLevel(next);requests.save(r);}}else e.setVariable("hasMoreApprovers",false);record(id,action,actor);}
 private void record(Long id,String action,Long actor){WorkflowRequestActivity a=new WorkflowRequestActivity();a.setWorkflowRequestId(id);a.setActivityType(switch(action){case "APPROVE"->RequestActivityType.APPROVED;case "REJECT"->RequestActivityType.REJECTED;default->RequestActivityType.RETURNED;});a.setDescription("Approval decision: "+action);a.setActorEmployeeId(actor);activities.save(a);}
}
