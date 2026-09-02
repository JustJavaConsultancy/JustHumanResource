package com.justjava.humanresource.documents;

import com.justjava.humanresource.communication.entity.ChatMessageAttachment;
import com.justjava.humanresource.communication.entity.GroupChatMessageAttachment;
import com.justjava.humanresource.communication.entity.HrBroadcastAttachment;
import com.justjava.humanresource.communication.repository.ChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.GroupChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.HrBroadcastAttachmentRepository;
import com.justjava.humanresource.communication.service.CommunicationAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class DocumentLibraryController {

    private final DocumentLibraryService documentLibraryService;
    private final CommunicationAttachmentService communicationAttachmentService;
    private final ChatMessageAttachmentRepository chatMessageAttachmentRepository;
    private final GroupChatMessageAttachmentRepository groupChatMessageAttachmentRepository;
    private final HrBroadcastAttachmentRepository broadcastAttachmentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/documents/library")
    public String library(Model model) {
        documentLibraryService.requireDocumentLibraryAccess();
        model.addAttribute("title", "Document Library");
        model.addAttribute("subTitle", "Search, preview, and download employee documents and request attachments");
        return "documents/library";
    }

    @GetMapping("/api/documents/library")
    @ResponseBody
    public DocumentLibraryResponse search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return documentLibraryService.search(query, sourceType, employeeId, uploadedFrom, uploadedTo, page, size);
    }

    @GetMapping("/api/documents/me")
    @ResponseBody
    public List<DocumentLibraryItemDTO> myDocuments() {
        return documentLibraryService.currentEmployeeDocuments();
    }

    @GetMapping("/api/documents/library/employees")
    @ResponseBody
    public List<DocumentLibraryEmployeeOption> employees() {
        return documentLibraryService.employeeOptions();
    }

    @PostMapping(value = "/api/documents/library/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<DocumentLibraryItemDTO> uploadLibraryDocument(
            @RequestParam Long ownerEmployeeId,
            @RequestParam String documentName,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(documentLibraryService.uploadLibraryDocument(ownerEmployeeId, documentName, file));
    }

    @PostMapping(value = "/api/documents/library/forward", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<DocumentForwardResponse> forwardDocuments(@Valid @RequestBody DocumentForwardCommand command) {
        DocumentForwardResult result = documentLibraryService.forwardDocuments(command);
        result.messages().forEach(message -> {
            messagingTemplate.convertAndSendToUser(message.recipientEmployeeNumber(), "/queue/messages", message);
            messagingTemplate.convertAndSendToUser(message.senderEmployeeNumber(), "/queue/messages", message);
        });
        return ResponseEntity.ok(result.response());
    }

    @GetMapping("/api/documents/library/direct-chat/{attachmentId}")
    public ResponseEntity<Resource> downloadDirectChatAttachment(@PathVariable Long attachmentId) {
        documentLibraryService.requireDocumentLibraryAccess();
        ChatMessageAttachment attachment = chatMessageAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), false, attachment.getStoragePath());
    }

    @GetMapping("/api/documents/library/direct-chat/{attachmentId}/view")
    public ResponseEntity<Resource> viewDirectChatAttachment(@PathVariable Long attachmentId) {
        documentLibraryService.requireDocumentLibraryAccess();
        ChatMessageAttachment attachment = chatMessageAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), true, attachment.getStoragePath());
    }

    @GetMapping("/api/documents/library/group-chat/{attachmentId}")
    public ResponseEntity<Resource> downloadGroupChatAttachment(@PathVariable Long attachmentId) {
        documentLibraryService.requireDocumentLibraryAccess();
        GroupChatMessageAttachment attachment = groupChatMessageAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), false, attachment.getStoragePath());
    }

    @GetMapping("/api/documents/library/group-chat/{attachmentId}/view")
    public ResponseEntity<Resource> viewGroupChatAttachment(@PathVariable Long attachmentId) {
        documentLibraryService.requireDocumentLibraryAccess();
        GroupChatMessageAttachment attachment = groupChatMessageAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), true, attachment.getStoragePath());
    }

    @GetMapping("/api/documents/library/broadcast/{attachmentId}")
    public ResponseEntity<Resource> downloadBroadcastAttachment(@PathVariable Long attachmentId) {
        documentLibraryService.requireDocumentLibraryAccess();
        HrBroadcastAttachment attachment = broadcastAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), false, attachment.getStoragePath());
    }

    @GetMapping("/api/documents/library/broadcast/{attachmentId}/view")
    public ResponseEntity<Resource> viewBroadcastAttachment(@PathVariable Long attachmentId) {
        documentLibraryService.requireDocumentLibraryAccess();
        HrBroadcastAttachment attachment = broadcastAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), true, attachment.getStoragePath());
    }

    private ResponseEntity<Resource> attachmentResponse(String contentType,
                                                       Long fileSize,
                                                       String filename,
                                                       boolean inline,
                                                       String storagePath) {
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(filename).build()
                : ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(communicationAttachmentService.load(storagePath));
    }
}
