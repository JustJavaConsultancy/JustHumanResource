package com.justjava.humanresource.recruitment;

import com.justjava.humanresource.recruitment.entity.Interview;
import com.justjava.humanresource.recruitment.entity.InterviewPanelMember;
import com.justjava.humanresource.recruitment.entity.JobApplication;
import com.justjava.humanresource.recruitment.enums.ApplicationStatus;
import com.justjava.humanresource.recruitment.enums.InterviewRecommendation;
import com.justjava.humanresource.recruitment.enums.InterviewStatus;
import com.justjava.humanresource.recruitment.enums.RecruitmentStage;
import com.justjava.humanresource.recruitment.repository.InterviewPanelMemberRepository;
import com.justjava.humanresource.recruitment.repository.InterviewRepository;
import com.justjava.humanresource.recruitment.repository.InterviewScorecardRepository;
import com.justjava.humanresource.recruitment.repository.JobApplicationRepository;
import com.justjava.humanresource.recruitment.service.InterviewService;
import com.justjava.humanresource.recruitment.service.RecruitmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {
    @Mock InterviewRepository interviewRepository;
    @Mock InterviewPanelMemberRepository panelRepository;
    @Mock InterviewScorecardRepository scorecardRepository;
    @Mock JobApplicationRepository applicationRepository;
    @Mock RecruitmentService recruitmentService;
    InterviewService service;

    @BeforeEach
    void setUp() {
        service = new InterviewService(interviewRepository, panelRepository, scorecardRepository,
                applicationRepository, recruitmentService);
    }

    @Test
    void schedulingInterviewRecordsApplicationStageHistoryThroughRecruitmentService() {
        JobApplication application = new JobApplication();
        application.setId(40L);
        when(applicationRepository.findById(40L)).thenReturn(Optional.of(application));
        when(interviewRepository.save(any())).thenAnswer(i -> {
            Interview interview = i.getArgument(0);
            interview.setId(70L);
            return interview;
        });

        service.schedule(40L, "Panel", LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1), "Meet", 5L, List.of(5L, 6L));

        verify(recruitmentService).moveApplication(40L, RecruitmentStage.INTERVIEW, ApplicationStatus.IN_PROCESS,
                "Interview scheduled", null, 5L);
    }

    @Test
    void duplicatePanelMemberScorecardIsRejected() {
        Interview interview = new Interview();
        interview.setId(70L);
        interview.setStatus(InterviewStatus.SCHEDULED);
        InterviewPanelMember member = new InterviewPanelMember();
        member.setInterviewId(70L);
        member.setEmployeeId(5L);
        when(interviewRepository.findById(70L)).thenReturn(Optional.of(interview));
        when(panelRepository.findByInterviewId(70L)).thenReturn(List.of(member));
        when(scorecardRepository.existsByInterviewIdAndReviewerEmployeeId(70L, 5L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.submitScorecard(70L, 5L, 4, InterviewRecommendation.HIRE, "Good"));

        verify(scorecardRepository, never()).save(any());
    }
}
