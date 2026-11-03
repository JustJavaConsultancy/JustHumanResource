package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.handler.WorkflowRequestTypeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
@Component @RequiredArgsConstructor
public class CashAdvanceRequestHandler implements WorkflowRequestTypeHandler {
 private final FundingRequestService funding;
 public RequestType supportedType(){return RequestType.CASH_ADVANCE;}
 public void validate(CreateWorkflowRequestCommand c){funding.validate(c);}
 public void saveDetails(WorkflowRequest r,CreateWorkflowRequestCommand c){funding.save(r,c);}
 public void updateDetails(WorkflowRequest r,CreateWorkflowRequestCommand c){funding.save(r,c);}
 public void beforeSubmit(WorkflowRequest r){funding.beforeSubmit(r);}
 public Optional<RequestMonetaryContext> monetaryContext(WorkflowRequest r){return Optional.of(funding.context(r));}
}
