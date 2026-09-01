package com.justjava.humanresource.documents;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeDocumentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.entity.WorkflowRequestAttachment;
import com.justjava.humanresource.request.repository.WorkflowRequestAttachmentRepository;
import com.justjava.humanresource.request.repository.WorkflowRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DocumentLibraryService {

    private static final String EMPLOYEE_DOCUMENT = "EMPLOYEE_DOCUMENT";
    private static final String REQUEST_ATTACHMENT = "REQUEST_ATTACHMENT";

    private final AuthenticationManager authenticationManager;
    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDocumentRepository employeeDocumentRepository;
    private final WorkflowRequestAttachmentRepository attachmentRepository;
    private final WorkflowRequestRepository requestRepository;

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

        List<DocumentLibraryItemDTO> filtered = Stream.concat(employeeDocuments.stream(), requestDocuments.stream())
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
}
