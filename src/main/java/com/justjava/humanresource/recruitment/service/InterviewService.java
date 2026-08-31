package com.justjava.humanresource.recruitment.service;

import com.justjava.humanresource.recruitment.entity.*;
import com.justjava.humanresource.recruitment.enums.*;
import com.justjava.humanresource.recruitment.repository.*;
import com.justjava.humanresource.utils.AfterCommitExecutor;
import com.justjava.humanresource.utils.RecruitmentEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class InterviewService {
    private final InterviewRepository interviewRepository;
    private final InterviewPanelMemberRepository panelRepository;
    private final InterviewScorecardRepository scorecardRepository;
    private final JobApplicationRepository applicationRepository;
    private final RecruitmentService recruitmentService;
    private final RecruitmentEmailService recruitmentEmailService;
    private final AfterCommitExecutor afterCommitExecutor;

    @Transactional
    public Interview schedule(Long applicationId, String title, LocalDateTime start, LocalDateTime end,
                              String location, Long coordinatorId, List<Long> panelEmployeeIds) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (start == null || end == null || !end.isAfter(start)) throw new IllegalArgumentException("Interview end must follow its start.");
        Interview interview = new Interview();
        interview.setApplicationId(applicationId);
        interview.setTitle(title == null || title.isBlank() ? "Candidate interview" : title.trim());
        interview.setScheduledStart(start); interview.setScheduledEnd(end);
        interview.setLocationOrMeetingLink(location); interview.setCoordinatorEmployeeId(coordinatorId);
        interview.setStatus(InterviewStatus.SCHEDULED);
        Interview saved = interviewRepository.save(interview);
        if (panelEmployeeIds != null) panelEmployeeIds.stream().distinct().forEach(employeeId -> {
            InterviewPanelMember member = new InterviewPanelMember();
            member.setInterviewId(saved.getId()); member.setEmployeeId(employeeId);
            member.setChairperson(employeeId.equals(coordinatorId)); panelRepository.save(member);
        });
        recruitmentService.moveApplication(applicationId, RecruitmentStage.INTERVIEW, ApplicationStatus.IN_PROCESS,
                "Interview scheduled", null, coordinatorId);
        Long savedInterviewId = saved.getId();
        System.out.println("[RecruitmentMail][QUEUE] interview scheduled email interviewId=" + savedInterviewId
                + " applicationId=" + applicationId
                + " coordinatorEmployeeId=" + coordinatorId
                + " panelEmployeeIds=" + panelEmployeeIds);
        afterCommitExecutor.runAfterCommit(() -> recruitmentEmailService.notifyInterviewScheduled(savedInterviewId));
        return saved;
    }

    @Transactional
    public InterviewScorecard submitScorecard(Long interviewId, Long reviewerId, int score,
                                               InterviewRecommendation recommendation, String comments) {
        if (score < 1 || score > 5) throw new IllegalArgumentException("Overall score must be between 1 and 5.");
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found."));
        boolean panelMember = panelRepository.findByInterviewId(interviewId).stream()
                .anyMatch(member -> member.getEmployeeId().equals(reviewerId));
        if (!panelMember) throw new IllegalStateException("Only an assigned panel member may submit a scorecard.");
        if (scorecardRepository.existsByInterviewIdAndReviewerEmployeeId(interviewId, reviewerId)) {
            throw new IllegalStateException("This panel member has already submitted a scorecard for this interview.");
        }
        InterviewScorecard card = new InterviewScorecard();
        card.setInterviewId(interviewId); card.setReviewerEmployeeId(reviewerId); card.setOverallScore(score);
        card.setRecommendation(recommendation); card.setComments(comments); card.setSubmittedAt(LocalDateTime.now());
        InterviewScorecard saved = scorecardRepository.save(card);
        if (scorecardRepository.findByInterviewId(interviewId).size() >= panelRepository.findByInterviewId(interviewId).size()) {
            interview.setStatus(InterviewStatus.COMPLETED); interviewRepository.save(interview);
            Long completedInterviewId = interview.getId();
            System.out.println("[RecruitmentMail][QUEUE] interview completed email interviewId=" + completedInterviewId
                    + " reviewerEmployeeId=" + reviewerId);
            afterCommitExecutor.runAfterCommit(() -> recruitmentEmailService.notifyInterviewCompleted(completedInterviewId));
        }
        return saved;
    }
}
