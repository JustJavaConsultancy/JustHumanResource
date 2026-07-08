package com.justjava.humanresource.request.workflow.delegate;
import com.justjava.humanresource.request.enums.RequestStatus;
import com.justjava.humanresource.request.handler.WorkflowRequestHandlerRegistry;
import com.justjava.humanresource.request.repository.WorkflowRequestRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.*;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
@Component("finalizeRequestApprovalDelegate") @RequiredArgsConstructor public class FinalizeRequestApprovalDelegate implements JavaDelegate { private final WorkflowRequestRepository repository;private final WorkflowRequestHandlerRegistry handlers;public void execute(DelegateExecution e){Long id=((Number)e.getVariable("workflowRequestId")).longValue();var r=repository.findById(id).orElseThrow();r.setStatus(RequestStatus.APPROVED);r.setApprovedAt(LocalDateTime.now());repository.save(r);handlers.get(r.getRequestType()).afterApproved(r);} }
