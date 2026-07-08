package com.justjava.humanresource.request.handler;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.repository.FileRequestDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class FileRequestHandler implements WorkflowRequestTypeHandler {
 private final FileRequestDetailRepository repository;
 public RequestType supportedType(){return RequestType.FILE_REQUEST;}
 public void validate(CreateWorkflowRequestCommand c){if(c.getFileRequest()==null) throw new IllegalArgumentException("File request details are required.");}
 public void saveDetails(WorkflowRequest r,CreateWorkflowRequestCommand c){var p=c.getFileRequest(); FileRequestDetail d=new FileRequestDetail(); d.setWorkflowRequestId(r.getId()); d.setFileCategory(p.getFileCategory()); d.setConfidentialityLevel(p.getConfidentialityLevel()); d.setRequestedAccessType(p.getRequestedAccessType()); d.setRetentionRequired(p.isRetentionRequired()); d.setPurpose(p.getPurpose()); repository.save(d);}
}
