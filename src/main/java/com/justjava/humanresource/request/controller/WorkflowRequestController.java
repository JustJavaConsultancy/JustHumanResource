package com.justjava.humanresource.request.controller;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobGradeRepository;
import com.justjava.humanresource.request.dto.*;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.*;
import com.justjava.humanresource.request.repository.WorkflowRequestTypeRepository;
import com.justjava.humanresource.request.service.*;
import com.justjava.humanresource.integration.fam.FamAssetDTO;
import com.justjava.humanresource.integration.fam.FamAssetLookupService;
import com.justjava.humanresource.integration.fam.FamIntegrationException;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Arrays;
import java.util.Comparator;

@RestController @RequestMapping("/api/requests") @RequiredArgsConstructor
public class WorkflowRequestController {
 private final WorkflowRequestService service; private final WorkflowRequestAttachmentService attachments; private final WorkflowRequestTypeRepository types; private final FamAssetLookupService famAssetLookupService; private final DepartmentRepository departmentRepository; private final JobGradeRepository jobGradeRepository; private final EmployeeRepository employeeRepository;
 @PostMapping public ResponseEntity<WorkflowRequest> create(@Valid @RequestBody CreateWorkflowRequestCommand command){return ResponseEntity.status(HttpStatus.CREATED).body(service.createDraft(command));}
 @PostMapping("/submit") public ResponseEntity<WorkflowRequest> createAndSubmit(@Valid @RequestBody CreateWorkflowRequestCommand command){return ResponseEntity.status(HttpStatus.CREATED).body(service.createAndSubmit(command));}
 @PostMapping("/{id}/submit") public WorkflowRequest submit(@PathVariable Long id){return service.submit(id);}
 @GetMapping("/me") public List<WorkflowRequest> mine(){return service.myRequests();}
 @GetMapping("/visible") public List<WorkflowRequest> visible(){return service.visibleRequests();}
 @GetMapping("/all") public List<WorkflowRequest> all(){return service.allRequests();}
 @GetMapping("/context") public java.util.Map<String,Object> context(){return service.context();}
 @GetMapping("/{id}") public WorkflowRequestDetailDTO details(@PathVariable Long id){return service.details(id);}
 @GetMapping("/types") public List<WorkflowRequestType> requestTypes(){return types.findByEnabledTrueOrderByName();}
 @GetMapping("/types/{code}") public WorkflowRequestType requestType(@PathVariable String code){return types.findByCode(code.toUpperCase()).orElseThrow(()->new IllegalArgumentException("Request type not found."));}
 @GetMapping("/staff-requisition-options") public StaffRequisitionOptions staffRequisitionOptions(){return new StaffRequisitionOptions(departmentOptions(),jobGradeOptions(),employeeOptions(),employmentTypeOptions(),requisitionReasonOptions());}
 @GetMapping("/file-options") public FileRequestOptions fileOptions(){return new FileRequestOptions(fileCategoryOptions(),confidentialityOptions(),accessTypeOptions());}
 @GetMapping("/expense-reimbursement-options") public ExpenseReimbursementOptions expenseReimbursementOptions(){return new ExpenseReimbursementOptions(expenseCategoryOptions(),paymentMethodOptions());}
 @GetMapping("/assets/catalog") public List<FamAssetDTO> assetCatalog(){try{return famAssetLookupService.listRequestableAssets();}catch(FamIntegrationException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,ex.getMessage(),ex);}}
 @GetMapping("/approvals/tasks") public List<com.justjava.humanresource.workflow.dto.FlowableTaskDTO> tasks(){return service.pendingTasks();}
 @PostMapping("/approvals/approve") public void approve(@Valid @RequestBody RequestApprovalActionCommand c){service.approve(c.getTaskId(),c.getComment());}
 @PostMapping("/approvals/reject") public void reject(@Valid @RequestBody RequestApprovalActionCommand c){service.reject(c.getTaskId(),c.getComment());}
 @PostMapping("/approvals/return") public void returnRequest(@Valid @RequestBody RequestApprovalActionCommand c){service.returnForCorrection(c.getTaskId(),c.getComment());}
 @PostMapping("/{id}/cancel") public WorkflowRequest cancel(@PathVariable Long id){return service.cancel(id);}
 @PostMapping("/{id}/close") public WorkflowRequest close(@PathVariable Long id){return service.close(id);}
 @PostMapping("/{id}/comments") public WorkflowRequestComment comment(@PathVariable Long id,@Valid @RequestBody CommentCommand c){return service.addComment(id,c.getComment(),c.isInternalOnly());}
 @PostMapping(value="/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<WorkflowRequestAttachment> upload(@PathVariable Long id,@RequestPart("file") MultipartFile file,@RequestParam(defaultValue="OTHER") AttachmentType attachmentType,@RequestParam(required=false) String description){return ResponseEntity.status(HttpStatus.CREATED).body(attachments.store(id,file,attachmentType,description,service.currentEmployee().getId()));}
 @GetMapping("/{id}/attachments/{attachmentId}") public ResponseEntity<Resource> download(@PathVariable Long id,@PathVariable Long attachmentId){service.visible(id);WorkflowRequestAttachment a=attachments.get(id,attachmentId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.getContentType())).contentLength(a.getFileSize()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(a.getOriginalFilename()).build().toString()).body(attachments.load(id,attachmentId));}
 @DeleteMapping("/{id}/attachments/{attachmentId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable Long id,@PathVariable Long attachmentId){attachments.remove(id,attachmentId,service.currentEmployee().getId());}
 @Data public static class CommentCommand { @jakarta.validation.constraints.NotBlank private String comment; private boolean internalOnly; }
 public record FileRequestOptions(List<Option> fileCategories,List<Option> confidentialityLevels,List<Option> requestedAccessTypes){}
 public record Option(String value,String label){}
 private static List<Option> fileCategoryOptions(){return Arrays.stream(FileCategory.values()).map(v->new Option(v.name(),v.getLabel())).toList();}
 private static List<Option> confidentialityOptions(){return Arrays.stream(FileConfidentialityLevel.values()).map(v->new Option(v.name(),v.getLabel())).toList();}
 private static List<Option> accessTypeOptions(){return Arrays.stream(FileAccessType.values()).map(v->new Option(v.name(),v.getLabel())).toList();}
 private List<RequestLookupOption> departmentOptions(){return departmentRepository.findAll().stream().filter(d->d.getStatus()==RecordStatus.ACTIVE).sorted(Comparator.comparing(Department::getName,String.CASE_INSENSITIVE_ORDER)).map(d->new RequestLookupOption(d.getId(),d.getCode()+" - "+d.getName())).toList();}
 private List<RequestLookupOption> jobGradeOptions(){return jobGradeRepository.findAll().stream().sorted(Comparator.comparing(JobGrade::getName,Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).map(g->new RequestLookupOption(g.getId(),g.getName()+(g.getDepartment()==null?"":" - "+g.getDepartment().getName()))).toList();}
 private List<RequestLookupOption> employeeOptions(){return employeeRepository.findAllVisible().stream().filter(e->e.getStatus()==RecordStatus.ACTIVE).sorted(Comparator.comparing(Employee::getFullName,String.CASE_INSENSITIVE_ORDER)).map(e->new RequestLookupOption(e.getId(),e.getEmployeeNumber()+" - "+e.getFullName()+(e.getDepartment()==null?"":" - "+e.getDepartment().getName()))).toList();}
 private static List<RequestEnumOption> employmentTypeOptions(){return Arrays.stream(StaffEmploymentType.values()).map(v->new RequestEnumOption(v.name(),v.getLabel())).toList();}
 private static List<RequestEnumOption> requisitionReasonOptions(){return Arrays.stream(RequisitionReason.values()).map(v->new RequestEnumOption(v.name(),v.getLabel())).toList();}
 private static List<RequestEnumOption> expenseCategoryOptions(){return Arrays.stream(ExpenseCategory.values()).map(v->new RequestEnumOption(v.name(),v.getLabel())).toList();}
 private static List<RequestEnumOption> paymentMethodOptions(){return Arrays.stream(ExpensePaymentMethod.values()).map(v->new RequestEnumOption(v.name(),v.getLabel())).toList();}
}
