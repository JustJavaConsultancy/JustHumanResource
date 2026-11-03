package com.justjava.humanresource.request.monetary;

import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.grouped.GroupedApprovalConfigurationService;
import com.justjava.humanresource.request.repository.WorkflowRequestTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class MonetaryPolicyService {
 private final MonetaryPolicyRepository policies;
 private final MonetaryBandRepository bands;
 private final MonetaryBindingRepository bindings;
 private final WorkflowRequestTypeRepository types;
 private final GroupedApprovalConfigurationService access;
 private final MonetaryPolicyEventRepository events;
 @Value("${app.requests.monetary-routing.enabled:true}") private boolean enabled;
 public boolean isEnabled(){return enabled;}
 public void requireEnabled(){if(!enabled)throw conflict("New monetary-routed submissions are disabled. Existing approvals remain available.");}
 public static boolean supports(RequestType type){return type==RequestType.EXPENSE_REIMBURSEMENT||FundingRequestService.supports(type);}

 @Transactional(readOnly=true) public List<View> list(){access.requireAdministrator();return policies.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();}
 @Transactional(readOnly=true) public List<MonetaryBinding> bindings(){access.requireAdministrator();return bindings.findAllByOrderById();}
 @Transactional public View create(MonetaryPolicyCommand command){
  access.requireAdministrator();requireEnabled();validate(command);
  var p=new MonetaryPolicy();p.setFamilyKey(UUID.randomUUID().toString());p.setRevision(1);return save(p,command);
 }
 @Transactional public View update(Long id,Long expectedVersion,MonetaryPolicyCommand command){
  access.requireAdministrator();requireEnabled();validate(command);var p=policies.lockById(id).orElseThrow();
  if(!"DRAFT".equals(p.getState())||!Objects.equals(p.getVersion(),expectedVersion))throw conflict("The draft changed or is published. Refresh or clone it.");
  bands.deleteByPolicyId(id);bands.flush();return save(p,command);
 }
 @Transactional public View publish(Long id,Long expectedVersion){
  access.requireAdministrator();requireEnabled();var p=policies.lockById(id).orElseThrow();
  if(!"DRAFT".equals(p.getState())||!Objects.equals(p.getVersion(),expectedVersion))throw conflict("The draft changed or was already published.");
  var v=view(p);validate(new MonetaryPolicyCommand(p.getName(),p.getRequestType(),p.getCurrency(),v.bands()));
  p.setState("PUBLISHED");p.setPublishedAt(LocalDateTime.now());p.setPublishedBy(access.actorId());event(p,"PUBLISHED");return view(policies.saveAndFlush(p));
 }
 @Transactional public View cloneDraft(Long id){
  access.requireAdministrator();requireEnabled();var source=policies.findById(id).orElseThrow();
  var root=policies.findAllByOrderByCreatedAtDesc().stream().filter(p->p.getFamilyKey().equals(source.getFamilyKey())).min(Comparator.comparing(MonetaryPolicy::getRevision)).orElseThrow();
  policies.lockById(root.getId()).orElseThrow();
  int revision=policies.findAllByOrderByCreatedAtDesc().stream().filter(p->p.getFamilyKey().equals(source.getFamilyKey())).mapToInt(MonetaryPolicy::getRevision).max().orElseThrow()+1;
  var p=new MonetaryPolicy();p.setFamilyKey(source.getFamilyKey());p.setRevision(revision);
  return save(p,new MonetaryPolicyCommand(source.getName(),source.getRequestType(),source.getCurrency(),view(source).bands()));
 }
 @Transactional public void activate(Long id){
  access.requireAdministrator();requireEnabled();var p=published(id);
  var type=types.findByCodeForUpdate(p.getRequestType().name()).orElseThrow();
  if(!type.isRequiresApproval())throw conflict("Monetary routing requires approval to be enabled.");
  var b=bindings.findByRequestTypeAndCurrency(p.getRequestType(),p.getCurrency()).orElseGet(MonetaryBinding::new);
  b.setRequestType(p.getRequestType());b.setCurrency(p.getCurrency());b.setPolicyId(p.getId());bindings.save(b);
  type.setMonetaryRoutingEnabled(true);types.save(type);event(p,"ACTIVATED");
 }
 @Transactional public void fixedRouting(RequestType requestType){
  access.requireAdministrator();var type=types.findByCodeForUpdate(requestType.name()).orElseThrow();
  // Historical bindings and snapshots remain intact. Returned monetary requests still use their pinned policy.
  type.setMonetaryRoutingEnabled(false);types.save(type);var e=new MonetaryPolicyEvent();e.setRequestType(requestType);e.setAction("FIXED_ROUTING");e.setActorId(access.actorId());events.save(e);
 }
 @Transactional public View availability(Long id,boolean value){
  access.requireAdministrator();var p=policies.lockById(id).orElseThrow();p.setEnabled(value);event(p,value?"ENABLED":"DISABLED");return view(policies.saveAndFlush(p));
 }
 @Transactional(readOnly=true) public MonetaryPolicy published(Long id){
  var p=policies.findById(id).orElseThrow();
  if(!p.isEnabled()||!"PUBLISHED".equals(p.getState()))throw conflict("The monetary policy is disabled or unpublished. Contact an administrator.");
  return p;
 }
 public static List<MonetaryPolicyCommand.Band> validateCoverage(String currency,List<MonetaryPolicyCommand.Band> source){
  MonetaryValues.currency(currency);
  if(source==null||source.isEmpty()||source.size()>30)throw new IllegalArgumentException("A policy requires 1 to 30 bands.");
  for(var b:source){
   if(b==null||b.target()==null)throw new IllegalArgumentException("Every band needs a route target.");
   MonetaryValues.amount(b.lowerBound(),currency,true);
   if(b.upperBound()!=null){MonetaryValues.amount(b.upperBound(),currency,false);if(b.upperBound().compareTo(b.lowerBound())<=0)throw new IllegalArgumentException("Upper bound must exceed lower bound.");}
   if((b.target()==MonetaryPolicyCommand.Target.GROUPED)!=(b.groupedVersionId()!=null))throw new IllegalArgumentException("Only grouped targets require a published grouped version.");
  }
  var sorted=source.stream().sorted(Comparator.comparing(MonetaryPolicyCommand.Band::lowerBound)).toList();
  BigDecimal next=BigDecimal.ZERO;
  for(var b:sorted){if(next==null||b.lowerBound().compareTo(next)!=0)throw new IllegalArgumentException("Bands must cover amounts from zero without gaps or overlaps.");next=b.upperBound();}
  if(next!=null)throw new IllegalArgumentException("The highest band must have no upper bound.");
  return sorted;
 }
 private void validate(MonetaryPolicyCommand c){
  if(c==null||c.name()==null||c.name().isBlank()||c.name().length()>100||!supports(c.requestType()))throw new IllegalArgumentException("Choose a monetary request type and policy name.");
  for(var b:validateCoverage(c.currency(),c.bands()))if(b.groupedVersionId()!=null)access.published(b.groupedVersionId());
 }
 private View save(MonetaryPolicy p,MonetaryPolicyCommand c){
  p.setName(c.name().trim());p.setRequestType(c.requestType());p.setCurrency(MonetaryValues.currency(c.currency()));p.setUpdatedAt(LocalDateTime.now());p=policies.saveAndFlush(p);
  int ordinal=1;for(var b:validateCoverage(p.getCurrency(),c.bands())){var row=new MonetaryBand();row.setPolicyId(p.getId());row.setOrdinal(ordinal++);row.setLowerBound(b.lowerBound());row.setUpperBound(b.upperBound());row.setTarget(b.target().name());row.setGroupedVersionId(b.groupedVersionId());bands.save(row);}
  bands.flush();event(p,"DRAFT_SAVED");return view(p);
 }
 @Transactional public void recordTypeAvailability(RequestType type,boolean enabled){
  access.requireAdministrator();var e=new MonetaryPolicyEvent();e.setRequestType(type);e.setAction(enabled?"TYPE_ENABLED":"TYPE_DISABLED");e.setActorId(access.actorId());events.save(e);
 }
 @Transactional(readOnly=true) public List<MonetaryPolicyEvent> audit(){access.requireAdministrator();return events.findTop100ByOrderByIdDesc();}
 private void event(MonetaryPolicy p,String action){var e=new MonetaryPolicyEvent();e.setPolicyId(p.getId());e.setRequestType(p.getRequestType());e.setActorId(access.actorId());e.setAction(action);events.save(e);}
 private View view(MonetaryPolicy p){return new View(p.getId(),p.getVersion(),p.getName(),p.getRequestType(),p.getCurrency(),p.getRevision(),p.getState(),p.isEnabled(),
  bands.findByPolicyIdOrderByOrdinal(p.getId()).stream().map(b->new MonetaryPolicyCommand.Band(b.getLowerBound(),b.getUpperBound(),MonetaryPolicyCommand.Target.valueOf(b.getTarget()),b.getGroupedVersionId())).toList());}
 public record View(Long id,Long version,String name,RequestType requestType,String currency,Integer revision,String state,boolean enabled,List<MonetaryPolicyCommand.Band> bands){}
 private ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
}
