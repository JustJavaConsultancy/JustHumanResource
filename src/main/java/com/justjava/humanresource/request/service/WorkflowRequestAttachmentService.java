package com.justjava.humanresource.request.service;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.*;
import com.justjava.humanresource.request.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class WorkflowRequestAttachmentService {
 private static final Set<String> ALLOWED=Set.of("application/pdf","image/png","image/jpeg","text/plain","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
 private final WorkflowRequestAttachmentRepository repository;
 private final WorkflowRequestRepository requestRepository;
 private final WorkflowRequestActivityService activityService;
 @Value("${app.requests.storage-path:${user.home}/just-hr/request-attachments}") private String storageRoot;
 @Transactional public WorkflowRequestAttachment store(Long requestId,MultipartFile file,AttachmentType type,String description,Long actorId){WorkflowRequest request=requestRepository.findById(requestId).orElseThrow(()->new IllegalArgumentException("Request not found.")); if(!request.getRequesterEmployeeId().equals(actorId)||!(request.getStatus()==RequestStatus.DRAFT||request.getStatus()==RequestStatus.SUBMITTED||request.getStatus()==RequestStatus.RETURNED_FOR_CORRECTION)) throw new IllegalStateException("Attachments cannot be added to this request."); if(file==null||file.isEmpty()) throw new IllegalArgumentException("A non-empty file is required."); if(file.getSize()>20L*1024*1024) throw new IllegalArgumentException("File exceeds the 20 MB limit."); String content=Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"); if(!ALLOWED.contains(content)) throw new IllegalArgumentException("Unsupported file type: "+content); String original=Paths.get(Optional.ofNullable(file.getOriginalFilename()).orElse("file")).getFileName().toString(); String stored=UUID.randomUUID()+extension(original); try{Path root=Paths.get(storageRoot).toAbsolutePath().normalize();Path dir=root.resolve(String.valueOf(requestId)).normalize();if(!dir.startsWith(root))throw new IllegalStateException("Invalid storage path.");Files.createDirectories(dir);Path target=dir.resolve(stored).normalize();try(var in=file.getInputStream()){Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING);} WorkflowRequestAttachment a=new WorkflowRequestAttachment();a.setWorkflowRequestId(requestId);a.setOriginalFilename(original);a.setStoredFilename(stored);a.setStoragePath(target.toString());a.setContentType(content);a.setFileSize(file.getSize());a.setUploadedByEmployeeId(actorId);a.setUploadedAt(LocalDateTime.now());a.setAttachmentType(type==null?AttachmentType.OTHER:type);a.setDescription(description);a=repository.save(a);activityService.record(requestId,RequestActivityType.ATTACHMENT_ADDED,"Attachment added: "+original,actorId);return a;}catch(IOException ex){throw new IllegalStateException("Could not store attachment.",ex);}}
 @Transactional(readOnly=true) public Resource load(Long requestId,Long attachmentId){WorkflowRequestAttachment a=get(requestId,attachmentId);Path path=Paths.get(a.getStoragePath()).normalize();Resource r=new FileSystemResource(path);if(!r.exists())throw new IllegalStateException("Attachment file is missing.");return r;}
 @Transactional public void remove(Long requestId,Long attachmentId,Long actorId){WorkflowRequest request=requestRepository.findById(requestId).orElseThrow(()->new IllegalArgumentException("Request not found."));if(!request.getRequesterEmployeeId().equals(actorId)||!(request.getStatus()==RequestStatus.DRAFT||request.getStatus()==RequestStatus.RETURNED_FOR_CORRECTION))throw new IllegalStateException("Attachment cannot be removed.");WorkflowRequestAttachment a=get(requestId,attachmentId);try{Files.deleteIfExists(Paths.get(a.getStoragePath()));}catch(IOException ex){throw new IllegalStateException("Could not delete attachment.",ex);}repository.delete(a);activityService.record(requestId,RequestActivityType.ATTACHMENT_REMOVED,"Attachment removed: "+a.getOriginalFilename(),actorId);}
 public WorkflowRequestAttachment get(Long requestId,Long id){WorkflowRequestAttachment a=repository.findById(id).orElseThrow(()->new IllegalArgumentException("Attachment not found."));if(!a.getWorkflowRequestId().equals(requestId))throw new IllegalArgumentException("Attachment does not belong to request.");return a;}
 private String extension(String name){int i=name.lastIndexOf('.');if(i<0)return "";String ext=name.substring(i).toLowerCase(Locale.ROOT);return ext.length()<=10?ext:"";}
}
