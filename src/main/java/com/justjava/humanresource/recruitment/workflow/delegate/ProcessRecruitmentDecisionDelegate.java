package com.justjava.humanresource.recruitment.workflow.delegate;

import com.justjava.humanresource.recruitment.enums.ApplicationStatus;
import com.justjava.humanresource.recruitment.enums.RecruitmentStage;
import com.justjava.humanresource.recruitment.repository.JobApplicationRepository;
import com.justjava.humanresource.recruitment.service.RecruitmentService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("processRecruitmentDecisionDelegate") @RequiredArgsConstructor
public class ProcessRecruitmentDecisionDelegate implements JavaDelegate {
    private final RecruitmentService recruitmentService;
    private final JobApplicationRepository applicationRepository;

    public void execute(DelegateExecution execution) {
        Long applicationId = ((Number) execution.getVariable("applicationId")).longValue();
        String decision = String.valueOf(execution.getVariable("recruitmentDecision")).toUpperCase();
        String comment = execution.getVariable("recruitmentComment") == null ? null
                : String.valueOf(execution.getVariable("recruitmentComment"));
        String taskId = execution.getVariable("flowableTaskId") == null ? null
                : String.valueOf(execution.getVariable("flowableTaskId"));
        var application = applicationRepository.findById(applicationId).orElseThrow();
        RecruitmentStage nextStage = application.getCurrentStage();
        ApplicationStatus nextStatus = ApplicationStatus.IN_PROCESS;
        switch (decision) {
            case "REJECT" -> nextStatus = ApplicationStatus.REJECTED;
            case "HOLD" -> nextStatus = ApplicationStatus.ON_HOLD;
            case "ADVANCE" -> nextStage = next(application.getCurrentStage());
            case "ACCEPT_OFFER" -> { nextStage = RecruitmentStage.PRE_EMPLOYMENT; nextStatus = ApplicationStatus.OFFER_ACCEPTED; }
            case "DECLINE_OFFER" -> nextStatus = ApplicationStatus.OFFER_DECLINED;
            default -> throw new IllegalArgumentException("Unsupported recruitment decision: " + decision);
        }
        recruitmentService.moveApplication(applicationId, nextStage, nextStatus, comment, taskId, null);
        execution.setVariable("decisionOutcome", decision);
    }

    private RecruitmentStage next(RecruitmentStage stage) {
        return switch (stage) {
            case SCREENING -> RecruitmentStage.ASSESSMENT;
            case ASSESSMENT -> RecruitmentStage.INTERVIEW;
            case INTERVIEW -> RecruitmentStage.CHECKS;
            case CHECKS -> RecruitmentStage.OFFER;
            case OFFER -> RecruitmentStage.PRE_EMPLOYMENT;
            case PRE_EMPLOYMENT -> RecruitmentStage.ONBOARDING;
            default -> throw new IllegalStateException("Application cannot advance from " + stage + ".");
        };
    }
}
