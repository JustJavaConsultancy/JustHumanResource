package com.justjava.humanresource.request.monetary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.approval.service.ApprovalRouteResolverFactory;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.grouped.GroupedApprovalConfigurationService;
import com.justjava.humanresource.request.handler.*;
import com.justjava.humanresource.request.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real repositories, policy publication and funding persistence, isolated from application.yml. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonetaryPersistenceTest {
 AnnotationConfigApplicationContext context;
 MonetaryPolicyService policies;
 FundingRequestService funding;
 WorkflowRequestRepository requests;
 TransactionTemplate tx;
 @Configuration @EnableTransactionManagement @EnableJpaAuditing
 @EnableJpaRepositories(basePackages={"com.justjava.humanresource.request.monetary","com.justjava.humanresource.request.repository"},includeFilters=@ComponentScan.Filter(type=FilterType.REGEX,pattern=".*(Monetary.*Repository|CashAdvanceDetailRepository|FundRequestDetailRepository|WorkflowRequestRepository|WorkflowRequestTypeRepository)"))
 static class Config {
  @Bean DataSource dataSource() throws Exception {
   String url=System.getProperty("monetary.postgres.url");
   if(url==null)return new DriverManagerDataSource("jdbc:h2:mem:monetary-persistence;DB_CLOSE_DELAY=-1","sa","");
   MonetaryPostgresMigrationTest.requireDisposableDatabase(url);
   var bootstrap=new DriverManagerDataSource(url,"request_enhancement_test","");
   try(var c=bootstrap.getConnection();var s=c.createStatement()){s.execute("CREATE SCHEMA IF NOT EXISTS monetary_runtime_test");}
   return new DriverManagerDataSource(url+"?currentSchema=monetary_runtime_test","request_enhancement_test","");
  }
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
   var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
   f.setManagedTypes(PersistenceManagedTypes.of(WorkflowRequest.class.getName(),WorkflowRequestType.class.getName(),MonetaryPolicy.class.getName(),MonetaryBand.class.getName(),MonetaryBinding.class.getName(),MonetarySnapshot.class.getName(),MonetaryPolicyEvent.class.getName(),CashAdvanceDetail.class.getName(),FundRequestDetail.class.getName()));
   f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));return f;
  }
  @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f){return new JpaTransactionManager(f);}
  @Bean GroupedApprovalConfigurationService access(){var a=mock(GroupedApprovalConfigurationService.class);when(a.actorId()).thenReturn(1L);var e=new Employee();e.setId(9L);e.setFirstName("Test");e.setLastName("Beneficiary");when(a.eligibleEmployee(9L)).thenReturn(e);return a;}
  @Bean MonetaryPolicyService policies(MonetaryPolicyRepository p,MonetaryBandRepository b,MonetaryBindingRepository bindings,WorkflowRequestTypeRepository types,GroupedApprovalConfigurationService access,MonetaryPolicyEventRepository events){return new MonetaryPolicyService(p,b,bindings,types,access,events);}
  @Bean FundingRequestService funding(CashAdvanceDetailRepository a,FundRequestDetailRepository f,GroupedApprovalConfigurationService access){return new FundingRequestService(a,f,access);}
 }
 @BeforeAll void boot(){context=new AnnotationConfigApplicationContext();context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",Map.of("app.requests.monetary-routing.enabled","true","app.requests.funding.enabled","true")));context.register(Config.class);context.refresh();policies=context.getBean(MonetaryPolicyService.class);funding=context.getBean(FundingRequestService.class);requests=context.getBean(WorkflowRequestRepository.class);tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));}
 @AfterAll void close(){context.close();}
 MonetaryPolicyCommand command(){return new MonetaryPolicyCommand("Funding limits",RequestType.FUND_REQUEST,"NGN",List.of(new MonetaryPolicyCommand.Band(BigDecimal.ZERO,null,MonetaryPolicyCommand.Target.LINE_MANAGER,null)));}
 @Test void publicationIsImmutableCloneGetsNewRevisionAndActivationKeepsFixedConfiguration(){
  var types=context.getBean(WorkflowRequestTypeRepository.class);var t=new WorkflowRequestType();t.setCode("FUND_REQUEST");t.setName("Funds");t.setCustomApprovalPathId(42L);types.saveAndFlush(t);
  var draft=policies.create(command());var published=policies.publish(draft.id(),draft.version());
  assertThatThrownBy(()->policies.update(published.id(),published.version(),command())).hasMessageContaining("published");
  var clone=policies.cloneDraft(published.id());assertThat(clone.revision()).isEqualTo(2);assertThat(clone.state()).isEqualTo("DRAFT");
  policies.activate(published.id());assertThat(types.findByCode("FUND_REQUEST").orElseThrow().isMonetaryRoutingEnabled()).isTrue();
  policies.fixedRouting(RequestType.FUND_REQUEST);var unchanged=types.findByCode("FUND_REQUEST").orElseThrow();assertThat(unchanged.isMonetaryRoutingEnabled()).isFalse();assertThat(unchanged.getCustomApprovalPathId()).isEqualTo(42L);
  assertThat(policies.bindings()).hasSize(1);assertThat(policies.audit()).extracting(MonetaryPolicyEvent::getAction).contains("PUBLISHED","ACTIVATED","FIXED_ROUTING");
 }
 @Test void staleDraftVersionCannotOverwriteANewerEdit(){var d=policies.create(command());var updated=policies.update(d.id(),d.version(),command());assertThat(updated.version()).isGreaterThan(d.version());assertThatThrownBy(()->policies.update(d.id(),d.version(),command())).hasMessageContaining("changed");}
 @Test void fundingDetailsPersistExactlyAndRollbackWithTheirParent(){
  var c=new CreateWorkflowRequestCommand();c.setRequestType(RequestType.FUND_REQUEST);var p=new CreateWorkflowRequestCommand.FundRequestPayload();p.setAmount(new BigDecimal("99999999999999999.99"));p.setCurrency("ngn");p.setPurpose("Equipment funding");p.setCostCenter("OPS");p.setRequiredDate(LocalDate.now().plusDays(1));p.setBeneficiaryEmployeeId(9L);c.setFundRequest(p);
  Long id=tx.execute(status->{var r=new WorkflowRequest();r.setRequestNumber("FND-PERSIST");r.setTitle("Funding");r.setRequestType(RequestType.FUND_REQUEST);r.setRequesterEmployeeId(1L);requests.saveAndFlush(r);funding.save(r,c);return r.getId();});
  var r=requests.findById(id).orElseThrow();assertThat(funding.context(r).amount()).isEqualByComparingTo(p.getAmount());assertThat(funding.edit(r).getFundRequest().getBeneficiaryEmployeeId()).isEqualTo(9L);
  assertThat(((FundRequestDetail)funding.detail(r)).getBeneficiaryName()).contains("Beneficiary");
  tx.executeWithoutResult(status->{var rollback=new WorkflowRequest();rollback.setRequestNumber("FND-ROLLBACK");rollback.setTitle("Rollback");rollback.setRequestType(RequestType.FUND_REQUEST);rollback.setRequesterEmployeeId(1L);requests.saveAndFlush(rollback);funding.save(rollback,c);status.setRollbackOnly();});
  assertThat(requests.findAll()).noneMatch(row->row.getRequestNumber().equals("FND-ROLLBACK"));
 }

 @Test void controllerPreviewReadsPersistedPolicyWithoutCreatingWorkflowOrSnapshot(){
  var types=context.getBean(WorkflowRequestTypeRepository.class);
  var type=new WorkflowRequestType();type.setCode("EXPENSE_REIMBURSEMENT");type.setName("Expenses");type.setRequiresApproval(true);types.saveAndFlush(type);
  var draft=policies.create(new MonetaryPolicyCommand("Expense preview",RequestType.EXPENSE_REIMBURSEMENT,"NGN",List.of(
   new MonetaryPolicyCommand.Band(BigDecimal.ZERO,new BigDecimal("1000"),MonetaryPolicyCommand.Target.LINE_MANAGER,null),
   new MonetaryPolicyCommand.Band(new BigDecimal("1000"),null,MonetaryPolicyCommand.Target.LINE_MANAGER,null))));
  policies.publish(draft.id(),draft.version());policies.activate(draft.id());
  var request=new WorkflowRequest();request.setRequestNumber("EXP-PREVIEW");request.setTitle("Preview only");request.setRequestType(RequestType.EXPENSE_REIMBURSEMENT);request.setRequesterEmployeeId(1L);requests.saveAndFlush(request);
  var handlers=mock(WorkflowRequestHandlerRegistry.class);var handler=mock(WorkflowRequestTypeHandler.class);when(handlers.get(RequestType.EXPENSE_REIMBURSEMENT)).thenReturn(handler);
  when(handler.monetaryContext(any())).thenReturn(Optional.of(new RequestMonetaryContext(new BigDecimal("1000.00"),"NGN")));
  var routes=mock(ApprovalRouteResolverFactory.class);var resolver=mock(com.justjava.humanresource.approval.service.ApprovalRouteResolver.class);when(routes.getResolver(any())).thenReturn(resolver);
  when(resolver.resolveApprovers(any())).thenReturn(List.of(com.justjava.humanresource.approval.model.ApproverRef.builder().employeeId(9L).level(1).build()));
  var snapshots=context.getBean(MonetarySnapshotRepository.class);var access=context.getBean(GroupedApprovalConfigurationService.class);
  var routing=new MonetaryRoutingService(policies,context.getBean(MonetaryBindingRepository.class),context.getBean(MonetaryBandRepository.class),snapshots,handlers,routes,access,new ObjectMapper());
  var requestService=mock(com.justjava.humanresource.request.service.WorkflowRequestService.class);when(requestService.visible(request.getId())).thenAnswer(inv->requests.findById(request.getId()).orElseThrow());
  var controller=new MonetaryRequestController(policies,routing,funding,requestService,types,mock(com.justjava.humanresource.hr.repository.EmployeeRepository.class),access,mock(com.justjava.humanresource.core.config.AuthenticationManager.class));
  long before=snapshots.count(),workflowsBefore=requests.findAll().stream().filter(r->r.getWorkflowInstanceId()!=null).count();
  var preview=controller.preview(request.getId());assertThat(preview).containsEntry("amount","1000.00").containsEntry("lowerBound","1000.00").containsEntry("policyVersion",1);
  assertThat(snapshots.count()).isEqualTo(before);assertThat(requests.findAll().stream().filter(r->r.getWorkflowInstanceId()!=null).count()).isEqualTo(workflowsBefore);
  assertThat(requests.findById(request.getId()).orElseThrow().getWorkflowInstanceId()).isNull();verify(requestService,never()).submit(any());
 }
}
