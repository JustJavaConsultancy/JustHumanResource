package com.justjava.humanresource.promotion;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionWorkflowRuntimeTest {

    private ProcessEngine engine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private final List<String> delegateCalls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration configuration =
                (StandaloneInMemProcessEngineConfiguration) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:promotion-workflow-" + System.nanoTime());
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setBeans(delegates());

        engine = configuration.buildProcessEngine();
        runtimeService = engine.getRuntimeService();
        taskService = engine.getTaskService();

        RepositoryService repositoryService = engine.getRepositoryService();
        repositoryService.createDeployment()
                .addClasspathResource("processes/promotion-approval-process.bpmn20.xml")
                .deploy();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void completesHappyPathThroughHrOnlyWhenFinanceAndExecutiveAreSkipped() {
        start(false, false, false);

        assertCurrentTask("hrApproval");
        approveCurrentTask("HR");

        assertThat(activeTask()).isNull();
        assertThat(delegateCalls).containsExactly(
                "initialize",
                "evaluateEligibility",
                "record:HR:APPROVE",
                "approved"
        );
    }

    @Test
    void routesThroughLineManagerFinanceAndExecutiveWhenRequired() {
        start(true, true, true);

        assertCurrentTask("lineManagerApproval");
        approveCurrentTask("LINE_MANAGER");

        assertCurrentTask("hrApproval");
        approveCurrentTask("HR");

        assertCurrentTask("financeApproval");
        approveCurrentTask("FINANCE");

        assertCurrentTask("executiveApproval");
        approveCurrentTask("EXECUTIVE");

        assertThat(activeTask()).isNull();
        assertThat(delegateCalls).containsExactly(
                "initialize",
                "evaluateEligibility",
                "record:LINE_MANAGER:APPROVE",
                "record:HR:APPROVE",
                "record:FINANCE:APPROVE",
                "record:EXECUTIVE:APPROVE",
                "approved"
        );
    }

    @Test
    void rejectionTerminatesTheProcessAfterRecordingDecision() {
        start(false, true, true);

        assertCurrentTask("hrApproval");
        completeCurrentTask("HR", "REJECT");

        assertThat(activeTask()).isNull();
        assertThat(delegateCalls).containsExactly(
                "initialize",
                "evaluateEligibility",
                "record:HR:REJECT",
                "rejected"
        );
    }

    @Test
    void returnForCorrectionTerminatesTheProcessAfterRecordingDecision() {
        start(false, true, true);

        assertCurrentTask("hrApproval");
        completeCurrentTask("HR", "RETURN");

        assertThat(activeTask()).isNull();
        assertThat(delegateCalls).containsExactly(
                "initialize",
                "evaluateEligibility",
                "record:HR:RETURN",
                "returned"
        );
    }

    @Test
    void skipsFinanceAndRoutesFromHrToExecutiveWhenFinanceIsNotRequired() {
        start(false, false, true);

        assertCurrentTask("hrApproval");
        approveCurrentTask("HR");

        assertCurrentTask("executiveApproval");
        approveCurrentTask("EXECUTIVE");

        assertThat(activeTask()).isNull();
        assertThat(delegateCalls).containsExactly(
                "initialize",
                "evaluateEligibility",
                "record:HR:APPROVE",
                "record:EXECUTIVE:APPROVE",
                "approved"
        );
    }

    @Test
    void cancellationDeletesActiveRuntimeProcessWithoutCallingTerminalDelegates() {
        String processInstanceId = start(true, true, true);

        assertCurrentTask("lineManagerApproval");
        runtimeService.deleteProcessInstance(processInstanceId, "Promotion request cancelled.");

        assertThat(activeTask()).isNull();
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult()).isNull();
        assertThat(delegateCalls).containsExactly(
                "initialize",
                "evaluateEligibility"
        );
    }

    private Map<Object, Object> delegates() {
        Map<Object, Object> beans = new HashMap<>();
        beans.put("initializePromotionApprovalDelegate", (JavaDelegate) execution -> delegateCalls.add("initialize"));
        beans.put("evaluatePromotionEligibilityDelegate", (JavaDelegate) execution -> delegateCalls.add("evaluateEligibility"));
        beans.put("processPromotionApprovalDecisionDelegate", (JavaDelegate) execution -> delegateCalls.add(String.format(
                "record:%s:%s",
                execution.getVariable("approvalStage"),
                execution.getVariable("approvalDecision")
        )));
        beans.put("finalizePromotionApprovalDelegate", (JavaDelegate) execution -> delegateCalls.add("approved"));
        beans.put("finalizePromotionRejectionDelegate", (JavaDelegate) execution -> delegateCalls.add("rejected"));
        beans.put("returnPromotionForCorrectionDelegate", (JavaDelegate) execution -> delegateCalls.add("returned"));
        return beans;
    }

    private String start(boolean manager, boolean finance, boolean executive) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("promotionRequestId", 1L);
        variables.put("requiresLineManagerApproval", manager);
        variables.put("lineManagerEmployeeId", "7");
        variables.put("requiresFinanceApproval", finance);
        variables.put("requiresExecutiveApproval", executive);
        return runtimeService.startProcessInstanceByKey("promotionApprovalProcess", "PROMOTION_1", variables)
                .getProcessInstanceId();
    }

    private void approveCurrentTask(String stage) {
        completeCurrentTask(stage, "APPROVE");
    }

    private void completeCurrentTask(String stage, String decision) {
        Task task = activeTask();
        assertThat(task).isNotNull();
        Map<String, Object> variables = new HashMap<>();
        variables.put("approvalStage", stage);
        variables.put("approvalDecision", decision);
        variables.put("approvalComment", "Looks valid");
        variables.put("approvalActorId", 99L);
        variables.put("flowableTaskId", task.getId());
        taskService.complete(task.getId(), variables);
    }

    private void assertCurrentTask(String taskDefinitionKey) {
        Task task = activeTask();
        assertThat(task).isNotNull();
        assertThat(task.getTaskDefinitionKey()).isEqualTo(taskDefinitionKey);
    }

    private Task activeTask() {
        return taskService.createTaskQuery()
                .processDefinitionKey("promotionApprovalProcess")
                .active()
                .singleResult();
    }
}
