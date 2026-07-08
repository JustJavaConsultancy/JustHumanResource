package com.justjava.humanresource.request.controller;
import com.justjava.humanresource.request.dto.*;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.AttachmentType;
import com.justjava.humanresource.request.repository.WorkflowRequestTypeRepository;
import com.justjava.humanresource.request.service.*;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequestMapping("/api/requests") @RequiredArgsConstructor
public class WorkflowRequestController {
 private final WorkflowRequestService service; private final WorkflowRequestAttachmentService attachments; private final WorkflowRequestTypeRepository types;
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
}
