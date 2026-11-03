package com.justjava.humanresource.request.monetary;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.*;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.request.dto.*;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.grouped.GroupedApprovalConfigurationService;
import com.justjava.humanresource.request.repository.WorkflowRequestTypeRepository;
import com.justjava.humanresource.request.service.WorkflowRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Controller @RequiredArgsConstructor
public class MonetaryRequestController {
 private final MonetaryPolicyService policies;
 private final MonetaryRoutingService routing;
 private final FundingRequestService funding;
 private final WorkflowRequestService requests;
 private final WorkflowRequestTypeRepository types;
 private final EmployeeRepository employees;
 private final GroupedApprovalConfigurationService access;
 private final AuthenticationManager auth;

 @GetMapping("/requests/monetary-policies") public String policyPage(){access.requireAdministrator();return "request/monetary-policies";}
 @GetMapping({"/employee/requests/funding/new","/employee/requests/funding/{id}/edit","/mobile/requests/funding/new","/mobile/requests/funding/{id}/edit"})
 public String fundingPage(jakarta.servlet.http.HttpServletRequest request,Model model){
  model.addAttribute("employee",requests.currentEmployee());model.addAttribute("fundingLayout",request.getRequestURI().startsWith("/mobile/")?"layouts/mobileEmployeeLayout":"layouts/employeeLayout");return "request/funding-form";
 }
 @GetMapping("/api/request-monetary/config") @ResponseBody public Map<String,Object> config(){
  access.requireAdministrator();return Map.of("enabled",policies.isEnabled(),"fundingEnabled",funding.isEnabled(),"policies",policies.list(),"bindings",policies.bindings(),
   "types",types.findAll().stream().filter(t->MonetaryPolicyService.supports(RequestType.valueOf(t.getCode()))).toList());
 }
 @PostMapping("/api/request-monetary/policies") @ResponseBody public MonetaryPolicyService.View create(@Valid @RequestBody MonetaryPolicyCommand c){return policies.create(c);}
 @GetMapping("/api/request-monetary/audit") @ResponseBody public List<MonetaryPolicyEvent> audit(){return policies.audit();}
 @PutMapping("/api/request-monetary/policies/{id}") @ResponseBody public MonetaryPolicyService.View update(@PathVariable Long id,@RequestParam Long version,@Valid @RequestBody MonetaryPolicyCommand c){return policies.update(id,version,c);}
 @PostMapping("/api/request-monetary/policies/{id}/publish") @ResponseBody public MonetaryPolicyService.View publish(@PathVariable Long id,@RequestParam Long version){return policies.publish(id,version);}
 @PostMapping("/api/request-monetary/policies/{id}/clone") @ResponseBody public MonetaryPolicyService.View cloneDraft(@PathVariable Long id){return policies.cloneDraft(id);}
 @PostMapping("/api/request-monetary/policies/{id}/activate") @ResponseBody public void activate(@PathVariable Long id){policies.activate(id);}
 @PostMapping("/api/request-monetary/policies/{id}/availability") @ResponseBody public MonetaryPolicyService.View availability(@PathVariable Long id,@RequestParam boolean enabled){return policies.availability(id,enabled);}
 @PostMapping("/api/request-monetary/types/{type}/fixed-routing") @ResponseBody public void fixed(@PathVariable RequestType type){policies.fixedRouting(type);}
 @PostMapping("/api/request-monetary/types/{type}/availability") @ResponseBody @Transactional
 public void fundingAvailability(@PathVariable RequestType type,@RequestParam boolean enabled){
  access.requireAdministrator();if(!FundingRequestService.supports(type))throw new IllegalArgumentException("Only funding types use this availability control.");
  if(enabled)funding.requireEnabled(type);var config=types.findByCodeForUpdate(type.name()).orElseThrow();config.setEnabled(enabled);types.save(config);policies.recordTypeAvailability(type,enabled);
 }
 @GetMapping("/api/request-monetary/options") @ResponseBody public Map<String,Object> options(){
  requests.currentEmployee();
  var people=employees.findAllVisible().stream().filter(e->e.getEmploymentStatus()==EmploymentStatus.ACTIVE&&e.getStatus()!=RecordStatus.SUSPENDED)
   .map(e->new RequestLookupOption(e.getId(),e.getEmployeeNumber()+" - "+e.getFullName())).toList();
  return Map.of("currencies",new TreeSet<>(MonetaryValues.CURRENCIES),"beneficiaries",people,"enabled",funding.isEnabled(),"types",types.findByEnabledTrueOrderByName().stream().filter(t->FundingRequestService.supports(RequestType.valueOf(t.getCode()))).toList());
 }
 @PostMapping("/api/request-monetary/funding") @ResponseBody public WorkflowRequest createFunding(@Valid @RequestBody CreateWorkflowRequestCommand c){
  if(!FundingRequestService.supports(c.getRequestType()))throw new IllegalArgumentException("Choose Cash Advance or Fund Request.");return requests.createDraft(c);
 }
 @GetMapping("/api/request-monetary/funding/{id}") @ResponseBody public WorkflowRequestEditDTO edit(@PathVariable Long id){
  var detail=requests.editDetails(id);if(!FundingRequestService.supports(detail.getRequestType())||!detail.isCanEdit())throw new AccessDeniedException("This funding request is not editable by you.");return detail;
 }
 @PutMapping("/api/request-monetary/funding/{id}") @ResponseBody public WorkflowRequest updateFunding(@PathVariable Long id,@Valid @RequestBody CreateWorkflowRequestCommand c){
  if(!FundingRequestService.supports(c.getRequestType()))throw new IllegalArgumentException("Choose a funding request type.");return requests.updateEditableRequest(id,c);
 }
 @PostMapping("/api/request-monetary/requests/{id}/submit") @ResponseBody public WorkflowRequest submit(@PathVariable Long id){return requests.submit(id);}
 @GetMapping("/api/request-monetary/requests/{id}/preview") @ResponseBody public Map<String,Object> preview(@PathVariable Long id){
  var request=requests.visible(id);var type=types.findByCode(request.getRequestType().name()).orElseThrow();var selected=routing.select(request,type);
  if(selected.isEmpty())return Map.of("monetary",false,"message","The configured fixed approval route applies. Free Route and Finance remain separate authorized actions.");
  var s=selected.get();return Map.of("monetary",true,"policy",s.policy().getName(),"policyVersion",s.policy().getRevision(),"amount",s.money().amount().toPlainString(),"currency",s.money().currency(),"lowerBound",s.band().getLowerBound().toPlainString(),"upperBound",s.band().getUpperBound()==null?"No upper limit":s.band().getUpperBound().toPlainString(),"target",s.band().getTarget(),"resolvedRoute",s.resolvedRoute(),"routeLabel","GROUPED".equals(s.band().getTarget())?access.published(s.band().getGroupedVersionId()).getName():"Line Manager chain ("+s.approvers().size()+" approvers)");
 }
 @GetMapping("/api/request-monetary/requests/{id}/history") @ResponseBody public Map<String,Object> history(@PathVariable Long id){
  var detail=auth.isFinancialOfficer()?requests.financeDetails(id):requests.details(id);
  var request=detail.getRequest();var history=routing.history(id);
  boolean applicable=MonetaryPolicyService.supports(request.getRequestType())&&
   (!history.isEmpty()||types.findByCode(request.getRequestType().name()).map(WorkflowRequestType::isMonetaryRoutingEnabled).orElse(false));
  var capabilities=new RoutingCapabilities(applicable,policies.isEnabled(),applicable&&requests.canSubmitNormally(request));
  return Map.of("request",request,"typeDetails",detail.getTypeDetails()==null?Map.of():detail.getTypeDetails(),"snapshots",history,"funding",FundingRequestService.supports(request.getRequestType()),"routing",capabilities);
 }
 public record RoutingCapabilities(boolean monetaryApplicable,boolean monetaryRoutingAvailable,boolean canPreviewSavedRoute){}
}
