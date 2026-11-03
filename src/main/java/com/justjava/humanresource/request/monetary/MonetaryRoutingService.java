package com.justjava.humanresource.request.monetary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.approval.enums.*;
import com.justjava.humanresource.approval.model.*;
import com.justjava.humanresource.approval.service.ApprovalRouteResolverFactory;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.grouped.GroupedApprovalConfigurationService;
import com.justjava.humanresource.request.handler.WorkflowRequestHandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class MonetaryRoutingService {
 private final MonetaryPolicyService policyService;
 private final MonetaryBindingRepository bindings;
 private final MonetaryBandRepository bands;
 private final MonetarySnapshotRepository snapshots;
 private final WorkflowRequestHandlerRegistry handlers;
 private final ApprovalRouteResolverFactory routes;
 private final GroupedApprovalConfigurationService grouped;
 private final ObjectMapper mapper;

 @Transactional(readOnly=true) public Optional<Selection> select(WorkflowRequest request,WorkflowRequestType type){
  var previous=snapshots.findFirstByRequestIdOrderBySubmissionNoDesc(request.getId());
  if(previous.isEmpty()&&!type.isMonetaryRoutingEnabled())return Optional.empty();
  policyService.requireEnabled();
  if(!type.isRequiresApproval())throw conflict("Monetary routing requires approval to be enabled.");
  var money=handlers.get(request.getRequestType()).monetaryContext(request).orElseThrow(()->conflict("This request type does not provide a monetary amount."));
  Long policyId;
  if(previous.isPresent()){
   if(!previous.get().getCurrency().equals(money.currency()))throw conflict("Currency cannot change after first monetary submission. Create a new request.");
   policyId=previous.get().getPolicyId();
  }else policyId=bindings.findByRequestTypeAndCurrency(request.getRequestType(),money.currency()).map(MonetaryBinding::getPolicyId).orElseThrow(()->conflict("No monetary policy is activated for this request type and currency."));
  var policy=policyService.published(policyId);
  if(policy.getRequestType()!=request.getRequestType()||!policy.getCurrency().equals(money.currency()))throw conflict("Policy dimensions do not match this request.");
  var matches=bands.findByPolicyIdOrderByOrdinal(policyId).stream().filter(b->money.amount().compareTo(b.getLowerBound())>=0&&(b.getUpperBound()==null||money.amount().compareTo(b.getUpperBound())<0)).toList();
  if(matches.size()!=1)throw conflict("The monetary amount must match exactly one policy band.");
  var band=matches.getFirst();List<ApproverRef> approvers=List.of();Object resolved;
  if("GROUPED".equals(band.getTarget())){
   grouped.requireEnabled();
   resolved=grouped.effectiveLevels(band.getGroupedVersionId(),request.getRequesterEmployeeId()).stream()
    .map(level->new ResolvedLevel(level.name(),level.policy().name(),level.employeeIds().stream().map(this::resolvedMember).toList())).toList();
  }else if("LINE_MANAGER".equals(band.getTarget())){
   var context=ApprovalContext.builder().moduleType(ApprovalModuleType.REQUEST).routeType(ApprovalRouteType.LINE_MANAGER).requesterEmployeeId(request.getRequesterEmployeeId()).moduleRefId(request.getId()).requestTypeCode(request.getRequestType().name()).build();
   approvers=routes.getResolver(context).resolveApprovers(context);
   if(approvers.isEmpty())throw conflict("The selected Line Manager route has no eligible approvers.");
   for(var approver:approvers){
    if(approver.getEmployeeId()==null||approver.getEmployeeId().equals(request.getRequesterEmployeeId()))throw conflict("The selected route cannot include the requester.");
    grouped.eligibleEmployee(approver.getEmployeeId());
   }
   resolved=approvers.stream().map(a->new ResolvedLevel("Line Manager level "+a.getLevel(),"SINGLE",List.of(resolvedMember(a.getEmployeeId())))).toList();
  }else throw conflict("Unsupported monetary route target.");
  try{return Optional.of(new Selection(policy,band,money,approvers,mapper.writeValueAsString(resolved),previous.map(MonetarySnapshot::getSubmissionNo).orElse(0)+1));}
  catch(JsonProcessingException ex){throw new IllegalStateException("Could not snapshot the selected approval route.",ex);}
 }
 @Transactional public void record(WorkflowRequest request,Selection s){
  var row=new MonetarySnapshot();row.setRequestId(request.getId());row.setSubmissionNo(s.submissionNo());row.setPolicyId(s.policy().getId());row.setBandId(s.band().getId());
  row.setPolicyName(s.policy().getName()+" (v"+s.policy().getRevision()+")");row.setAmount(s.money().amount());row.setCurrency(s.money().currency());
  row.setLowerBound(s.band().getLowerBound());row.setUpperBound(s.band().getUpperBound());row.setTarget(s.band().getTarget());row.setGroupedVersionId(s.band().getGroupedVersionId());
  row.setResolvedRoute(s.resolvedRoute());row.setWorkflowInstanceId(request.getWorkflowInstanceId());row.setSubmittedBy(request.getRequesterEmployeeId());snapshots.saveAndFlush(row);
 }
 @Transactional(readOnly=true) public void validateCurrencyEdit(WorkflowRequest r,com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand c){
  var previous=snapshots.findFirstByRequestIdOrderBySubmissionNoDesc(r.getId());if(previous.isEmpty())return;
  String currency=switch(r.getRequestType()){
   case CASH_ADVANCE -> c.getCashAdvance()==null?null:c.getCashAdvance().getCurrency();
   case FUND_REQUEST -> c.getFundRequest()==null?null:c.getFundRequest().getCurrency();
   case EXPENSE_REIMBURSEMENT -> c.getExpenseReimbursement()==null?null:c.getExpenseReimbursement().getCurrency();
   default -> previous.get().getCurrency();};
  if(!previous.get().getCurrency().equals(MonetaryValues.currency(currency)))throw conflict("Currency cannot change after first monetary submission. Create a new request.");
 }
 @Transactional(readOnly=true) public List<MonetarySnapshot> history(Long requestId){return snapshots.findByRequestIdOrderBySubmissionNo(requestId);}
 private ResolvedMember resolvedMember(Long id){return new ResolvedMember(id,grouped.eligibleEmployee(id).getFullName());}
 public record ResolvedMember(Long employeeId,String name){}
 public record ResolvedLevel(String name,String policy,List<ResolvedMember> members){}
 public record Selection(MonetaryPolicy policy,MonetaryBand band,RequestMonetaryContext money,List<ApproverRef> approvers,String resolvedRoute,int submissionNo){}
 private ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
}
