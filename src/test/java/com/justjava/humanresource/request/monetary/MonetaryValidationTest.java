package com.justjava.humanresource.request.monetary;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.grouped.GroupedApprovalConfigurationService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class MonetaryValidationTest {
 MonetaryPolicyCommand.Band band(String from,String to){return new MonetaryPolicyCommand.Band(new BigDecimal(from),to==null?null:new BigDecimal(to),MonetaryPolicyCommand.Target.LINE_MANAGER,null);}
 @Test void coverageAllowsAdjacentUnsortedBandsAndNoUpperLimit(){assertThat(MonetaryPolicyService.validateCoverage("NGN",List.of(band("1000",null),band("0","1000")))).extracting(MonetaryPolicyCommand.Band::lowerBound).containsExactly(new BigDecimal("0"),new BigDecimal("1000"));}
 @Test void coverageRejectsGapsOverlapAndMissingExtremes(){
  for(var rows:List.of(List.of(band("0","10"),band("11",null)),List.of(band("0","10"),band("9",null)),List.of(band("1",null)),List.of(band("0","10"))))assertThatThrownBy(()->MonetaryPolicyService.validateCoverage("NGN",rows)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void validatesPrecisionWithoutRoundingAndEnforcesStorageLimit(){
  assertThat(new RequestMonetaryContext(new BigDecimal("99999999999999999.99")," ngn ").currency()).isEqualTo("NGN");
  for(String invalid:List.of("0","-1","0.001","100000000000000000"))assertThatThrownBy(()->new RequestMonetaryContext(new BigDecimal(invalid),"NGN")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new RequestMonetaryContext(new BigDecimal("1.01"),"JPY")).hasMessageContaining("precision");
  assertThatThrownBy(()->new RequestMonetaryContext(BigDecimal.ONE,"XXX")).hasMessageContaining("Unsupported currency");
 }
 @Test void rejectsInvalidAdvanceDatesAndRequiresEligibleFundBeneficiary(){
  var employees=mock(GroupedApprovalConfigurationService.class);var service=new FundingRequestService(mock(CashAdvanceDetailRepository.class),mock(FundRequestDetailRepository.class),employees);
  var c=new CreateWorkflowRequestCommand();c.setRequestType(RequestType.CASH_ADVANCE);var p=new CreateWorkflowRequestCommand.CashAdvancePayload();c.setCashAdvance(p);
  p.setAmount(BigDecimal.TEN);p.setCurrency("NGN");p.setPurpose("Site visit");p.setCostCenter("OPS");p.setRequiredDate(LocalDate.now());p.setExpenseStartDate(LocalDate.now().plusDays(1));p.setExpenseEndDate(LocalDate.now().plusDays(2));p.setSettlementDueDate(LocalDate.now().plusDays(3));service.validate(c);
  p.setSettlementDueDate(LocalDate.now());assertThatThrownBy(()->service.validate(c)).hasMessageContaining("dates");
  var f=new CreateWorkflowRequestCommand.FundRequestPayload();f.setAmount(BigDecimal.TEN);f.setCurrency("NGN");f.setPurpose("Office funding");f.setCostCenter("OPS");f.setRequiredDate(LocalDate.now());c.setRequestType(RequestType.FUND_REQUEST);c.setFundRequest(f);
  assertThatThrownBy(()->service.validate(c)).hasMessageContaining("beneficiary");f.setBeneficiaryEmployeeId(9L);service.validate(c);verify(employees).eligibleEmployee(9L);
 }
 @Test void decimalJsonIsLosslessForBrowserEditing() throws Exception {
  var p=new CashAdvanceDetail();p.setAmount(new BigDecimal("99999999999999999.99"));
  assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(p)).contains("\"amount\":\"99999999999999999.99\"");
  assertThat(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(p)).contains("\"amount\":\"99999999999999999.99\"");
 }
 @Test void monetaryConfigurationRequiresAdministratorAndExplicitEnablement(){
  var access=mock(GroupedApprovalConfigurationService.class);var policies=mock(MonetaryPolicyRepository.class);var bands=mock(MonetaryBandRepository.class);var bindings=mock(MonetaryBindingRepository.class);var types=mock(com.justjava.humanresource.request.repository.WorkflowRequestTypeRepository.class);var events=mock(MonetaryPolicyEventRepository.class);
  var service=new MonetaryPolicyService(policies,bands,bindings,types,access,events);var c=new MonetaryPolicyCommand("Test",RequestType.FUND_REQUEST,"NGN",List.of(band("0",null)));
  doThrow(new org.springframework.security.access.AccessDeniedException("Administrator required")).when(access).requireAdministrator();
  assertThatThrownBy(()->service.create(c)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(policies,bands,bindings,types,events);
  doNothing().when(access).requireAdministrator();assertThatThrownBy(()->service.create(c)).hasMessageContaining("disabled");verifyNoInteractions(policies,bands,bindings,types,events);
 }

}
