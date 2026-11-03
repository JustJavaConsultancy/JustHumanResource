package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.*;
import com.justjava.humanresource.request.handler.*;
import com.justjava.humanresource.request.repository.WorkflowRequestRepository;
import com.justjava.humanresource.request.service.WorkflowRequestActivityService;
import org.flowable.engine.*;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JPA + Spring-managed Flowable transactions. Uses a disposable H2 database, never application.yml. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupedApprovalRuntimeTest {
    AnnotationConfigApplicationContext context;
    GroupedApprovalService service;
    GroupedApprovalConfigurationService configuration;
    WorkflowRequestRepository requests;
    GroupedApprovalCycleRepository cycles;
    GroupedApprovalAssignmentRepository assignments;
    ProcessEngine engine;
    TransactionTemplate transaction;
    WorkflowRequestTypeHandler handler;
    long serial;

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackages={"com.justjava.humanresource.request.grouped", "com.justjava.humanresource.request.repository"},
            includeFilters=@ComponentScan.Filter(type=FilterType.REGEX, pattern=".*(GroupedApproval.*Repository|WorkflowRequestRepository)"))
    static class TestConfiguration {
        @Bean DataSource dataSource() {
            String url = System.getProperty("grouped.postgres.url");
            if (url != null) {
                if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/grouped_workflow_test"))
                    throw new IllegalArgumentException("PostgreSQL tests require the dedicated local grouped_workflow_test database.");
                return new DriverManagerDataSource(url,"grouped_test","");
            }
            return new DriverManagerDataSource("jdbc:h2:mem:grouped-approval-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(dataSource);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(
                    WorkflowRequest.class.getName(), GroupedApprovalVersion.class.getName(), GroupedApprovalDefinition.class.getName(),
                    GroupedApprovalCycle.class.getName(), GroupedApprovalLevel.class.getName(), GroupedApprovalAssignment.class.getName(),
                    GroupedApprovalEvent.class.getName(), GroupedApprovalNotice.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.show_sql", "false",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory, DataSource ds) {
            var manager = new JpaTransactionManager(factory); manager.setDataSource(ds); return manager;
        }
        @Bean ProcessEngine processEngine(DataSource ds, PlatformTransactionManager manager, org.springframework.context.ApplicationContext context) {
            var configuration = new SpringProcessEngineConfiguration(); configuration.setDataSource(ds); configuration.setTransactionManager(manager);
            configuration.setApplicationContext(context); configuration.setDatabaseSchemaUpdate("true");
            configuration.setAsyncExecutorActivate(false); configuration.setDisableIdmEngine(true); configuration.setDisableEventRegistry(true);
            return configuration.buildProcessEngine();
        }
        @Bean RuntimeService runtimeService(ProcessEngine engine) { return engine.getRuntimeService(); }
        @Bean TaskService taskService(ProcessEngine engine) { return engine.getTaskService(); }
        @Bean GroupedApprovalConfigurationService configuration() { return mock(GroupedApprovalConfigurationService.class); }
        @Bean WorkflowRequestHandlerRegistry handlers() { return mock(WorkflowRequestHandlerRegistry.class); }
        @Bean WorkflowRequestActivityService activities() { return mock(WorkflowRequestActivityService.class); }
        @Bean GroupedApprovalService groupedApprovalService(GroupedApprovalConfigurationService cfg, GroupedApprovalCycleRepository cycles,
                GroupedApprovalLevelRepository levels, GroupedApprovalAssignmentRepository assignments, GroupedApprovalEventRepository events,
                GroupedApprovalNoticeRepository notices, WorkflowRequestRepository requests, WorkflowRequestHandlerRegistry handlers,
                WorkflowRequestActivityService activities, RuntimeService runtime, TaskService tasks) {
            return new GroupedApprovalService(cfg, cycles, levels, assignments, events, notices, requests, handlers, activities, runtime, tasks);
        }
    }

    @BeforeAll void boot() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        service = context.getBean(GroupedApprovalService.class); configuration = context.getBean(GroupedApprovalConfigurationService.class);
        requests = context.getBean(WorkflowRequestRepository.class); assignments = context.getBean(GroupedApprovalAssignmentRepository.class);
        cycles = context.getBean(GroupedApprovalCycleRepository.class); engine = context.getBean(ProcessEngine.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        engine.getRepositoryService().createDeployment().addClasspathResource("processes/groupedRequestApprovalProcess.bpmn").deploy();
    }
    @AfterAll void close() { if (context != null) context.close(); }
    @BeforeEach void prepare() {
        reset(configuration); handler = mock(WorkflowRequestTypeHandler.class);
        when(context.getBean(WorkflowRequestHandlerRegistry.class).get(any())).thenReturn(handler);
        when(handler.buildWorkflowVariables(any())).thenReturn(Map.of());
        when(configuration.eligibleEmployee(anyLong())).thenAnswer(invocation -> {
            Employee employee = new Employee(); employee.setId(invocation.getArgument(0)); employee.setFirstName("Approver"); employee.setLastName(invocation.getArgument(0).toString()); return employee;
        });
        GroupedApprovalVersion version = new GroupedApprovalVersion(); version.setId(100L); version.setName("Test"); version.setRevision(1);
        when(configuration.published(anyLong())).thenReturn(version);
    }

    WorkflowRequest start(ApprovalPolicy policy, boolean secondLevel) {
        List<GroupedApprovalCommand.Level> route = new ArrayList<>();
        route.add(new GroupedApprovalCommand.Level("First", policy, List.of(11L, 12L, 13L)));
        if (secondLevel) route.add(new GroupedApprovalCommand.Level("Final", ApprovalPolicy.ALL, List.of(21L)));
        when(configuration.effectiveLevels(anyLong(), anyLong())).thenReturn(route);
        return transaction.execute(status -> {
            WorkflowRequest request = new WorkflowRequest(); request.setRequestNumber("TEST-" + (++serial));
            request.setRequestType(RequestType.GENERAL_REQUEST); request.setTitle("Grouped test"); request.setRequesterEmployeeId(1L);
            requests.saveAndFlush(request); return service.start(request, 100L);
        });
    }
    String task(WorkflowRequest request, long actor) {
        return engine.getTaskService().createTaskQuery().processInstanceId(request.getWorkflowInstanceId()).taskAssignee(Long.toString(actor)).singleResult().getId();
    }
    long count(WorkflowRequest request) { return engine.getTaskService().createTaskQuery().processInstanceId(request.getWorkflowInstanceId()).count(); }

    @Test void allWaitsForEveryoneThenActivatesNextLevel() {
        var request = start(ApprovalPolicy.ALL, true); assertThat(count(request)).isEqualTo(3);
        service.decide(task(request,11), "APPROVE", null, 11L); assertThat(count(request)).isEqualTo(2);
        assertThat(service.inboxLabel(task(request,12))).contains("ALL","1 of 3 approvals");
        service.decide(task(request,12), "APPROVE", null, 12L); assertThat(count(request)).isEqualTo(1);
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.IN_APPROVAL);
        service.decide(task(request,13), "APPROVE", null, 13L);
        assertThat(count(request)).isEqualTo(1); assertThat(task(request,21)).isNotBlank();
        service.decide(task(request,21), "APPROVE", null, 21L);
        assertThat(count(request)).isZero(); assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.APPROVED);
        verify(handler, times(1)).afterApproved(any());
    }

    @Test void anyOneCancelsOtherTasksAndRejectsStaleConflict() {
        var request = start(ApprovalPolicy.ANY_ONE, true); String stale = task(request,12);
        var result = service.decide(task(request,11), "APPROVE", "Reviewed", 11L);
        assertThat(result.message()).contains("next level"); assertThat(count(request)).isEqualTo(1);
        assertThatThrownBy(() -> service.decide(stale,"REJECT","No",12L)).isInstanceOf(ResponseStatusException.class);
        assertThat(assignments.findByTaskId(stale).orElseThrow().getState()).isEqualTo("SUPERSEDED");
    }

    @Test void rejectionAndReturnCancelAllRemainingWork() {
        for (String action : List.of("REJECT", "RETURN")) {
            var request = start(ApprovalPolicy.ALL, true);
            service.decide(task(request,11), action, "Needs correction", 11L);
            assertThat(count(request)).isZero();
            assertThat(assignments.findByRequestIdOrderById(request.getId())).allMatch(a -> !Set.of("PENDING","NOT_STARTED").contains(a.getState()));
            assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(action.equals("REJECT") ? RequestStatus.REJECTED : RequestStatus.RETURNED_FOR_CORRECTION);
        }
    }

    @Test void duplicateIdenticalVoteIsIdempotentAndActorCannotBeForged() {
        var request = start(ApprovalPolicy.ALL, false); String task = task(request,11);
        assertThatThrownBy(() -> service.decide(task,"APPROVE",null,12L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        service.decide(task,"APPROVE",null,11L); service.decide(task,"APPROVE",null,11L);
        assertThat(count(request)).isEqualTo(2);
        assertThat(service.history(request.getId(),1L,false).getFirst().levels().getFirst().approvedCount()).isEqualTo(1);
    }

    @Test void genericTaskCompletionCannotBypassTheDecisionService() {
        var request = start(ApprovalPolicy.ANY_ONE, false); String id = task(request,11);
        assertThatThrownBy(() -> engine.getTaskService().complete(id,Map.of("groupLevelResolved",true,"approvalDecision","APPROVE")))
                .isInstanceOf(RuntimeException.class);
        assertThat(count(request)).isEqualTo(3);
        assertThat(assignments.findByTaskId(id).orElseThrow().getState()).isEqualTo("PENDING");
    }

    @Test void directEngineStartCannotAttachToAnExistingApprovalCycle() {
        var request = start(ApprovalPolicy.ALL,false);
        Long cycleId = cycles.findFirstByRequestIdOrderByCycleNoDesc(request.getId()).orElseThrow().getId();
        assertThatThrownBy(() -> engine.getRuntimeService().startProcessInstanceByKey(GroupedApprovalService.PROCESS_KEY,
                Map.of("groupCycleId",cycleId,"workflowRequestId",request.getId())))
                .isInstanceOf(RuntimeException.class);
        assertThat(count(request)).isEqualTo(3);
        assertThat(assignments.findByRequestIdOrderById(request.getId())).allMatch(a -> "PENDING".equals(a.getState()));
    }

    @Test void transactionFailureRollsBackVoteTaskAndFinalization() {
        var request = start(ApprovalPolicy.ANY_ONE, false); String id = task(request,11);
        doThrow(new IllegalStateException("Simulated handler failure")).when(handler).afterApproved(any());
        assertThatThrownBy(() -> service.decide(id,"APPROVE",null,11L)).isInstanceOf(RuntimeException.class);
        assertThat(count(request)).isEqualTo(3);
        assertThat(assignments.findByTaskId(id).orElseThrow().getState()).isEqualTo("PENDING");
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.IN_APPROVAL);
    }

    @Test void returnedRequestStartsNewCyclePinnedToOriginalVersion() {
        var request = start(ApprovalPolicy.ALL, false);
        service.decide(task(request,11),"RETURN","Correct details",11L);
        var returned = requests.findById(request.getId()).orElseThrow();
        var resumed = service.start(returned,999L);
        assertThat(count(resumed)).isEqualTo(3);
        assertThat(cycles.findByRequestIdOrderByCycleNo(request.getId())).extracting(GroupedApprovalCycle::getPathVersionId).containsExactly(100L,100L);
        assertThat(service.history(request.getId(),1L,false)).hasSize(2);
    }

    @Test void disablingNewSubmissionsDoesNotBlockExistingDecisions() {
        var request = start(ApprovalPolicy.ANY_ONE, false);
        doThrow(new IllegalStateException("New submissions disabled")).when(configuration).requireEnabled();
        service.decide(task(request,11),"APPROVE",null,11L);
        assertThat(count(request)).isZero();
    }

    @Test void financeSupersessionPreservesVotesAndClosesEveryTask() {
        var request = start(ApprovalPolicy.ALL, true); service.decide(task(request,11),"APPROVE",null,11L);
        transaction.executeWithoutResult(status -> {
            var locked = requests.findByIdForUpdate(request.getId()).orElseThrow();
            service.supersede(request.getId(),99L,"Finance override: APPROVE");
            engine.getRuntimeService().deleteProcessInstance(locked.getWorkflowInstanceId(),"Finance override");
            locked.setStatus(RequestStatus.APPROVED); requests.save(locked);
        });
        assertThat(count(request)).isZero();
        assertThat(assignments.findByRequestIdOrderById(request.getId())).filteredOn(a -> a.getEmployeeId().equals(11L)).allMatch(a -> a.getState().equals("APPROVED"));
        assertThat(assignments.findByRequestIdOrderById(request.getId())).noneMatch(a -> Set.of("PENDING","NOT_STARTED").contains(a.getState()));
    }

    @Test void simultaneousAnyOneApproveAndRejectCommitOnlyOneOutcome() throws Exception {
        var request = start(ApprovalPolicy.ANY_ONE, false); String approve = task(request,11), reject = task(request,12);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> compete(barrier, approve,"APPROVE",11L));
            var b = executor.submit(() -> compete(barrier, reject,"REJECT",12L));
            assertThat(List.of(a.get(20,TimeUnit.SECONDS), b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(count(request)).isZero();
        assertThat(assignments.findByRequestIdOrderById(request.getId())).filteredOn(a -> Set.of("APPROVED","REJECTED").contains(a.getState())).hasSize(1);
    }

    @Test void financeOverrideRacingAVoteLeavesOneFinalOutcomeAndNoTasks() throws Exception {
        var request = start(ApprovalPolicy.ANY_ONE, false); String approve = task(request,11);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var vote = executor.submit(() -> compete(barrier,approve,"APPROVE",11L));
            var finance = executor.submit(() -> {
                try { barrier.await(10,TimeUnit.SECONDS); } catch(Exception e) { throw new RuntimeException(e); }
                return transaction.execute(status -> {
                    var locked = requests.findByIdForUpdate(request.getId()).orElseThrow();
                    if (locked.getStatus() != RequestStatus.IN_APPROVAL) return false;
                    service.supersede(locked.getId(),99L,"Finance override: REJECT");
                    engine.getRuntimeService().deleteProcessInstance(locked.getWorkflowInstanceId(),"Finance override");
                    locked.setStatus(RequestStatus.REJECTED); requests.saveAndFlush(locked); return true;
                });
            });
            assertThat(List.of(vote.get(20,TimeUnit.SECONDS),finance.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(count(request)).isZero();
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isIn(RequestStatus.APPROVED,RequestStatus.REJECTED);
        assertThat(assignments.findByRequestIdOrderById(request.getId())).noneMatch(a -> Set.of("PENDING","NOT_STARTED").contains(a.getState()));
    }
    boolean compete(CyclicBarrier barrier, String task, String action, Long actor) {
        try { barrier.await(10, TimeUnit.SECONDS); service.decide(task,action,"Concurrent",actor); return true; }
        catch (ResponseStatusException conflict) { return false; }
        catch (Exception error) { throw new RuntimeException(error); }
    }
}
