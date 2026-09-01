package com.justjava.humanresource.documents;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeDocumentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
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
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final EmployeeDocumentRepository employeeDocumentRepository = mock(EmployeeDocumentRepository.class);
    private final WorkflowRequestAttachmentRepository attachmentRepository = mock(WorkflowRequestAttachmentRepository.class);
    private final WorkflowRequestRepository requestRepository = mock(WorkflowRequestRepository.class);

    private final DocumentLibraryService service = new DocumentLibraryService(
            authenticationManager,
            employeeService,
            employeeRepository,
            employeeDocumentRepository,
            attachmentRepository,
            requestRepository
    );

    @Test
    void searchRejectsNonHrAndNonAdminUsers() {
        assertThrows(AccessDeniedException.class,
                () -> service.search(null, null, null, null, null, 0, 25));

        verify(employeeDocumentRepository, never()).findAllForDocumentLibrary();
        verify(attachmentRepository, never()).findAllByOrderByUploadedAtDesc();
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
}
