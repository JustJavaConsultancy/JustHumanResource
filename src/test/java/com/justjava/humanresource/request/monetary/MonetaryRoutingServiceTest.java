package com.justjava.humanresource.request.monetary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.approval.model.*;
import com.justjava.humanresource.approval.service.*;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.grouped.GroupedApprovalConfigurationService;
import com.justjava.humanresource.request.handler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonetaryRoutingServiceTest {
 @Mock MonetaryPolicyService policies;
 @Mock MonetaryBindingRepository bindings;
 @Mock MonetaryBandRepository bands;
 @Mock MonetarySnapshotRepository snapshots;
 @Mock WorkflowRequestHandlerRegistry handlers;
 @Mock ApprovalRouteResolverFactory routes;
 @Mock GroupedApprovalConfigurationService grouped;
 MonetaryRoutingService service;
 WorkflowRequest request;
 WorkflowRequestType type;
 MonetaryPolicy policy;
 @BeforeEach void setup(){
  lenient().when(grouped.eligibleEmployee(anyLong())).thenAnswer(inv->{var e=new com.justjava.humanresource.hr.entity.Employee();e.setId(inv.getArgument(0));e.setFirstName("Manager");e.setLastName("Example");return e;});
  service=new MonetaryRoutingService(policies,bindings,bands,snapshots,handlers,routes,grouped,new ObjectMapper());
  request=new WorkflowRequest();request.setId(7L);request.setRequesterEmployeeId(1L);request.setRequestType(RequestType.CASH_ADVANCE);
  type=new WorkflowRequestType();type.setRequiresApproval(true);type.setMonetaryRoutingEnabled(true);
  policy=new MonetaryPolicy();policy.setId(20L);policy.setName("Expense limits");policy.setRevision(1);policy.setRequestType(RequestType.CASH_ADVANCE);policy.setCurrency("NGN");
 }
 void persistedAmount(String value){
  var handler=mock(WorkflowRequestTypeHandler.class);when(handlers.get(RequestType.CASH_ADVANCE)).thenReturn(handler);
  when(handler.monetaryContext(request)).thenReturn(Optional.of(new RequestMonetaryContext(new BigDecimal(value),"NGN")));
 }
 MonetaryBand band(long id,String lower,String upper,String target){
  var b=new MonetaryBand();b.setId(id);b.setPolicyId(20L);b.setLowerBound(new BigDecimal(lower));b.setUpperBound(upper==null?null:new BigDecimal(upper));b.setTarget(target);if(target.equals("GROUPED"))b.setGroupedVersionId(30L);return b;
 }
 @Test void fixedRoutesRemainUntouchedWhenFeatureIsNotActivated(){
  type.setMonetaryRoutingEnabled(false);assertThat(service.select(request,type)).isEmpty();verifyNoInteractions(policies,handlers,routes,grouped);
 }
 @Test void exactThresholdSelectsUpperBandFromAuthoritativeAmount(){
  persistedAmount("1000.00");var binding=new MonetaryBinding();binding.setPolicyId(20L);
  when(bindings.findByRequestTypeAndCurrency(RequestType.CASH_ADVANCE,"NGN")).thenReturn(Optional.of(binding));when(policies.published(20L)).thenReturn(policy);
  when(bands.findByPolicyIdOrderByOrdinal(20L)).thenReturn(List.of(band(1,"0","1000","LINE_MANAGER"),band(2,"1000",null,"GROUPED")));
  var s=service.select(request,type).orElseThrow();assertThat(s.band().getId()).isEqualTo(2L);assertThat(s.submissionNo()).isEqualTo(1);verify(grouped).effectiveLevels(30L,1L);
  request.setWorkflowInstanceId("process-7");service.record(request,s);var captor=ArgumentCaptor.forClass(MonetarySnapshot.class);verify(snapshots).saveAndFlush(captor.capture());
  assertThat(captor.getValue().getAmount()).isEqualByComparingTo("1000");assertThat(captor.getValue().getWorkflowInstanceId()).isEqualTo("process-7");
 }
 @Test void correctionPinsPolicyButReselectsBandEvenAfterTypeReturnsToFixedMode(){
  persistedAmount("99.99");type.setMonetaryRoutingEnabled(false);var previous=new MonetarySnapshot();previous.setPolicyId(20L);previous.setCurrency("NGN");previous.setSubmissionNo(2);
  when(snapshots.findFirstByRequestIdOrderBySubmissionNoDesc(7L)).thenReturn(Optional.of(previous));when(policies.published(20L)).thenReturn(policy);
  when(bands.findByPolicyIdOrderByOrdinal(20L)).thenReturn(List.of(band(1,"0","1000","LINE_MANAGER"),band(2,"1000",null,"GROUPED")));
  var resolver=mock(ApprovalRouteResolver.class);when(routes.getResolver(any())).thenReturn(resolver);when(resolver.resolveApprovers(any())).thenReturn(List.of(ApproverRef.builder().employeeId(8L).level(1).build()));
  var s=service.select(request,type).orElseThrow();assertThat(s.band().getId()).isEqualTo(1L);assertThat(s.submissionNo()).isEqualTo(3);assertThat(s.resolvedRoute()).contains("8");verifyNoInteractions(bindings);
 }
 @Test void missingBindingFailsClosed(){persistedAmount("20");assertThatThrownBy(()->service.select(request,type)).hasMessageContaining("No monetary policy");}
 @Test void ambiguousBandsFailClosed(){
  persistedAmount("20");var binding=new MonetaryBinding();binding.setPolicyId(20L);when(bindings.findByRequestTypeAndCurrency(any(),any())).thenReturn(Optional.of(binding));when(policies.published(20L)).thenReturn(policy);
  when(bands.findByPolicyIdOrderByOrdinal(20L)).thenReturn(List.of(band(1,"0",null,"LINE_MANAGER"),band(2,"0",null,"GROUPED")));
  assertThatThrownBy(()->service.select(request,type)).hasMessageContaining("exactly one");
 }
 @Test void correctionCannotChangeCurrency(){
  var previous=new MonetarySnapshot();previous.setCurrency("NGN");when(snapshots.findFirstByRequestIdOrderBySubmissionNoDesc(7L)).thenReturn(Optional.of(previous));
  var command=new CreateWorkflowRequestCommand();var payload=new CreateWorkflowRequestCommand.CashAdvancePayload();payload.setCurrency("USD");command.setCashAdvance(payload);
  assertThatThrownBy(()->service.validateCurrencyEdit(request,command)).hasMessageContaining("Currency cannot change");
 }
}
