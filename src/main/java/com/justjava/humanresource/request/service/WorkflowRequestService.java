package com.justjava.humanresource.request.service;

import com.justjava.humanresource.approval.enums.ApprovalModuleType;
import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.approval.model.*;
import com.justjava.humanresource.approval.service.ApprovalRouteResolverFactory;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobGradeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.integration.fam.FamAssetDTO;
import com.justjava.humanresource.integration.fam.FamAssetLookupService;
import com.justjava.humanresource.request.dto.*;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.*;
import com.justjava.humanresource.request.handler.*;
import com.justjava.humanresource.request.repository.*;
import com.justjava.humanresource.utils.AfterCommitExecutor;
import com.justjava.humanresource.utils.RequestEmailService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class WorkflowRequestService {
 private final WorkflowRequestRepository requestRepository; private final WorkflowRequestTypeRepository typeRepository; private final WorkflowRequestItemRepository itemRepository; private final WorkflowRequestAttachmentRepository attachmentRepository; private final WorkflowRequestApprovalStepRepository stepRepository; private final WorkflowRequestCommentRepository commentRepository; private final WorkflowRequestActivityRepository activityRepository; private final StaffRequisitionDetailRepository staffRepository; private final FileRequestDetailRepository fileRepository; private final AssetRequestDetailRepository assetRepository; private final ExpenseReimbursementDetailRepository expenseRepository; private final ExpenseReimbursementItemRepository expenseItemRepository; private final WorkflowRequestHandlerRegistry handlers; private final RequestNumberService numberService; private final WorkflowRequestActivityService activityService; private final EmployeeService employeeService; private final AuthenticationManager auth; private final ApprovalRouteResolverFactory routeFactory; private final RuntimeService runtimeService; private final FlowableTaskService taskService; private final TaskService flowableTaskService; private final FamAssetLookupService famAssetLookupService; private final DepartmentRepository departmentRepository; private final JobGradeRepository jobGradeRepository; private final EmployeeRepository employeeRepository; private final RequestEmailService requestEmailService; private final AfterCommitExecutor afterCommitExecutor; private final WorkflowRequestFreeRouteEventRepository freeRouteEventRepository; private final WorkflowRequestFinanceActionRepository financeActionRepository; private final com.justjava.humanresource.utils.FinanceRequestEmailService financeRequestEmailService;
 @Transactional public WorkflowRequest createDraft(CreateWorkflowRequestCommand command){Employee actor=currentEmployee();WorkflowRequestType config=type(command.getRequestType());handlers.get(command.getRequestType()).validate(command);WorkflowRequest r=new WorkflowRequest();r.setRequestNumber(numberService.next(command.getRequestType()));r.setRequestType(command.getRequestType());r.setTitle(command.getTitle().trim());r.setDescription(command.getDescription());r.setPriority(command.getPriority()==null?RequestPriority.NORMAL:command.getPriority());r.setRequesterEmployeeId(actor.getId());r.setDepartmentId(command.getDepartmentId()!=null?command.getDepartmentId():actor.getDepartment()==null?null:actor.getDepartment().getId());r.setStatus(RequestStatus.DRAFT);r=requestRepository.save(r);handlers.get(r.getRequestType()).saveDetails(r,command);saveItems(r,command.getItems());activityService.record(r.getId(),RequestActivityType.CREATED,"Request draft created",actor.getId());return r;}
 @Transactional public WorkflowRequest createAndSubmit(CreateWorkflowRequestCommand command){WorkflowRequest r=createDraft(command);return submit(r.getId());}
 @Transactional public WorkflowRequest submit(Long id){WorkflowRequest r=owned(id);if(!canSubmit(r,r.getRequesterEmployeeId()))throw new IllegalStateException("This request cannot be submitted on this route.");WorkflowRequestType config=type(r.getRequestType());if(config.isRequiresAttachment()&&attachmentRepository.countByWorkflowRequestId(id)==0)throw new IllegalStateException("An attachment is required before submission.");if(config.isSupportsItems()&&itemRepository.countByWorkflowRequestId(id)==0)throw new IllegalStateException("At least one item is required before submission.");handlers.get(r.getRequestType()).beforeSubmit(r);boolean firstSubmission=r.getSubmittedAt()==null;r.setStatus(RequestStatus.SUBMITTED);r.setSubmittedAt(LocalDateTime.now());r=requestRepository.save(r);if(firstSubmission)financeRequestEmailService.enqueueInitialSubmission(r);if(!config.isRequiresApproval()){r.setStatus(RequestStatus.APPROVED);r.setApprovedAt(LocalDateTime.now());handlers.get(r.getRequestType()).afterApproved(r);activityService.record(id,RequestActivityType.APPROVED,"Request automatically approved",r.getRequesterEmployeeId());WorkflowRequest saved=requestRepository.save(r);afterCommitExecutor.runAfterCommit(()->requestEmailService.notifyRequestApproved(saved));return saved;}ApprovalRouteType routeType=config.getApprovalRouteType()==null?ApprovalRouteType.LINE_MANAGER:config.getApprovalRouteType();if(routeType==ApprovalRouteType.CUSTOM&&config.getCustomApprovalPathId()==null)throw new IllegalStateException("A custom approval path must be selected for this request type.");r.setApprovalRouteType(routeType);ApprovalContext context=ApprovalContext.builder().moduleType(ApprovalModuleType.REQUEST).routeType(routeType).requesterEmployeeId(r.getRequesterEmployeeId()).moduleRefId(r.getId()).requestTypeCode(r.getRequestType().name()).customApprovalPathId(config.getCustomApprovalPathId()).build();List<ApproverRef> route=routeFactory.getResolver(context).resolveApprovers(context);if(route.isEmpty())throw new IllegalStateException("No approval route is configured for this requester.");List<String> approvers=route.stream().map(a->String.valueOf(a.getEmployeeId())).toList();r.setCurrentApprovalLevel(1);r.setTotalApprovalLevels(approvers.size());r.setStatus(RequestStatus.IN_APPROVAL);r=requestRepository.save(r);Map<String,Object> vars=new HashMap<>(handlers.get(r.getRequestType()).buildWorkflowVariables(r));vars.put("workflowRequestId",r.getId());vars.put("requestType",r.getRequestType().name());vars.put("requesterEmployeeId",r.getRequesterEmployeeId());vars.put("approvalRouteType",routeType.name());vars.put("customApprovalPathId",config.getCustomApprovalPathId());vars.put("approverIds",approvers);vars.put("totalLevels",approvers.size());var pi=runtimeService.startProcessInstanceByKey(config.getProcessDefinitionKey(),"REQUEST_"+r.getId(),vars);r.setWorkflowInstanceId(pi.getProcessInstanceId());activityService.record(id,RequestActivityType.SUBMITTED,"Request submitted for approval",r.getRequesterEmployeeId());WorkflowRequest saved=requestRepository.save(r);Long firstApproverId=route.get(0).getEmployeeId();afterCommitExecutor.runAfterCommit(()->{requestEmailService.notifyRequestSubmitted(saved,firstApproverId);requestEmailService.notifyPendingApproval(saved,firstApproverId);});return saved;}
 @Transactional public WorkflowRequest sendFreeRoute(Long id,Long toEmployeeId,String comment){WorkflowRequest r=owned(id);if(!canSendFreeRoute(r,r.getRequesterEmployeeId()))throw new IllegalStateException("This request cannot be sent through Free Route.");if(r.getWorkflowInstanceId()!=null)throw new IllegalStateException("This request already has an active approval process.");WorkflowRequestType config=type(r.getRequestType());if(config.isRequiresAttachment()&&attachmentRepository.countByWorkflowRequestId(id)==0)throw new IllegalStateException("An attachment is required before submission.");if(config.isSupportsItems()&&itemRepository.countByWorkflowRequestId(id)==0)throw new IllegalStateException("At least one item is required before submission.");handlers.get(r.getRequestType()).beforeSubmit(r);if(toEmployeeId==null)throw new IllegalArgumentException("A recipient employee must be selected.");Employee actor=currentEmployee();if(toEmployeeId.equals(r.getRequesterEmployeeId()))throw new IllegalArgumentException("You cannot send a request to the original requester.");if(toEmployeeId.equals(actor.getId()))throw new IllegalArgumentException("You cannot send a request to yourself.");requireSelectableFreeRouteEmployee(toEmployeeId);boolean firstSend=r.getSubmittedAt()==null;LocalDateTime now=LocalDateTime.now();r.setApprovalRouteType(ApprovalRouteType.FREE_ROUTE);r.setStatus(RequestStatus.IN_APPROVAL);if(r.getSubmittedAt()==null)r.setSubmittedAt(now);r.setCurrentApprovalLevel(1);r.setTotalApprovalLevels(1);r.setFreeRouteCurrentApproverEmployeeId(toEmployeeId);if(r.getFreeRouteStartedAt()==null)r.setFreeRouteStartedAt(now);r=requestRepository.save(r);if(firstSend)financeRequestEmailService.enqueueInitialSubmission(r);Map<String,Object> vars=new HashMap<>(handlers.get(r.getRequestType()).buildWorkflowVariables(r));vars.put("workflowRequestId",r.getId());vars.put("requestType",r.getRequestType().name());vars.put("requesterEmployeeId",r.getRequesterEmployeeId());vars.put("approvalRouteType",ApprovalRouteType.FREE_ROUTE.name());vars.put("approverIds",List.of(String.valueOf(toEmployeeId)));vars.put("totalLevels",1);var pi=runtimeService.startProcessInstanceByKey(config.getProcessDefinitionKey(),"REQUEST_"+r.getId(),vars);r.setWorkflowInstanceId(pi.getProcessInstanceId());WorkflowRequest saved=requestRepository.save(r);WorkflowRequestFreeRouteEvent event=new WorkflowRequestFreeRouteEvent();event.setWorkflowRequestId(id);event.setEventType(FreeRouteEventType.SENT);event.setFromEmployeeId(actor.getId());event.setToEmployeeId(toEmployeeId);event.setComment(comment==null||comment.isBlank()?null:comment.trim());freeRouteEventRepository.save(event);activityService.record(id,RequestActivityType.FREE_ROUTE_SENT,"Request sent by Free Route to employee id: "+toEmployeeId,actor.getId());afterCommitExecutor.runAfterCommit(()->{requestEmailService.notifyRequestSubmitted(saved,toEmployeeId);requestEmailService.notifyPendingApproval(saved,toEmployeeId);});return saved;}
 @Transactional public WorkflowRequest forwardFreeRoute(String taskId,Long toEmployeeId,String comment){if(taskId==null||taskId.isBlank())throw new IllegalArgumentException("Task id is required.");Employee actor=currentEmployee();if(!taskService.isTaskAssignedTo(taskId,String.valueOf(actor.getId())))throw new IllegalStateException("Task is not assigned to current user.");Task task=flowableTaskService.createTaskQuery().taskId(taskId).singleResult();if(task==null)throw new IllegalStateException("Task not found: "+taskId);Object rawId=runtimeService.getVariable(task.getProcessInstanceId(),"workflowRequestId");if(rawId==null)throw new IllegalStateException("Task is not linked to a workflow request.");Long requestId=rawId instanceof Number?((Number)rawId).longValue():Long.valueOf(String.valueOf(rawId));WorkflowRequest r=requestRepository.findById(requestId).orElseThrow(()->new IllegalArgumentException("Request not found."));if(r.getStatus()!=RequestStatus.IN_APPROVAL)throw new IllegalStateException("Only requests in approval can be forwarded.");if(r.getApprovalRouteType()!=ApprovalRouteType.FREE_ROUTE)throw new IllegalStateException("Only Free Route requests can be forwarded.");if(!actor.getId().equals(r.getFreeRouteCurrentApproverEmployeeId()))throw new IllegalStateException("Only the current assigned approver can forward this request.");if(toEmployeeId==null)throw new IllegalArgumentException("A recipient employee must be selected.");if(toEmployeeId.equals(r.getRequesterEmployeeId()))throw new IllegalArgumentException("You cannot forward a request to the original requester.");if(toEmployeeId.equals(actor.getId()))throw new IllegalArgumentException("You cannot forward a request to yourself.");requireSelectableFreeRouteEmployee(toEmployeeId);List<WorkflowRequestApprovalStep> steps=stepRepository.findByWorkflowRequestIdOrderBySequenceNo(r.getId());WorkflowRequestApprovalStep currentStep=steps.stream().filter(s->s.getSequenceNo().equals(r.getCurrentApprovalLevel())).findFirst().or(()->steps.stream().filter(s->s.getDecision()==ApprovalDecision.PENDING).reduce((a,b)->b)).orElseThrow(()->new IllegalStateException("No pending approval step found for this request."));int nextSequence=steps.stream().mapToInt(WorkflowRequestApprovalStep::getSequenceNo).max().orElse(0)+1;LocalDateTime now=LocalDateTime.now();currentStep.setDecision(ApprovalDecision.DELEGATED);currentStep.setComments(comment);currentStep.setDecisionAt(now);currentStep.setFlowableTaskId(taskId);currentStep.setForwardedToEmployeeId(toEmployeeId);currentStep.setForwardedAt(now);stepRepository.save(currentStep);WorkflowRequestApprovalStep nextStep=new WorkflowRequestApprovalStep();nextStep.setWorkflowRequestId(r.getId());nextStep.setSequenceNo(nextSequence);nextStep.setApproverEmployeeId(toEmployeeId);nextStep.setDecision(ApprovalDecision.PENDING);stepRepository.save(nextStep);r.setCurrentApprovalLevel(nextSequence);r.setTotalApprovalLevels(nextSequence);r.setFreeRouteCurrentApproverEmployeeId(toEmployeeId);r.setFreeRouteLastForwardedAt(now);WorkflowRequest saved=requestRepository.save(r);flowableTaskService.setAssignee(taskId,String.valueOf(toEmployeeId));Map<String,Object> vars=new HashMap<>();vars.put("currentLevel",nextSequence);vars.put("approvalSequenceBase",0);vars.put("currentApproverId",String.valueOf(toEmployeeId));vars.put("approverIds",List.of(String.valueOf(toEmployeeId)));vars.put("hasMoreApprovers",false);vars.put("totalLevels",1);runtimeService.setVariables(task.getProcessInstanceId(),vars);runtimeService.removeVariables(task.getProcessInstanceId(),List.of("approvalDecision","approvalComment","approvalActorId","flowableTaskId"));WorkflowRequestFreeRouteEvent event=new WorkflowRequestFreeRouteEvent();event.setWorkflowRequestId(r.getId());event.setEventType(FreeRouteEventType.FORWARDED);event.setFromEmployeeId(actor.getId());event.setToEmployeeId(toEmployeeId);event.setComment(comment==null||comment.isBlank()?null:comment.trim());freeRouteEventRepository.save(event);activityService.record(r.getId(),RequestActivityType.FREE_ROUTE_FORWARDED,"Request forwarded by Free Route from employee "+actor.getId()+" to employee "+toEmployeeId,actor.getId());afterCommitExecutor.runAfterCommit(()->{requestEmailService.notifyRequestMovedToApprover(saved,toEmployeeId);requestEmailService.notifyPendingApproval(saved,toEmployeeId);});return saved;}
 @Transactional(readOnly=true) public List<WorkflowRequest> myRequests(){return requestRepository.findByRequesterEmployeeIdOrderByCreatedAtDesc(currentEmployee().getId());}
 @Transactional(readOnly=true) public List<WorkflowRequest> visibleRequests(){return isHrAdmin()?requestRepository.findAllByOrderByCreatedAtDesc():myRequests();}
 @Transactional(readOnly=true) public List<WorkflowRequest> allRequests(){requireHrAdmin();return requestRepository.findAllByOrderByCreatedAtDesc();}
 @Transactional(readOnly=true) public List<WorkflowRequest> financeRequests(){requireFinance();return requestRepository.findAllByOrderByCreatedAtDesc();}
 @Transactional(readOnly=true) public WorkflowRequest requireFinanceRead(Long id){requireFinance();return requestRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Request not found."));}
 @Transactional(readOnly=true) public List<FlowableTaskDTO> pendingTasks(){Employee e=currentEmployee();return taskService.getTasksForAssigneeAcrossProcesses(String.valueOf(e.getId()),requestProcessKeys());}
 @Transactional(readOnly=true) public Map<String,Object> context(){Employee e=currentEmployee();return Map.of("employeeId",e.getId(),"hrAdmin",isHrAdmin(),"canCreate",true);}
 @Transactional(readOnly=true) public WorkflowRequestDetailDTO details(Long id){return buildDetails(visible(id),isHrAdmin());}
 @Transactional(readOnly=true) public WorkflowRequestDetailDTO financeDetails(Long id){return buildDetails(requireFinanceRead(id),false);}
 private WorkflowRequestDetailDTO buildDetails(WorkflowRequest r,boolean includeInternalComments){Long id=r.getId();Object typed=switch(r.getRequestType()){case STAFF_REQUISITION->staffRequisitionDetail(id);case FILE_REQUEST->fileRepository.findByWorkflowRequestId(id).orElse(null);case ASSET_REQUEST->assetRepository.findByWorkflowRequestId(id).orElse(null);case EXPENSE_REIMBURSEMENT->expenseReimbursementDetail(id);case GENERAL_REQUEST->null;};List<WorkflowRequestComment> comments=commentRepository.findByWorkflowRequestIdOrderByCreatedAt(id);if(!includeInternalComments)comments=comments.stream().filter(c->!c.isInternalOnly()).toList();return WorkflowRequestDetailDTO.builder().request(r).typeDetails(typed).items(itemRepository.findByWorkflowRequestIdOrderById(id)).attachments(attachmentRepository.findByWorkflowRequestIdOrderByCreatedAt(id)).approvalSteps(stepRepository.findByWorkflowRequestIdOrderBySequenceNo(id)).comments(comments).activities(activityRepository.findByWorkflowRequestIdOrderByCreatedAt(id)).freeRouteEvents(freeRouteEventRepository.findByWorkflowRequestIdOrderByCreatedAt(id)).build();}
 @Transactional public void approve(String taskId,String comment){complete(taskId,"APPROVE",comment);}
 @Transactional public void reject(String taskId,String comment){if(comment==null||comment.isBlank())throw new IllegalArgumentException("A rejection comment is required.");complete(taskId,"REJECT",comment);}
 @Transactional public void returnForCorrection(String taskId,String comment){if(comment==null||comment.isBlank())throw new IllegalArgumentException("A return comment is required.");complete(taskId,"RETURN",comment);}
 @Transactional public WorkflowRequest cancel(Long id){WorkflowRequest r=owned(id);if(EnumSet.of(RequestStatus.APPROVED,RequestStatus.REJECTED,RequestStatus.CANCELLED,RequestStatus.CLOSED).contains(r.getStatus()))throw new IllegalStateException("This request can no longer be cancelled.");if(r.getWorkflowInstanceId()!=null)runtimeService.deleteProcessInstance(r.getWorkflowInstanceId(),"Cancelled by requester");r.setStatus(RequestStatus.CANCELLED);r.setFinanceOverrideRecipientId(null);activityService.record(id,RequestActivityType.CANCELLED,"Request cancelled",r.getRequesterEmployeeId());return requestRepository.save(r);}
 @Transactional public WorkflowRequest close(Long id){requireHrAdmin();WorkflowRequest r=requestRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Request not found."));if(r.getStatus()!=RequestStatus.APPROVED)throw new IllegalStateException("Only approved requests can be closed.");r.setStatus(RequestStatus.CLOSED);r.setClosedAt(LocalDateTime.now());activityService.record(id,RequestActivityType.CLOSED,"Request closed",currentEmployee().getId());return requestRepository.save(r);}
 @Transactional public WorkflowRequestComment addComment(Long id,String text,boolean internal){if(text==null||text.isBlank())throw new IllegalArgumentException("Comment is required.");WorkflowRequest r=visible(id);Employee actor=currentEmployee();if(internal&&!isHrAdmin())throw new IllegalStateException("Only HR or administrators may add internal comments.");WorkflowRequestComment c=new WorkflowRequestComment();c.setWorkflowRequestId(id);c.setComment(text.trim());c.setCommentedByEmployeeId(actor.getId());c.setInternalOnly(internal);c=commentRepository.save(c);activityService.record(id,RequestActivityType.COMMENTED,"Comment added",actor.getId());return c;}
 public Employee currentEmployee(){Object email=auth.get("email");if(email==null)throw new IllegalStateException("Authenticated user has no email claim.");return employeeService.getByEmail(String.valueOf(email));}
 public WorkflowRequest visible(Long id){WorkflowRequest r=requestRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Request not found."));Long actor=currentEmployee().getId();if(r.getRequesterEmployeeId().equals(actor)||isHrAdmin()||actor.equals(r.getFinanceOverrideRecipientId())||stepRepository.findByWorkflowRequestIdOrderBySequenceNo(id).stream().anyMatch(s->s.getApproverEmployeeId().equals(actor)))return r;throw new IllegalStateException("You are not authorized to view this request.");}
 @Transactional(readOnly=true) public List<RequestLookupOption> freeRouteRecipients(Long requestId){Employee actor=currentEmployee();Set<Long> excluded=new HashSet<>();excluded.add(actor.getId());if(requestId!=null){WorkflowRequest r=visible(requestId);excluded.add(r.getRequesterEmployeeId());}return employeeRepository.findAllVisible().stream().filter(e->e.getEmploymentStatus()==EmploymentStatus.ACTIVE).filter(e->e.getStatus()==RecordStatus.ACTIVE).filter(e->!excluded.contains(e.getId())).sorted(Comparator.comparing(Employee::getFullName,String.CASE_INSENSITIVE_ORDER)).map(e->new RequestLookupOption(e.getId(),e.getEmployeeNumber()+" - "+e.getFullName()+(e.getDepartment()==null?"":" - "+e.getDepartment().getName()))).toList();}
 private void complete(String taskId,String decision,String comment){Employee actor=currentEmployee();if(!taskService.isTaskAssignedTo(taskId,String.valueOf(actor.getId())))throw new IllegalStateException("Task is not assigned to current user.");Map<String,Object> vars=new HashMap<>();vars.put("approvalDecision",decision);vars.put("approvalComment",comment);vars.put("approvalActorId",actor.getId());vars.put("flowableTaskId",taskId);taskService.completeTask(taskId,vars);}
 private WorkflowRequest owned(Long id){WorkflowRequest r=requestRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Request not found."));if(!r.getRequesterEmployeeId().equals(currentEmployee().getId()))throw new IllegalStateException("You do not own this request.");return r;}
 private boolean isEditableStatus(WorkflowRequest request){return request.getStatus()==RequestStatus.DRAFT||request.getStatus()==RequestStatus.RETURNED_FOR_CORRECTION;}
 private boolean wasFreeRoute(WorkflowRequest request){return request.getApprovalRouteType()==ApprovalRouteType.FREE_ROUTE;}
 private boolean canSubmit(WorkflowRequest request,Long actorEmployeeId){if(!request.getRequesterEmployeeId().equals(actorEmployeeId))return false;if(request.getStatus()==RequestStatus.DRAFT)return true;if(request.getStatus()==RequestStatus.RETURNED_FOR_CORRECTION)return !wasFreeRoute(request);return false;}
 private boolean canSendFreeRoute(WorkflowRequest request,Long actorEmployeeId){if(!request.getRequesterEmployeeId().equals(actorEmployeeId))return false;if(request.getStatus()==RequestStatus.DRAFT)return true;if(request.getStatus()==RequestStatus.RETURNED_FOR_CORRECTION)return wasFreeRoute(request);return false;}
 private boolean canEdit(WorkflowRequest request,Long actorEmployeeId){return isEditableStatus(request)&&(canSubmit(request,actorEmployeeId)||canSendFreeRoute(request,actorEmployeeId));}
 private WorkflowRequestType type(RequestType type){WorkflowRequestType t=typeRepository.findByCode(type.name()).orElseThrow(()->new IllegalStateException("Request type is not configured: "+type));if(!t.isEnabled())throw new IllegalStateException("Request type is disabled.");return t;}
 private Employee requireSelectableFreeRouteEmployee(Long employeeId){Employee e=employeeRepository.findById(employeeId).orElseThrow(()->new IllegalArgumentException("Selected employee not found."));if(e.isRestrictedVisibility())throw new IllegalArgumentException("Selected employee is not available for selection.");if(e.getEmploymentStatus()!=EmploymentStatus.ACTIVE||e.getStatus()!=RecordStatus.ACTIVE)throw new IllegalArgumentException("Selected employee is not active.");return e;}
 private void saveItems(WorkflowRequest r,List<CreateWorkflowRequestCommand.Item> items){if(items==null)return;for(var p:items){WorkflowRequestItem i=new WorkflowRequestItem();i.setWorkflowRequestId(r.getId());if(r.getRequestType()==RequestType.ASSET_REQUEST){FamAssetDTO asset=famAssetLookupService.findRequestableAsset(p.getFamAssetId());i.setExternalSourceSystem("FAM");i.setExternalAssetId(asset.assetId());i.setExternalAssetCode(asset.assetCode());i.setItemName(asset.assetName());i.setCategory(asset.category());i.setUnitOfMeasure(asset.unitOfMeasure());i.setQuantity(p.getQuantity());}else{i.setItemName(p.getItemName());i.setDescription(p.getDescription());i.setCategory(p.getCategory());i.setQuantity(p.getQuantity());i.setUnitOfMeasure(p.getUnitOfMeasure());i.setEstimatedUnitCost(p.getEstimatedUnitCost());i.setEstimatedTotalCost(p.getEstimatedUnitCost()==null?null:p.getEstimatedUnitCost().multiply(p.getQuantity()));i.setCurrency(p.getCurrency()==null?null:p.getCurrency().toUpperCase(Locale.ROOT));i.setVendorName(p.getVendorName());i.setRemarks(p.getRemarks());}itemRepository.save(i);}}
 private Set<String> requestProcessKeys(){Set<String> keys=typeRepository.findByEnabledTrueOrderByName().stream().map(WorkflowRequestType::getProcessDefinitionKey).filter(Objects::nonNull).filter(k->!k.isBlank()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));return keys.isEmpty()?Set.of("genericRequestApprovalProcess"):keys;}
 public boolean isHrAdmin(){return auth.isHumanResource()||auth.isJobHR()||auth.isAdmin()||auth.isRestrictedHr();} private void requireFinance(){if(!auth.isFinancialOfficer())throw new org.springframework.security.access.AccessDeniedException("Financial officer access is required.");} private void requireHrAdmin(){if(!isHrAdmin())throw new IllegalStateException("HR or administrator access is required.");}
 private StaffRequisitionDetailView staffRequisitionDetail(Long id){return staffRepository.findByWorkflowRequestId(id).map(d->{String departmentName=departmentRepository.findById(d.getDepartmentId()).map(Department::getName).orElse(null);String jobGradeName=d.getJobGradeId()==null?null:jobGradeRepository.findById(d.getJobGradeId()).map(JobGrade::getName).orElse(null);String replacementName=d.getReplacementEmployeeId()==null?null:employeeRepository.findById(d.getReplacementEmployeeId()).map(Employee::getFullName).orElse(null);return new StaffRequisitionDetailView(d.getJobTitle(),departmentName,jobGradeName,d.getNumberOfPositions(),d.getEmploymentType()==null?null:d.getEmploymentType().getLabel(),d.getRequisitionReason()==null?null:d.getRequisitionReason().getLabel(),d.getTargetStartDate(),d.isBudgeted(),d.getEstimatedMonthlyCost(),d.getReasonForHire(),replacementName);}).orElse(null);}
 private ExpenseReimbursementDetailView expenseReimbursementDetail(Long id){return expenseRepository.findByWorkflowRequestId(id).map(d->{String claimant=employeeRepository.findById(d.getClaimantEmployeeId()).map(Employee::getFullName).orElse(null);String department=d.getDepartmentId()==null?null:departmentRepository.findById(d.getDepartmentId()).map(Department::getName).orElse(null);List<ExpenseReimbursementItemView> items=expenseItemRepository.findByWorkflowRequestIdOrderByExpenseDateAscIdAsc(id).stream().map(i->new ExpenseReimbursementItemView(i.getExpenseDate(),i.getExpenseCategory(),i.getExpenseCategory()==null?null:i.getExpenseCategory().getLabel(),i.getDescription(),i.getVendorName(),i.getAmount(),i.getCurrency(),i.getRemarks())).toList();return new ExpenseReimbursementDetailView(claimant,department,d.getExpenseStartDate(),d.getExpenseEndDate(),d.getBusinessPurpose(),d.getPaymentMethod(),d.getPaymentMethod()==null?null:d.getPaymentMethod().getLabel(),d.getCurrency(),d.getTotalClaimAmount(),items);}).orElse(null);}

 @Transactional(readOnly = true)
 public WorkflowRequestEditDTO editDetails(Long id) {
  WorkflowRequest r = visible(id);
  Long actorId = currentEmployee().getId();
  boolean canSubmit = canSubmit(r, actorId);
  boolean canSendFreeRoute = canSendFreeRoute(r, actorId);
  boolean canEdit = canEdit(r, actorId);

  CreateWorkflowRequestCommand.StaffRequisitionPayload staffPayload = null;
  CreateWorkflowRequestCommand.FileRequestPayload filePayload = null;
  CreateWorkflowRequestCommand.AssetRequestPayload assetPayload = null;
  CreateWorkflowRequestCommand.ExpenseReimbursementPayload expensePayload = null;
  List<CreateWorkflowRequestCommand.Item> items = List.of();

  switch (r.getRequestType()) {
   case STAFF_REQUISITION -> staffPayload = staffRepository.findByWorkflowRequestId(id).map(d -> {
    CreateWorkflowRequestCommand.StaffRequisitionPayload p = new CreateWorkflowRequestCommand.StaffRequisitionPayload();
    p.setJobTitle(d.getJobTitle());
    p.setDepartmentId(d.getDepartmentId());
    p.setJobGradeId(d.getJobGradeId());
    p.setNumberOfPositions(d.getNumberOfPositions());
    p.setEmploymentType(d.getEmploymentType());
    p.setRequisitionReason(d.getRequisitionReason());
    p.setTargetStartDate(d.getTargetStartDate());
    p.setBudgeted(d.isBudgeted());
    p.setEstimatedMonthlyCost(d.getEstimatedMonthlyCost());
    p.setReasonForHire(d.getReasonForHire());
    p.setReplacementEmployeeId(d.getReplacementEmployeeId());
    return p;
   }).orElse(null);
   case FILE_REQUEST -> filePayload = fileRepository.findByWorkflowRequestId(id).map(d -> {
    CreateWorkflowRequestCommand.FileRequestPayload p = new CreateWorkflowRequestCommand.FileRequestPayload();
    p.setFileCategory(d.getFileCategory());
    p.setConfidentialityLevel(d.getConfidentialityLevel());
    p.setRequestedAccessType(d.getRequestedAccessType());
    p.setRetentionRequired(d.isRetentionRequired());
    p.setPurpose(d.getPurpose());
    return p;
   }).orElse(null);
   case ASSET_REQUEST -> {
    assetPayload = assetRepository.findByWorkflowRequestId(id).map(d -> {
     CreateWorkflowRequestCommand.AssetRequestPayload p = new CreateWorkflowRequestCommand.AssetRequestPayload();
     p.setCostCenter(d.getCostCenter());
     p.setRequiredDate(d.getRequiredDate());
     p.setBusinessJustification(d.getBusinessJustification());
     return p;
    }).orElse(null);
    items = itemRepository.findByWorkflowRequestIdOrderById(id).stream().map(i -> {
     CreateWorkflowRequestCommand.Item it = new CreateWorkflowRequestCommand.Item();
     it.setFamAssetId(i.getExternalAssetId());
     it.setItemName(i.getItemName());
     it.setCategory(i.getCategory());
     it.setUnitOfMeasure(i.getUnitOfMeasure());
     it.setQuantity(i.getQuantity());
     return it;
    }).toList();
   }
   case EXPENSE_REIMBURSEMENT -> expensePayload = expenseRepository.findByWorkflowRequestId(id).map(d -> {
    CreateWorkflowRequestCommand.ExpenseReimbursementPayload p = new CreateWorkflowRequestCommand.ExpenseReimbursementPayload();
    p.setExpenseStartDate(d.getExpenseStartDate());
    p.setExpenseEndDate(d.getExpenseEndDate());
    p.setBusinessPurpose(d.getBusinessPurpose());
    p.setPaymentMethod(d.getPaymentMethod());
    p.setCurrency(d.getCurrency());
    p.setExpenseItems(expenseItemRepository.findByWorkflowRequestIdOrderByExpenseDateAscIdAsc(id).stream().map(i -> {
     CreateWorkflowRequestCommand.ExpenseItemPayload ip = new CreateWorkflowRequestCommand.ExpenseItemPayload();
     ip.setExpenseCategory(i.getExpenseCategory());
     ip.setExpenseDate(i.getExpenseDate());
     ip.setDescription(i.getDescription());
     ip.setVendorName(i.getVendorName());
     ip.setAmount(i.getAmount());
     ip.setRemarks(i.getRemarks());
     return ip;
    }).toList());
    return p;
   }).orElse(null);
   case GENERAL_REQUEST -> { }
  }

  return WorkflowRequestEditDTO.builder()
          .id(r.getId())
          .requestNumber(r.getRequestNumber())
          .requestType(r.getRequestType())
          .title(r.getTitle())
          .description(r.getDescription())
          .priority(r.getPriority())
          .departmentId(r.getDepartmentId())
          .status(r.getStatus())
          .approvalRouteType(r.getApprovalRouteType())
          .submittedAt(r.getSubmittedAt())
          .canEdit(canEdit)
          .canSubmit(canSubmit)
          .canSendFreeRoute(canSendFreeRoute)
          .staffRequisition(staffPayload)
          .fileRequest(filePayload)
          .assetRequest(assetPayload)
          .expenseReimbursement(expensePayload)
          .items(items)
          .build();
 }

 @Transactional
 public WorkflowRequest updateEditableRequest(Long id, CreateWorkflowRequestCommand command) {
  WorkflowRequest r = owned(id);
  Employee actor = currentEmployee();
  if (!canEdit(r, actor.getId())) {
   throw new IllegalStateException("This request cannot be edited.");
  }
  if (command.getRequestType() != r.getRequestType()) {
   throw new IllegalStateException("Request type cannot be changed while editing.");
  }
  handlers.get(r.getRequestType()).validate(command);

  r.setTitle(command.getTitle().trim());
  r.setDescription(command.getDescription());
  r.setPriority(command.getPriority() == null ? RequestPriority.NORMAL : command.getPriority());

  Long departmentId = command.getDepartmentId();
  if (departmentId == null && r.getRequestType() == RequestType.STAFF_REQUISITION && command.getStaffRequisition() != null) {
   departmentId = command.getStaffRequisition().getDepartmentId();
  }
  if (departmentId == null) {
   departmentId = actor.getDepartment() == null ? null : actor.getDepartment().getId();
  }
  r.setDepartmentId(departmentId);

  r = requestRepository.save(r);
  handlers.get(r.getRequestType()).updateDetails(r, command);

  if (r.getRequestType() == RequestType.ASSET_REQUEST) {
   itemRepository.deleteByWorkflowRequestId(r.getId());
   saveItems(r, command.getItems());
  }

  activityService.record(r.getId(), RequestActivityType.UPDATED, "Request updated", actor.getId());
  return r;
 }

 // ---------------------------------------------------------------------------------------------
 // Finance final decisions (Step 4): approve / reject / return, independent of the approval route.
 // ---------------------------------------------------------------------------------------------

 /**
  * Statuses in which Finance may still make its one final decision. APPROVED / REJECTED are final,
  * and DRAFT / CANCELLED / CLOSED are deliberately NOT actionable (awaiting confirmation of the
  * Step 1 decision for those three; change this single set if that decision differs).
  */
 private static final Set<RequestStatus> FINANCE_ACTIONABLE_STATUSES =
         EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.IN_APPROVAL, RequestStatus.RETURNED_FOR_CORRECTION);

 private static final String FINANCE_FINAL_CONSTRAINT = "uk_finance_action_one_final_decision";

 @Transactional
 public WorkflowRequest financeApprove(Long id, String comment) {
  return financeDecide(id, FinanceActionType.APPROVE, comment);
 }

 @Transactional
 public WorkflowRequest financeReject(Long id, String comment) {
  return financeDecide(id, FinanceActionType.REJECT, comment);
 }

 @Transactional
 public WorkflowRequest financeReturn(Long id, String comment) {
  return financeDecide(id, FinanceActionType.RETURN, comment);
 }

 private WorkflowRequest financeDecide(Long id, FinanceActionType decision, String rawComment) {
  requireFinance();

  String comment = rawComment == null || rawComment.isBlank() ? null : rawComment.trim();
  if (comment == null && decision == FinanceActionType.REJECT) {
   throw new IllegalArgumentException("A rejection comment is required.");
  }
  if (comment == null && decision == FinanceActionType.RETURN) {
   throw new IllegalArgumentException("A return comment is required.");
  }
  if (comment != null && comment.length() > 2000) {
   throw new IllegalArgumentException("Comment must not exceed 2000 characters.");
  }

  Employee actor = currentEmployee();

  // Row lock: serialises competing Finance decisions; eligibility is checked while holding it.
  WorkflowRequest r;
  try {
   r = requestRepository.findByIdForUpdate(id)
           .orElseThrow(() -> new IllegalArgumentException("Request not found."));
  } catch (PessimisticLockingFailureException ex) {
   throw new IllegalStateException("This request is being updated by another user. Please try again.", ex);
  }

  if (r.isFinanceDecisionConsumed() || financeActionRepository.existsByWorkflowRequestIdAndFinalDecisionFlagTrue(id)) {
   throw new IllegalStateException("Finance has already made its final decision on this request.");
  }
  if (!FINANCE_ACTIONABLE_STATUSES.contains(r.getStatus())) {
   throw new IllegalStateException("Finance cannot act on a request with status " + r.getStatus() + ".");
  }

  RequestStatus statusBefore = r.getStatus();
  RequestStatus statusAfter = switch (decision) {
   case APPROVE -> RequestStatus.APPROVED;
   case REJECT -> RequestStatus.REJECTED;
   case RETURN -> RequestStatus.RETURNED_FOR_CORRECTION;
   default -> throw new IllegalArgumentException("Unsupported Finance decision: " + decision);
  };

  List<WorkflowRequestApprovalStep> pendingSteps = stepRepository.findByWorkflowRequestIdOrderBySequenceNo(id).stream()
          .filter(s -> s.getDecision() == ApprovalDecision.PENDING)
          .toList();

  // Audit row first and flushed immediately: the DB unique constraint is the second line of defence.
  WorkflowRequestFinanceAction action = new WorkflowRequestFinanceAction();
  action.setWorkflowRequestId(id);
  action.setActionType(decision);
  action.setActorEmployeeId(actor.getId());
  action.setComment(comment);
  action.setStatusBefore(statusBefore);
  action.setStatusAfter(statusAfter);
  action.setSupersededStepCount(pendingSteps.size());
  action.setFinalDecisionFlag(Boolean.TRUE);
  try {
   action = financeActionRepository.saveAndFlush(action);
  } catch (DataIntegrityViolationException ex) {
   String cause = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
   if (cause.contains(FINANCE_FINAL_CONSTRAINT)) {
    throw new IllegalStateException("Finance has already made its final decision on this request.", ex);
   }
   throw ex;
  }

  LocalDateTime now = LocalDateTime.now();

  // Close out pending configured steps; completed history is untouched.
  for (WorkflowRequestApprovalStep step : pendingSteps) {
   step.setDecision(ApprovalDecision.SUPERSEDED);
   step.setSupersededByFinanceActionId(action.getId());
   step.setSupersededAt(now);
   stepRepository.save(step);
  }

  // End the live Flowable process (and its task). deleteProcessInstance does not run the process's
  // service tasks, so the approve/reject/return delegates cannot fire a second time.
  String instanceId = r.getWorkflowInstanceId();
  if (instanceId != null) {
   if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count() > 0) {
    runtimeService.deleteProcessInstance(instanceId, "Finance override: " + decision);
   }
   r.setWorkflowInstanceId(null);
  }

  r.setStatus(statusAfter);
  if (decision == FinanceActionType.APPROVE) r.setApprovedAt(now);
  if (decision == FinanceActionType.REJECT) r.setRejectedAt(now);
  r.setFreeRouteCurrentApproverEmployeeId(null);
  r.setFinanceOverrideRecipientId(null);
  r.setFinanceDecisionConsumed(true);
  r.setFinanceDecisionType(decision);
  r.setFinanceDecisionByEmployeeId(actor.getId());
  r.setFinanceDecisionAt(now);
  WorkflowRequest saved = requestRepository.save(r);

  RequestActivityType activityType;
  String description;
  switch (decision) {
   case APPROVE -> {
    handlers.get(saved.getRequestType()).afterApproved(saved);
    activityType = RequestActivityType.FINANCE_APPROVED;
    description = "Finance approved the request";
    afterCommitExecutor.runAfterCommit(() -> requestEmailService.notifyRequestApproved(saved));
   }
   case REJECT -> {
    handlers.get(saved.getRequestType()).afterRejected(saved);
    activityType = RequestActivityType.FINANCE_REJECTED;
    description = "Finance rejected the request";
    afterCommitExecutor.runAfterCommit(() -> requestEmailService.notifyRequestRejected(saved));
   }
   default -> {
    activityType = RequestActivityType.FINANCE_RETURNED;
    description = "Finance returned the request for correction";
    afterCommitExecutor.runAfterCommit(() -> requestEmailService.notifyRequestReturnedForCorrection(saved));
   }
  }
  activityService.record(id, activityType, description, actor.getId());

  return saved;
 }

 // ---------------------------------------------------------------------------------------------
 // Finance forward (Step 5): repeatable hand-off that does NOT consume Finance's final decision,
 // plus approve / reject / return by the CURRENT Finance-forward recipient only.
 // ---------------------------------------------------------------------------------------------

 private static String trimToNull(String raw) {
  return raw == null || raw.isBlank() ? null : raw.trim();
 }

 private static void requireCommentLength(String comment) {
  if (comment != null && comment.length() > 2000) {
   throw new IllegalArgumentException("Comment must not exceed 2000 characters.");
  }
 }

 private WorkflowRequest lockRequest(Long id) {
  try {
   return requestRepository.findByIdForUpdate(id)
           .orElseThrow(() -> new IllegalArgumentException("Request not found."));
  } catch (PessimisticLockingFailureException ex) {
   throw new IllegalStateException("This request is being updated by another user. Please try again.", ex);
  }
 }

 /** Employees Finance may pick as a forward recipient (the server re-validates on forward). */
 @Transactional(readOnly = true)
 public List<RequestLookupOption> financeForwardRecipients(Long requestId) {
  requireFinance();
  Employee actor = currentEmployee();
  WorkflowRequest r = requestRepository.findById(requestId)
          .orElseThrow(() -> new IllegalArgumentException("Request not found."));
  return employeeRepository.findAllVisible().stream()
          .filter(e -> e.getEmploymentStatus() == EmploymentStatus.ACTIVE)
          .filter(e -> e.getStatus() == RecordStatus.ACTIVE)
          .filter(e -> !e.getId().equals(actor.getId()) && !e.getId().equals(r.getRequesterEmployeeId()))
          .sorted(Comparator.comparing(Employee::getFullName, String.CASE_INSENSITIVE_ORDER))
          .map(e -> new RequestLookupOption(e.getId(), e.getEmployeeNumber() + " - " + e.getFullName()
                  + (e.getDepartment() == null ? "" : " - " + e.getDepartment().getName())))
          .toList();
 }

 /** Append-only Finance/forward audit history for the Finance detail page. */
 @Transactional(readOnly = true)
 public List<WorkflowRequestFinanceActionDTO> financeActions(Long id) {
  requireFinanceRead(id);
  return financeActionRepository.findByWorkflowRequestIdOrderByCreatedAtAscIdAsc(id).stream()
          .map(a -> new WorkflowRequestFinanceActionDTO(a.getId(), a.getActionType(), a.getActorEmployeeId(),
                  a.getTargetEmployeeId(), a.getComment(), a.getStatusBefore(), a.getStatusAfter(),
                  a.getSupersededStepCount(), Boolean.TRUE.equals(a.getFinalDecisionFlag()), a.getCreatedAt()))
          .toList();
 }

 /** Requests currently handed to the logged-in employee by Finance and still awaiting their decision. */
 @Transactional(readOnly = true)
 public List<WorkflowRequest> financeForwardedToMe() {
  Long me = currentEmployee().getId();
  return requestRepository.findByFinanceOverrideRecipientIdOrderByCreatedAtDesc(me).stream()
          .filter(r -> r.getStatus() == RequestStatus.IN_APPROVAL)
          .toList();
 }

 @Transactional
 public WorkflowRequest financeForward(Long id, Long toEmployeeId, String rawComment) {
  requireFinance();
  String comment = trimToNull(rawComment);
  requireCommentLength(comment);
  if (toEmployeeId == null) {
   throw new IllegalArgumentException("A recipient employee must be selected.");
  }
  Employee actor = currentEmployee();

  WorkflowRequest r = lockRequest(id);

  if (r.isFinanceDecisionConsumed() || financeActionRepository.existsByWorkflowRequestIdAndFinalDecisionFlagTrue(id)) {
   throw new IllegalStateException("Finance has already made its final decision on this request.");
  }
  if (!FINANCE_ACTIONABLE_STATUSES.contains(r.getStatus())) {
   throw new IllegalStateException("Finance cannot act on a request with status " + r.getStatus() + ".");
  }
  if (toEmployeeId.equals(r.getRequesterEmployeeId())) {
   throw new IllegalArgumentException("You cannot forward a request to the original requester.");
  }
  if (toEmployeeId.equals(actor.getId())) {
   throw new IllegalArgumentException("You cannot forward a request to yourself.");
  }
  if (toEmployeeId.equals(r.getFinanceOverrideRecipientId())) {
   throw new IllegalStateException("This employee is already the current recipient of the request.");
  }
  requireSelectableFreeRouteEmployee(toEmployeeId);

  Long previousRecipient = r.getFinanceOverrideRecipientId();
  RequestStatus statusBefore = r.getStatus();
  // A forwarded request is active/in approval (also when it was returned), so the requester cannot
  // edit or resubmit it into the old configured route while the hand-off is pending.
  RequestStatus statusAfter = RequestStatus.IN_APPROVAL;

  List<WorkflowRequestApprovalStep> pendingSteps = stepRepository.findByWorkflowRequestIdOrderBySequenceNo(id).stream()
          .filter(s -> s.getDecision() == ApprovalDecision.PENDING)
          .toList();

  // finalDecisionFlag stays NULL: a forward never occupies the one-final-decision slot.
  WorkflowRequestFinanceAction action = new WorkflowRequestFinanceAction();
  action.setWorkflowRequestId(id);
  action.setActionType(FinanceActionType.FORWARD);
  action.setActorEmployeeId(actor.getId());
  action.setTargetEmployeeId(toEmployeeId);
  action.setComment(comment);
  action.setStatusBefore(statusBefore);
  action.setStatusAfter(statusAfter);
  action.setSupersededStepCount(pendingSteps.size());
  action.setFinalDecisionFlag(null);
  action = financeActionRepository.saveAndFlush(action);

  LocalDateTime now = LocalDateTime.now();
  for (WorkflowRequestApprovalStep step : pendingSteps) {
   step.setDecision(ApprovalDecision.SUPERSEDED);
   step.setSupersededByFinanceActionId(action.getId());
   step.setSupersededAt(now);
   stepRepository.save(step);
  }

  // Retire any live configured process/task so no original approver keeps an actionable task.
  String instanceId = r.getWorkflowInstanceId();
  if (instanceId != null) {
   if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count() > 0) {
    runtimeService.deleteProcessInstance(instanceId, "Finance override: FORWARD");
   }
   r.setWorkflowInstanceId(null);
  }

  r.setStatus(statusAfter);
  r.setFreeRouteCurrentApproverEmployeeId(null);
  r.setFinanceOverrideRecipientId(toEmployeeId);
  WorkflowRequest saved = requestRepository.save(r);

  activityService.record(id, RequestActivityType.FINANCE_FORWARDED,
          "Finance forwarded the request to employee " + toEmployeeId
                  + (previousRecipient == null ? "" : " (replacing employee " + previousRecipient + ")"),
          actor.getId());

  // Recipient / requester notices only; Finance is never emailed for a forward.
  afterCommitExecutor.runAfterCommit(() -> {
   requestEmailService.notifyRequestMovedToApprover(saved, toEmployeeId);
   requestEmailService.notifyPendingApproval(saved, toEmployeeId);
  });
  return saved;
 }

 @Transactional
 public WorkflowRequest financeForwardApprove(Long id, String comment) {
  return recipientDecide(id, FinanceActionType.RECIPIENT_APPROVE, comment);
 }

 @Transactional
 public WorkflowRequest financeForwardReject(Long id, String comment) {
  return recipientDecide(id, FinanceActionType.RECIPIENT_REJECT, comment);
 }

 @Transactional
 public WorkflowRequest financeForwardReturn(Long id, String comment) {
  return recipientDecide(id, FinanceActionType.RECIPIENT_RETURN, comment);
 }

 private WorkflowRequest recipientDecide(Long id, FinanceActionType decision, String rawComment) {
  String comment = trimToNull(rawComment);
  if (comment == null && decision == FinanceActionType.RECIPIENT_REJECT) {
   throw new IllegalArgumentException("A rejection comment is required.");
  }
  if (comment == null && decision == FinanceActionType.RECIPIENT_RETURN) {
   throw new IllegalArgumentException("A return comment is required.");
  }
  requireCommentLength(comment);

  Employee actor = currentEmployee();

  // Same row lock as Finance decisions/forwards: authority is re-checked while holding it, so a
  // stale tab of a former recipient fails after a later forward or Finance decision.
  WorkflowRequest r = lockRequest(id);

  if (r.getFinanceOverrideRecipientId() == null || !actor.getId().equals(r.getFinanceOverrideRecipientId())) {
   throw new IllegalStateException("You are not the current Finance-forward recipient of this request.");
  }
  if (r.isFinanceDecisionConsumed() || financeActionRepository.existsByWorkflowRequestIdAndFinalDecisionFlagTrue(id)) {
   throw new IllegalStateException("Finance has already made its final decision on this request.");
  }
  if (r.getStatus() != RequestStatus.IN_APPROVAL) {
   throw new IllegalStateException("This request can no longer be decided (status " + r.getStatus() + ").");
  }

  RequestStatus statusBefore = r.getStatus();
  RequestStatus statusAfter = switch (decision) {
   case RECIPIENT_APPROVE -> RequestStatus.APPROVED;
   case RECIPIENT_REJECT -> RequestStatus.REJECTED;
   case RECIPIENT_RETURN -> RequestStatus.RETURNED_FOR_CORRECTION;
   default -> throw new IllegalArgumentException("Unsupported recipient decision: " + decision);
  };

  List<WorkflowRequestApprovalStep> pendingSteps = stepRepository.findByWorkflowRequestIdOrderBySequenceNo(id).stream()
          .filter(s -> s.getDecision() == ApprovalDecision.PENDING)
          .toList();

  // Recorded as a recipient action (not a Finance decision); finalDecisionFlag stays NULL so
  // Finance's single final decision remains available after a recipient return.
  WorkflowRequestFinanceAction action = new WorkflowRequestFinanceAction();
  action.setWorkflowRequestId(id);
  action.setActionType(decision);
  action.setActorEmployeeId(actor.getId());
  action.setComment(comment);
  action.setStatusBefore(statusBefore);
  action.setStatusAfter(statusAfter);
  action.setSupersededStepCount(pendingSteps.size());
  action.setFinalDecisionFlag(null);
  action = financeActionRepository.saveAndFlush(action);

  LocalDateTime now = LocalDateTime.now();
  for (WorkflowRequestApprovalStep step : pendingSteps) {
   step.setDecision(ApprovalDecision.SUPERSEDED);
   step.setSupersededByFinanceActionId(action.getId());
   step.setSupersededAt(now);
   stepRepository.save(step);
  }

  String instanceId = r.getWorkflowInstanceId();
  if (instanceId != null) {
   if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count() > 0) {
    runtimeService.deleteProcessInstance(instanceId, "Finance-forward recipient decision: " + decision);
   }
   r.setWorkflowInstanceId(null);
  }

  r.setStatus(statusAfter);
  if (decision == FinanceActionType.RECIPIENT_APPROVE) r.setApprovedAt(now);
  if (decision == FinanceActionType.RECIPIENT_REJECT) r.setRejectedAt(now);
  r.setFreeRouteCurrentApproverEmployeeId(null);
  r.setFinanceOverrideRecipientId(null);
  // financeDecision* fields are deliberately untouched: this is not a Finance decision.
  WorkflowRequest saved = requestRepository.save(r);

  RequestActivityType activityType;
  String description;
  switch (decision) {
   case RECIPIENT_APPROVE -> {
    handlers.get(saved.getRequestType()).afterApproved(saved);
    activityType = RequestActivityType.APPROVED;
    description = "Finance-forward recipient approved the request";
    afterCommitExecutor.runAfterCommit(() -> requestEmailService.notifyRequestApproved(saved));
   }
   case RECIPIENT_REJECT -> {
    handlers.get(saved.getRequestType()).afterRejected(saved);
    activityType = RequestActivityType.REJECTED;
    description = "Finance-forward recipient rejected the request";
    afterCommitExecutor.runAfterCommit(() -> requestEmailService.notifyRequestRejected(saved));
   }
   default -> {
    activityType = RequestActivityType.RETURNED;
    description = "Finance-forward recipient returned the request for correction";
    afterCommitExecutor.runAfterCommit(() -> requestEmailService.notifyRequestReturnedForCorrection(saved));
   }
  }
  activityService.record(id, activityType, description, actor.getId());

  return saved;
 }
}