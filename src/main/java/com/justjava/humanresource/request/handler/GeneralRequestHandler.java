package com.justjava.humanresource.request.handler;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.RequestType;
import org.springframework.stereotype.Component;
@Component public class GeneralRequestHandler implements WorkflowRequestTypeHandler { public RequestType supportedType(){return RequestType.GENERAL_REQUEST;} public void validate(CreateWorkflowRequestCommand c){} public void saveDetails(WorkflowRequest r,CreateWorkflowRequestCommand c){} }
