package com.justjava.humanresource.request.workflow.delegate;
import com.justjava.humanresource.request.entity.WorkflowRequestApprovalStep;
import com.justjava.humanresource.request.enums.ApprovalDecision;
import com.justjava.humanresource.request.repository.WorkflowRequestApprovalStepRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.*;
import org.springframework.stereotype.Component;
import java.util.List;
@Component("initializeRequestApprovalDelegate") @RequiredArgsConstructor public class InitializeRequestApprovalDelegate implements JavaDelegate {
 private final WorkflowRequestApprovalStepRepository repository;
 public void execute(DelegateExecution e){Long id=((Number)e.getVariable("workflowRequestId")).longValue();List<?> ids=(List<?>)e.getVariable("approverIds");if(ids==null||ids.isEmpty())throw new IllegalStateException("Approval route is empty.");int base=repository.findByWorkflowRequestIdOrderBySequenceNo(id).size();for(int i=0;i<ids.size();i++){WorkflowRequestApprovalStep s=new WorkflowRequestApprovalStep();s.setWorkflowRequestId(id);s.setSequenceNo(base+i+1);s.setApproverEmployeeId(Long.valueOf(String.valueOf(ids.get(i))));s.setDecision(ApprovalDecision.PENDING);repository.save(s);}e.setVariable("approvalSequenceBase",base);e.setVariable("currentLevel",1);e.setVariable("currentApproverId",String.valueOf(ids.getFirst()));e.setVariable("hasMoreApprovers",ids.size()>1);}
}
