package com.justjava.humanresource.documents;

import com.justjava.humanresource.communication.entity.ChatGroup;
import com.justjava.humanresource.communication.entity.ChatMessage;
import com.justjava.humanresource.communication.entity.ChatMessageAttachment;
import com.justjava.humanresource.communication.entity.Conversation;
import com.justjava.humanresource.communication.entity.GroupChatMessage;
import com.justjava.humanresource.communication.entity.GroupChatMessageAttachment;
import com.justjava.humanresource.communication.entity.HrBroadcast;
import com.justjava.humanresource.communication.entity.HrBroadcastAttachment;
import com.justjava.humanresource.communication.repository.ChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.GroupChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.HrBroadcastAttachmentRepository;
import com.justjava.humanresource.communication.service.CommunicationAttachmentService;
import com.justjava.humanresource.communication.service.CommunicationService;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeDocumentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeDocumentService;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.request.repository.WorkflowRequestAttachmentRepository;
import com.justjava.humanresource.request.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentLibraryServiceTest {

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final EmployeeDocumentService employeeDocumentService = mock(EmployeeDocumentService.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final EmployeeDocumentRepository employeeDocumentRepository = mock(EmployeeDocumentRepository.class);
    private final WorkflowRequestAttachmentRepository attachmentRepository = mock(WorkflowRequestAttachmentRepository.class);
    private final WorkflowRequestRepository requestRepository = mock(WorkflowRequestRepository.class);
    private final ChatMessageAttachmentRepository chatMessageAttachmentRepository = mock(ChatMessageAttachmentRepository.class);
    private final GroupChatMessageAttachmentRepository groupChatMessageAttachmentRepository = mock(GroupChatMessageAttachmentRepository.class);
    private final HrBroadcastAttachmentRepository broadcastAttachmentRepository = mock(HrBroadcastAttachmentRepository.class);
    private final CommunicationService communicationService = mock(CommunicationService.class);
    private final CommunicationAttachmentService communicationAttachmentService = mock(CommunicationAttachmentService.class);

    private final DocumentLibraryService service = new DocumentLibraryService(
            authenticationManager,
            employeeService,
            employeeDocumentService,
            employeeRepository,
            employeeDocumentRepository,
            attachmentRepository,
            requestRepository,
            chatMessageAttachmentRepository,
            groupChatMessageAttachmentRepository,
            broadcastAttachmentRepository,
            communicationService,
            communicationAttachmentService
    );

    @Test
    void searchRejectsNonHrAndNonAdminUsers() {
        assertThrows(AccessDeniedException.class,
                () -> service.search(null, null, null, null, null, 0, 25));

        verify(employeeDocumentRepository, never()).findAllForDocumentLibrary();
        verify(attachmentRepository, never()).findAllByOrderByUploadedAtDesc();
        verify(chatMessageAttachmentRepository, never()).findAllByOrderByUploadedAtDesc();
        verify(groupChatMessageAttachmentRepository, never()).findAllByOrderByUploadedAtDesc();
        verify(broadcastAttachmentRepository, never()).findAllByOrderByUploadedAtDesc();
    }

    @Test
    void searchAllowsHumanResourceUsers() {
        when(authenticationManager.isHumanResource()).thenReturn(true);
        when(employeeDocumentRepository.findAllForDocumentLibrary()).thenReturn(List.of(
                new EmployeeDocumentLibraryRow(
                        10L,
                        "Contract",
                        "contract.pdf",
                        "application/pdf",
                        LocalDateTime.now(),
                        "humanResource",
                        2L,
                        "Jane Cooper",
                        "EMP-002",
                        3L
                )
        ));
        when(attachmentRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of());
        when(chatMessageAttachmentRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of());
        when(groupChatMessageAttachmentRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of());
        when(broadcastAttachmentRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of());

        DocumentLibraryResponse response = service.search("contract", null, null, null, null, 0, 25);

        assertEquals(1, response.totalElements());
        assertEquals("EMPLOYEE_DOCUMENT", response.documents().getFirst().sourceType());
    }

    @Test
    void currentEmployeeDocumentsOnlyLoadsLoggedInEmployeeDocuments() {
        Employee employee = new Employee();
        employee.setId(7L);

        when(authenticationManager.get("email")).thenReturn("employee@test.com");
        when(employeeService.getByEmail("employee@test.com")).thenReturn(employee);
        when(employeeDocumentRepository.findByEmployeeIdForDocumentLibrary(7L)).thenReturn(List.of(
                new EmployeeDocumentLibraryRow(
                        14L,
                        "ID Card",
                        "id-card.png",
                        "image/png",
                        LocalDateTime.now(),
                        "humanResource",
                        7L,
                        "Employee User",
                        "EMP-007",
                        null
                )
        ));

        List<DocumentLibraryItemDTO> documents = service.currentEmployeeDocuments();

        assertEquals(1, documents.size());
        assertEquals(7L, documents.getFirst().ownerEmployeeId());
        verify(employeeDocumentRepository).findByEmployeeIdForDocumentLibrary(7L);
    }

    @Test
    void searchIncludesDirectChatAttachments() {
        when(authenticationManager.isHumanResource()).thenReturn(true);
        when(chatMessageAttachmentRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(directChatAttachment()));

        DocumentLibraryResponse response = service.search("direct-note", "DIRECT_CHAT_ATTACHMENT", null, null, null, 0, 25);

        assertEquals(1, response.totalElements());
        DocumentLibraryItemDTO document = response.documents().getFirst();
        assertEquals("DIRECT_CHAT_ATTACHMENT", document.sourceType());
        assertEquals("/api/documents/library/direct-chat/30/view", document.viewUrl());
        assertEquals(1L, document.ownerEmployeeId());
    }

    @Test
    void searchIncludesGroupChatAttachments() {
        when(authenticationManager.isHumanResource()).thenReturn(true);
        when(groupChatMessageAttachmentRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(groupChatAttachment()));

        DocumentLibraryResponse response = service.search("Finance Team", "GROUP_CHAT_ATTACHMENT", null, null, null, 0, 25);

        assertEquals(1, response.totalElements());
        DocumentLibraryItemDTO document = response.documents().getFirst();
        assertEquals("GROUP_CHAT_ATTACHMENT", document.sourceType());
        assertEquals("Group message in Finance Team", document.description());
    }

    @Test
    void searchIncludesBroadcastAttachments() {
        when(authenticationManager.isHumanResource()).thenReturn(true);
        when(broadcastAttachmentRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(broadcastAttachment()));

        DocumentLibraryResponse response = service.search("Policy Update", "BROADCAST_ATTACHMENT", null, null, null, 0, 25);

        assertEquals(1, response.totalElements());
        DocumentLibraryItemDTO document = response.documents().getFirst();
        assertEquals("BROADCAST_ATTACHMENT", document.sourceType());
        assertEquals("Policy Update", document.documentName());
        assertEquals("/api/documents/library/broadcast/50", document.downloadUrl());
    }

    private ChatMessageAttachment directChatAttachment() {
        Employee sender = employee(1L, "Ada Sender", "EMP-001");
        Employee recipient = employee(2L, "Ben Receiver", "EMP-002");
        Conversation conversation = new Conversation(sender, recipient);
        conversation.setId(20L);
        ChatMessage message = new ChatMessage();
        message.setId(21L);
        message.setConversation(conversation);
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent("");

        ChatMessageAttachment attachment = new ChatMessageAttachment();
        attachment.setId(30L);
        attachment.setMessage(message);
        attachment.setOriginalFilename("direct-note.pdf");
        attachment.setContentType("application/pdf");
        attachment.setFileSize(1024L);
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setUploadedByEmployeeId(sender.getId());
        attachment.setStoragePath("direct-note.pdf");
        return attachment;
    }

    private GroupChatMessageAttachment groupChatAttachment() {
        Employee sender = employee(3L, "Cara Sender", "EMP-003");
        ChatGroup group = new ChatGroup();
        group.setId(40L);
        group.setName("Finance Team");
        GroupChatMessage message = new GroupChatMessage();
        message.setId(41L);
        message.setChatGroup(group);
        message.setSender(sender);
        message.setContent("");

        GroupChatMessageAttachment attachment = new GroupChatMessageAttachment();
        attachment.setId(42L);
        attachment.setMessage(message);
        attachment.setOriginalFilename("group-sheet.xlsx");
        attachment.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        attachment.setFileSize(2048L);
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setUploadedByEmployeeId(sender.getId());
        attachment.setStoragePath("group-sheet.xlsx");
        return attachment;
    }

    private HrBroadcastAttachment broadcastAttachment() {
        HrBroadcast broadcast = new HrBroadcast();
        broadcast.setId(49L);
        broadcast.setTitle("Policy Update");
        broadcast.setContent("Updated policy");

        HrBroadcastAttachment attachment = new HrBroadcastAttachment();
        attachment.setId(50L);
        attachment.setBroadcast(broadcast);
        attachment.setOriginalFilename("policy.pdf");
        attachment.setContentType("application/pdf");
        attachment.setFileSize(4096L);
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setUploadedByEmail("hr@test.com");
        attachment.setStoragePath("policy.pdf");
        return attachment;
    }

    private Employee employee(Long id, String fullName, String employeeNumber) {
        String[] parts = fullName.split(" ", 2);
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFirstName(parts[0]);
        employee.setLastName(parts.length > 1 ? parts[1] : "");
        employee.setEmployeeNumber(employeeNumber);
        return employee;
    }
}
