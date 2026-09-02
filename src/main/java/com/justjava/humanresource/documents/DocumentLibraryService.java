package com.justjava.humanresource.documents;

import com.justjava.humanresource.communication.dto.ChatMessageResponse;
import com.justjava.humanresource.communication.entity.ChatMessage;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.communication.entity.ChatMessageAttachment;
import com.justjava.humanresource.communication.entity.GroupChatMessageAttachment;
import com.justjava.humanresource.communication.entity.HrBroadcastAttachment;
import com.justjava.humanresource.communication.repository.ChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.GroupChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.HrBroadcastAttachmentRepository;
import com.justjava.humanresource.communication.service.CommunicationAttachmentService;
import com.justjava.humanresource.communication.service.CommunicationService;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeeDocument;
import com.justjava.humanresource.hr.repository.EmployeeDocumentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeDocumentService;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.entity.WorkflowRequestAttachment;
import com.justjava.humanresource.request.repository.WorkflowRequestAttachmentRepository;
import com.justjava.humanresource.request.repository.WorkflowRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DocumentLibraryService {

    private static final String EMPLOYEE_DOCUMENT = "EMPLOYEE_DOCUMENT";
    private static final String REQUEST_ATTACHMENT = "REQUEST_ATTACHMENT";
    private static final String DIRECT_CHAT_ATTACHMENT = "DIRECT_CHAT_ATTACHMENT";
    private static final String GROUP_CHAT_ATTACHMENT = "GROUP_CHAT_ATTACHMENT";
    private static final String BROADCAST_ATTACHMENT = "BROADCAST_ATTACHMENT";

    private final AuthenticationManager authenticationManager;
    private final EmployeeService employeeService;
    private final EmployeeDocumentService employeeDocumentService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDocumentRepository employeeDocumentRepository;
    private final WorkflowRequestAttachmentRepository attachmentRepository;
    private final WorkflowRequestRepository requestRepository;
    private final ChatMessageAttachmentRepository chatMessageAttachmentRepository;
    private final GroupChatMessageAttachmentRepository groupChatMessageAttachmentRepository;
    private final HrBroadcastAttachmentRepository broadcastAttachmentRepository;
    private final CommunicationService communicationService;
    private final CommunicationAttachmentService communicationAttachmentService;

    @Transactional(readOnly = true)
    public DocumentLibraryResponse search(String query,
                                          String sourceType,
                                          Long employeeId,
                                          LocalDate uploadedFrom,
                                          LocalDate uploadedTo,
                                          int page,
                                          int size) {
        requireDocumentLibraryAccess();

        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        String normalizedQuery = normalize(query);
        String normalizedSource = normalize(sourceType);
        LocalDateTime from = uploadedFrom == null ? null : uploadedFrom.atStartOfDay();
        LocalDateTime to = uploadedTo == null ? null : uploadedTo.atTime(LocalTime.MAX);

        List<DocumentLibraryItemDTO> employeeDocuments = shouldIncludeSource(normalizedSource, EMPLOYEE_DOCUMENT)
                ? employeeDocumentRepository.findAllForDocumentLibrary().stream()
                .map(this::fromEmployeeDocument)
                .toList()
                : List.of();

        List<WorkflowRequestAttachment> attachments = shouldIncludeSource(normalizedSource, REQUEST_ATTACHMENT)
                ? attachmentRepository.findAllByOrderByUploadedAtDesc()
                : List.of();
        Map<Long, WorkflowRequest> requestsById = attachments.isEmpty()
                ? Map.of()
                : requestRepository.findAllById(attachments.stream()
                        .map(WorkflowRequestAttachment::getWorkflowRequestId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(WorkflowRequest::getId, Function.identity()));
        Map<Long, Employee> requestersById = requestsById.isEmpty()
                ? Map.of()
                : employeeRepository.findAllById(requestsById.values().stream()
                .map(WorkflowRequest::getRequesterEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));

        List<DocumentLibraryItemDTO> requestDocuments = attachments.stream()
                .map(attachment -> {
                    WorkflowRequest request = requestsById.get(attachment.getWorkflowRequestId());
                    Employee requester = request == null ? null : requestersById.get(request.getRequesterEmployeeId());
                    return fromAttachment(attachment, request, requester);
                })
                .filter(Objects::nonNull)
                .toList();

        List<DocumentLibraryItemDTO> directChatDocuments = shouldIncludeSource(normalizedSource, DIRECT_CHAT_ATTACHMENT)
                ? chatMessageAttachmentRepository.findAllByOrderByUploadedAtDesc().stream()
                .map(this::fromDirectChatAttachment)
                .toList()
                : List.of();

        List<DocumentLibraryItemDTO> groupChatDocuments = shouldIncludeSource(normalizedSource, GROUP_CHAT_ATTACHMENT)
                ? groupChatMessageAttachmentRepository.findAllByOrderByUploadedAtDesc().stream()
                .map(this::fromGroupChatAttachment)
                .toList()
                : List.of();

        List<DocumentLibraryItemDTO> broadcastDocuments = shouldIncludeSource(normalizedSource, BROADCAST_ATTACHMENT)
                ? broadcastAttachmentRepository.findAllByOrderByUploadedAtDesc().stream()
                .map(this::fromBroadcastAttachment)
                .toList()
                : List.of();

        List<DocumentLibraryItemDTO> filtered = Stream.of(
                        employeeDocuments.stream(),
                        requestDocuments.stream(),
                        directChatDocuments.stream(),
                        groupChatDocuments.stream(),
                        broadcastDocuments.stream()
                )
                .flatMap(Function.identity())
                .filter(document -> employeeId == null || Objects.equals(document.ownerEmployeeId(), employeeId))
                .filter(document -> from == null || !safeUploadedAt(document).isBefore(from))
                .filter(document -> to == null || !safeUploadedAt(document).isAfter(to))
                .filter(document -> matchesQuery(document, normalizedQuery))
                .sorted(Comparator.comparing(DocumentLibraryItemDTO::uploadedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int fromIndex = Math.min(normalizedPage * normalizedSize, filtered.size());
        int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / normalizedSize);

        return new DocumentLibraryResponse(
                filtered.subList(fromIndex, toIndex),
                filtered.size(),
                normalizedPage,
                normalizedSize,
                totalPages
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentLibraryItemDTO> currentEmployeeDocuments() {
        Employee employee = currentEmployee();
        return employeeDocumentRepository.findByEmployeeIdForDocumentLibrary(employee.getId()).stream()
                .map(this::fromEmployeeDocument)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentLibraryEmployeeOption> employeeOptions() {
        requireDocumentLibraryAccess();
        return employeeRepository.findAllVisible().stream()
                .filter(employee -> employee.getEmploymentStatus() == EmploymentStatus.ACTIVE)
                .sorted(Comparator.comparing(Employee::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(employee -> new DocumentLibraryEmployeeOption(
                        employee.getId(),
                        employee.getEmployeeNumber(),
                        employee.getFullName(),
                        employee.getDepartment() == null ? "Unassigned" : employee.getDepartment().getName(),
                        employee.getEmail()
                ))
                .toList();
    }

    @Transactional
    public DocumentLibraryItemDTO uploadLibraryDocument(Long ownerEmployeeId, String documentName, MultipartFile file) {
        requireDocumentLibraryAccess();
        if (ownerEmployeeId == null) {
            throw new IllegalArgumentException("An owner employee is required.");
        }
        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException("Document name is required.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required.");
        }
        try {
            return fromEmployeeDocument(employeeDocumentService.uploadDocument(ownerEmployeeId, documentName.trim(), file));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not upload document.", ex);
        }
    }

    @Transactional
    public DocumentForwardResult forwardDocuments(DocumentForwardCommand command) {
        requireDocumentLibraryAccess();
        List<DocumentLibraryDocumentRef> refs = command == null ? List.of() : command.documents();
        if (refs == null || refs.isEmpty()) {
            throw new IllegalArgumentException("Select at least one document to forward.");
        }
        if (refs.size() > 5) {
            throw new IllegalArgumentException("You can forward up to 5 documents at a time.");
        }

        List<ResolvedLibraryDocument> documents = refs.stream()
                .map(this::resolveDocument)
                .toList();
        List<Employee> recipients = resolveRecipients(command);
        String message = normalizeForwardMessage(command.message(), documents);

        List<ChatMessageResponse> responses = new ArrayList<>();
        for (Employee recipient : recipients) {
            ChatMessage chatMessage = communicationService.createHrDirectMessageForForward(recipient.getId(), message);
            List<ChatMessageAttachment> attachments = documents.stream()
                    .map(document -> communicationAttachmentService.storeDirectAttachmentCopy(
                            chatMessage,
                            document.originalFilename(),
                            document.contentType(),
                            document.fileSize(),
                            chatMessage.getSender().getId(),
                            document.bytes()
                    ))
                    .toList();
            responses.add(communicationService.toDirectMessageResponse(chatMessage, attachments));
        }

        return new DocumentForwardResult(
                new DocumentForwardResponse(documents.size(), recipients.size(), responses.size()),
                responses
        );
    }

    public void requireDocumentLibraryAccess() {
        if (!(authenticationManager.isHumanResource() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("Only HR and admin users can access the document library.");
        }
    }

    private Employee currentEmployee() {
        String email = (String) authenticationManager.get("email");
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Unable to resolve logged-in employee.");
        }
        return employeeService.getByEmail(email);
    }

    private DocumentLibraryItemDTO fromEmployeeDocument(EmployeeDocumentLibraryRow row) {
        return new DocumentLibraryItemDTO(
                row.id(),
                EMPLOYEE_DOCUMENT,
                coalesce(row.documentName(), row.fileName()),
                row.fileName(),
                row.fileType(),
                null,
                row.uploadedAt(),
                row.uploadedBy(),
                row.employeeId(),
                row.employeeName(),
                row.employeeNumber(),
                null,
                null,
                null,
                null,
                row.documentName(),
                null,
                null,
                "/documents/view/" + row.id(),
                "/documents/download/" + row.id()
        );
    }

    private DocumentLibraryItemDTO fromAttachment(WorkflowRequestAttachment attachment, WorkflowRequest request, Employee requester) {
        if (request == null) {
            return null;
        }
        return new DocumentLibraryItemDTO(
                attachment.getId(),
                REQUEST_ATTACHMENT,
                coalesce(attachment.getDescription(), attachment.getOriginalFilename()),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                attachment.getUploadedByEmployeeId() == null ? null : String.valueOf(attachment.getUploadedByEmployeeId()),
                request.getRequesterEmployeeId(),
                requester == null ? null : requester.getFullName(),
                requester == null ? null : requester.getEmployeeNumber(),
                request.getId(),
                request.getRequestNumber(),
                request.getRequestType() == null ? null : request.getRequestType().name(),
                request.getStatus() == null ? null : request.getStatus().name(),
                null,
                attachment.getAttachmentType() == null ? null : attachment.getAttachmentType().name(),
                attachment.getDescription(),
                "/api/requests/" + request.getId() + "/attachments/" + attachment.getId() + "/view",
                "/api/requests/" + request.getId() + "/attachments/" + attachment.getId()
        );
    }

    private DocumentLibraryItemDTO fromEmployeeDocument(EmployeeDocument document) {
        Employee employee = document.getEmployee();
        return new DocumentLibraryItemDTO(
                document.getId(),
                EMPLOYEE_DOCUMENT,
                coalesce(document.getDocumentName(), document.getFileName()),
                document.getFileName(),
                document.getFileType(),
                document.getFileData() == null ? null : (long) document.getFileData().length,
                document.getUploadedAt(),
                document.getUploadedBy(),
                employee == null ? null : employee.getId(),
                employee == null ? null : employee.getFullName(),
                employee == null ? null : employee.getEmployeeNumber(),
                null,
                null,
                null,
                null,
                document.getDocumentName(),
                null,
                null,
                "/documents/view/" + document.getId(),
                "/documents/download/" + document.getId()
        );
    }

    private DocumentLibraryItemDTO fromDirectChatAttachment(ChatMessageAttachment attachment) {
        Employee sender = attachment.getMessage().getSender();
        Employee recipient = attachment.getMessage().getRecipient();
        return new DocumentLibraryItemDTO(
                attachment.getId(),
                DIRECT_CHAT_ATTACHMENT,
                attachment.getOriginalFilename(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                attachment.getUploadedByEmployeeId() == null ? null : String.valueOf(attachment.getUploadedByEmployeeId()),
                sender == null ? attachment.getUploadedByEmployeeId() : sender.getId(),
                sender == null ? null : sender.getFullName(),
                sender == null ? null : sender.getEmployeeNumber(),
                null,
                null,
                null,
                null,
                null,
                "Direct chat",
                recipient == null ? "Direct message attachment" : "Direct message to " + recipient.getFullName(),
                "/api/documents/library/direct-chat/" + attachment.getId() + "/view",
                "/api/documents/library/direct-chat/" + attachment.getId()
        );
    }

    private DocumentLibraryItemDTO fromGroupChatAttachment(GroupChatMessageAttachment attachment) {
        Employee sender = attachment.getMessage().getSender();
        String groupName = attachment.getMessage().getChatGroup() == null ? null : attachment.getMessage().getChatGroup().getName();
        return new DocumentLibraryItemDTO(
                attachment.getId(),
                GROUP_CHAT_ATTACHMENT,
                attachment.getOriginalFilename(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                attachment.getUploadedByEmployeeId() == null ? null : String.valueOf(attachment.getUploadedByEmployeeId()),
                sender == null ? attachment.getUploadedByEmployeeId() : sender.getId(),
                sender == null ? null : sender.getFullName(),
                sender == null ? null : sender.getEmployeeNumber(),
                null,
                null,
                null,
                null,
                null,
                "Group chat",
                groupName == null ? "Group message attachment" : "Group message in " + groupName,
                "/api/documents/library/group-chat/" + attachment.getId() + "/view",
                "/api/documents/library/group-chat/" + attachment.getId()
        );
    }

    private DocumentLibraryItemDTO fromBroadcastAttachment(HrBroadcastAttachment attachment) {
        String title = attachment.getBroadcast() == null ? null : attachment.getBroadcast().getTitle();
        return new DocumentLibraryItemDTO(
                attachment.getId(),
                BROADCAST_ATTACHMENT,
                attachment.getOriginalFilename(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                attachment.getUploadedByEmail(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                title,
                "HR broadcast",
                title,
                "/api/documents/library/broadcast/" + attachment.getId() + "/view",
                "/api/documents/library/broadcast/" + attachment.getId()
        );
    }

    private boolean shouldIncludeSource(String sourceType, String candidate) {
        return sourceType == null || sourceType.isBlank() || normalize(candidate).equals(sourceType);
    }

    private boolean matchesQuery(DocumentLibraryItemDTO document, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return Stream.of(
                        document.displayName(),
                        document.originalFileName(),
                        document.contentType(),
                        document.uploadedBy(),
                        document.ownerEmployeeName(),
                        document.employeeNumber(),
                        document.requestNumber(),
                        document.requestType(),
                        document.requestStatus(),
                        document.documentName(),
                        document.attachmentType(),
                        document.description()
                )
                .filter(Objects::nonNull)
                .map(this::normalize)
                .anyMatch(value -> value.contains(query));
    }

    private LocalDateTime safeUploadedAt(DocumentLibraryItemDTO document) {
        return document.uploadedAt() == null ? LocalDateTime.MIN : document.uploadedAt();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String coalesce(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private List<Employee> resolveRecipients(DocumentForwardCommand command) {
        if (command.allEmployees()) {
            return employeeRepository.findAllVisible().stream()
                    .filter(employee -> employee.getEmploymentStatus() == EmploymentStatus.ACTIVE)
                    .filter(employee -> !Objects.equals(employee.getId(), currentEmployee().getId()))
                    .toList();
        }

        Set<Long> recipientIds = command.recipientEmployeeIds() == null
                ? Set.of()
                : new LinkedHashSet<>(command.recipientEmployeeIds());
        if (recipientIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one recipient or choose all employees.");
        }
        List<Employee> recipients = employeeRepository.findAllById(recipientIds).stream()
                .filter(employee -> employee.getEmploymentStatus() == EmploymentStatus.ACTIVE)
                .filter(employee -> !employee.isRestrictedVisibility())
                .filter(employee -> !Objects.equals(employee.getId(), currentEmployee().getId()))
                .toList();
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("No active visible recipients were found.");
        }
        return recipients;
    }

    private String normalizeForwardMessage(String value, List<ResolvedLibraryDocument> documents) {
        String message = value == null ? "" : value.trim();
        if (message.isBlank()) {
            message = documents.size() == 1
                    ? "A document has been forwarded to you: " + documents.getFirst().displayName()
                    : documents.size() + " documents have been forwarded to you.";
        }
        if (message.length() > 2000) {
            throw new IllegalArgumentException("Message is too long.");
        }
        return message;
    }

    private ResolvedLibraryDocument resolveDocument(DocumentLibraryDocumentRef ref) {
        String source = ref == null ? null : normalize(ref.sourceType());
        Long id = ref == null ? null : ref.id();
        if (source == null || id == null) {
            throw new IllegalArgumentException("Document source and ID are required.");
        }
        return switch (source) {
            case "employee_document" -> employeeDocumentRepository.findById(id)
                    .map(document -> new ResolvedLibraryDocument(
                            coalesce(document.getDocumentName(), document.getFileName()),
                            document.getFileName(),
                            document.getFileType(),
                            document.getFileData() == null ? 0L : (long) document.getFileData().length,
                            document.getFileData()
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Employee document not found: " + id));
            case "request_attachment" -> attachmentRepository.findById(id)
                    .map(attachment -> new ResolvedLibraryDocument(
                            coalesce(attachment.getDescription(), attachment.getOriginalFilename()),
                            attachment.getOriginalFilename(),
                            attachment.getContentType(),
                            attachment.getFileSize(),
                            readFile(attachment.getStoragePath())
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Request attachment not found: " + id));
            case "direct_chat_attachment" -> chatMessageAttachmentRepository.findById(id)
                    .map(attachment -> new ResolvedLibraryDocument(
                            attachment.getOriginalFilename(),
                            attachment.getOriginalFilename(),
                            attachment.getContentType(),
                            attachment.getFileSize(),
                            readFile(attachment.getStoragePath())
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Direct chat attachment not found: " + id));
            case "group_chat_attachment" -> groupChatMessageAttachmentRepository.findById(id)
                    .map(attachment -> new ResolvedLibraryDocument(
                            attachment.getOriginalFilename(),
                            attachment.getOriginalFilename(),
                            attachment.getContentType(),
                            attachment.getFileSize(),
                            readFile(attachment.getStoragePath())
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Group chat attachment not found: " + id));
            case "broadcast_attachment" -> broadcastAttachmentRepository.findById(id)
                    .map(attachment -> new ResolvedLibraryDocument(
                            attachment.getOriginalFilename(),
                            attachment.getOriginalFilename(),
                            attachment.getContentType(),
                            attachment.getFileSize(),
                            readFile(attachment.getStoragePath())
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Broadcast attachment not found: " + id));
            default -> throw new IllegalArgumentException("Unsupported document source: " + ref.sourceType());
        };
    }

    private byte[] readFile(String storagePath) {
        try {
            return Files.readAllBytes(Paths.get(storagePath).normalize());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read document file.", ex);
        }
    }

    private record ResolvedLibraryDocument(
            String displayName,
            String originalFilename,
            String contentType,
            Long fileSize,
            byte[] bytes
    ) {
    }
}
