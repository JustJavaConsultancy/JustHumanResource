package com.justjava.humanresource.recruitment;

import com.justjava.humanresource.recruitment.dto.PublicApplicationCommand;
import com.justjava.humanresource.recruitment.entity.JobOpening;
import com.justjava.humanresource.recruitment.enums.JobOpeningStatus;
import com.justjava.humanresource.recruitment.repository.*;
import com.justjava.humanresource.recruitment.service.*;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.orgStructure.entity.Company;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.*;
import com.justjava.humanresource.request.repository.StaffRequisitionDetailRepository;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecruitmentServiceTest {
    @Mock JobOpeningRepository openingRepository; @Mock CandidateRepository candidateRepository;
    @Mock JobApplicationRepository applicationRepository; @Mock ApplicationStageHistoryRepository historyRepository;
    @Mock StaffRequisitionDetailRepository staffRepository; @Mock DepartmentRepository departmentRepository;
    @Mock RuntimeService runtimeService;
    RecruitmentService service;

    @BeforeEach void setUp() {
        service = new RecruitmentService(openingRepository, candidateRepository, applicationRepository,
                historyRepository, staffRepository, departmentRepository, new RecruitmentNumberService(), runtimeService);
    }

    @Test void approvedStaffRequisitionCreatesExactlyOneWorkflowBackedOpening() {
        WorkflowRequest request = request(); StaffRequisitionDetail detail = detail();
        when(openingRepository.findByStaffRequisitionRequestId(10L)).thenReturn(Optional.empty());
        when(staffRepository.findByWorkflowRequestId(10L)).thenReturn(Optional.of(detail));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(department(7L)));
        when(openingRepository.saveAndFlush(any())).thenAnswer(i -> { JobOpening j=i.getArgument(0); j.setId(20L); return j; });
        ProcessInstance process = mock(ProcessInstance.class); when(process.getProcessInstanceId()).thenReturn("proc-20");
        when(runtimeService.startProcessInstanceByKey(eq("recruitmentJobOpeningProcess"), eq("JOB_OPENING_20"), anyMap())).thenReturn(process);
        when(openingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        JobOpening opening = service.createOpeningFromApprovedRequisition(request);

        assertEquals("Software Engineer", opening.getJobTitle());
        assertEquals(7L, opening.getCompanyId());
        assertEquals("software-engineer-10", opening.getPublicSlug());
        assertEquals("proc-20", opening.getWorkflowInstanceId());
        verify(runtimeService).startProcessInstanceByKey(eq("recruitmentJobOpeningProcess"), eq("JOB_OPENING_20"), anyMap());
    }

    @Test void existingOpeningMakesApprovedCallbackIdempotent() {
        JobOpening existing = new JobOpening(); existing.setId(20L);
        when(openingRepository.findByStaffRequisitionRequestId(10L)).thenReturn(Optional.of(existing));
        assertSame(existing, service.createOpeningFromApprovedRequisition(request()));
        verifyNoInteractions(runtimeService, staffRepository);
    }

    @Test void publicApplicationStartsCandidateWorkflow() {
        JobOpening opening = publishedOpening();
        when(openingRepository.findPubliclyAvailableBySlug(eq("software-engineer-10"), eq(JobOpeningStatus.PUBLISHED), any(LocalDate.class))).thenReturn(Optional.of(opening));
        when(candidateRepository.findFirstByNormalizedEmailOrderByCreatedAtDesc("ada@example.com")).thenReturn(Optional.empty());
        when(candidateRepository.save(any())).thenAnswer(i -> { var c=(com.justjava.humanresource.recruitment.entity.Candidate)i.getArgument(0); c.setId(30L); return c; });
        when(applicationRepository.existsByCandidateIdAndJobOpeningId(30L,20L)).thenReturn(false);
        when(applicationRepository.saveAndFlush(any())).thenAnswer(i -> { var a=(com.justjava.humanresource.recruitment.entity.JobApplication)i.getArgument(0); a.setId(40L); return a; });
        when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ProcessInstance process=mock(ProcessInstance.class); when(process.getProcessInstanceId()).thenReturn("proc-40");
        when(runtimeService.startProcessInstanceByKey(eq("candidateApplicationProcess"),eq("JOB_APPLICATION_40"),anyMap())).thenReturn(process);
        PublicApplicationCommand command=new PublicApplicationCommand(); command.setFirstName("Ada");command.setLastName("Lovelace");command.setEmail("ADA@example.com");command.setConsent(true);

        var result=service.apply("software-engineer-10",command);

        assertEquals(40L,result.applicationId()); assertNotNull(result.accessToken());
        verify(historyRepository).save(any());
        verify(runtimeService).startProcessInstanceByKey(eq("candidateApplicationProcess"),eq("JOB_APPLICATION_40"),anyMap());
    }

    @Test void unavailablePublicOpeningCannotReceiveApplications() {
        when(openingRepository.findPubliclyAvailableBySlug(eq("software-engineer-10"), eq(JobOpeningStatus.PUBLISHED), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        PublicApplicationCommand command=new PublicApplicationCommand(); command.setFirstName("Ada");command.setLastName("Lovelace");command.setEmail("ada@example.com");command.setConsent(true);

        assertThrows(IllegalArgumentException.class, () -> service.apply("software-engineer-10", command));

        verifyNoInteractions(candidateRepository, runtimeService);
    }

    private WorkflowRequest request(){WorkflowRequest r=new WorkflowRequest();r.setId(10L);r.setRequestType(RequestType.STAFF_REQUISITION);r.setStatus(RequestStatus.APPROVED);r.setRequesterEmployeeId(5L);return r;}
    private StaffRequisitionDetail detail(){StaffRequisitionDetail d=new StaffRequisitionDetail();d.setJobTitle("Software Engineer");d.setDepartmentId(2L);d.setNumberOfPositions(2);d.setEmploymentType(StaffEmploymentType.FULL_TIME);d.setRequisitionReason(RequisitionReason.NEW_POSITION);d.setReasonForHire("Growth");return d;}
    private JobOpening publishedOpening(){JobOpening j=new JobOpening();j.setId(20L);j.setCompanyId(1L);j.setPublicSlug("software-engineer-10");j.setStatus(JobOpeningStatus.PUBLISHED);return j;}
    private Department department(Long companyId){Company c=new Company();c.setId(companyId);Department d=new Department();d.setId(2L);d.setCompany(c);return d;}
}
