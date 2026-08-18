package com.justjava.humanresource.request.service;
import com.justjava.humanresource.request.entity.WorkflowRequestActivity;
import com.justjava.humanresource.request.enums.RequestActivityType;
import com.justjava.humanresource.request.repository.WorkflowRequestActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor public class WorkflowRequestActivityService {
 private final WorkflowRequestActivityRepository repository;
 public void record(Long requestId,RequestActivityType type,String description,Long actorId){WorkflowRequestActivity a=new WorkflowRequestActivity();a.setWorkflowRequestId(requestId);a.setActivityType(type);a.setDescription(description);a.setActorEmployeeId(actorId);repository.save(a);}
}
