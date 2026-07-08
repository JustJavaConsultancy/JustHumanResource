package com.justjava.humanresource.request.handler;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.RequestType;
import java.util.Map;
public interface WorkflowRequestTypeHandler {
 RequestType supportedType();
 void validate(CreateWorkflowRequestCommand command);
 void saveDetails(WorkflowRequest request, CreateWorkflowRequestCommand command);
 default void beforeSubmit(WorkflowRequest request) {}
 default Map<String,Object> buildWorkflowVariables(WorkflowRequest request) { return Map.of(); }
 default void afterApproved(WorkflowRequest request) {}
 default void afterRejected(WorkflowRequest request) {}
}
