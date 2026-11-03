package com.justjava.humanresource.request.monetary;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.request.dto.WorkflowRequestDetailDTO;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.grouped.GroupedApprovalConfigurationService;
import com.justjava.humanresource.request.repository.WorkflowRequestTypeRepository;
import com.justjava.humanresource.request.service.WorkflowRequestService;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonetaryRequestControllerTest {
 MonetaryPolicyService policies=mock(MonetaryPolicyService.class);
 MonetaryRoutingService routing=mock(MonetaryRoutingService.class);
 WorkflowRequestService requests=mock(WorkflowRequestService.class);
 WorkflowRequestTypeRepository types=mock(WorkflowRequestTypeRepository.class);
 AuthenticationManager auth=mock(AuthenticationManager.class);
 MonetaryRequestController controller=new MonetaryRequestController(policies,routing,mock(FundingRequestService.class),requests,types,mock(EmployeeRepository.class),mock(GroupedApprovalConfigurationService.class),auth);
 WorkflowRequest request;WorkflowRequestType type;
 @BeforeEach void setup(){request=new WorkflowRequest();request.setId(42L);request.setRequestType(RequestType.EXPENSE_REIMBURSEMENT);type=new WorkflowRequestType();type.setMonetaryRoutingEnabled(true);when(requests.details(42L)).thenReturn(WorkflowRequestDetailDTO.builder().request(request).build());when(types.findByCode("EXPENSE_REIMBURSEMENT")).thenReturn(Optional.of(type));when(requests.canSubmitNormally(request)).thenReturn(true);when(policies.isEnabled()).thenReturn(true);}
 MonetaryRequestController.RoutingCapabilities capabilities(Map<String,Object> response){return (MonetaryRequestController.RoutingCapabilities)response.get("routing");}
 @Test void initialDraftCanPreviewWithoutResolvingOrWritingAnything(){var response=controller.history(42L);assertThat(capabilities(response)).isEqualTo(new MonetaryRequestController.RoutingCapabilities(true,true,true));assertThat(response).containsKeys("request","typeDetails","snapshots","funding");verify(routing,never()).select(any(),any());verify(routing,never()).record(any(),any());verify(requests,never()).submit(any());}
 @Test void unavailableRoutingDoesNotHideApplicability(){when(policies.isEnabled()).thenReturn(false);assertThat(capabilities(controller.history(42L))).isEqualTo(new MonetaryRequestController.RoutingCapabilities(true,false,true));}
 @Test void fixedDraftDoesNotOfferMonetaryPreview(){type.setMonetaryRoutingEnabled(false);assertThat(capabilities(controller.history(42L))).isEqualTo(new MonetaryRequestController.RoutingCapabilities(false,true,false));}
 @Test void pinnedCorrectionStillAppliesAfterModeSwitch(){type.setMonetaryRoutingEnabled(false);when(routing.history(42L)).thenReturn(List.of(new MonetarySnapshot()));assertThat(capabilities(controller.history(42L)).canPreviewSavedRoute()).isTrue();}
 @Test void nonOwnerOrAlternateFlowCannotUseOwnerPreviewAction(){when(requests.canSubmitNormally(request)).thenReturn(false);assertThat(capabilities(controller.history(42L)).canPreviewSavedRoute()).isFalse();}
 @Test void invisibleRequestsAreRejectedBeforeMetadataLookup(){when(requests.details(42L)).thenThrow(new IllegalStateException("Not visible"));assertThatThrownBy(()->controller.history(42L)).hasMessageContaining("Not visible");verifyNoInteractions(routing);}
 @Test void financeHistoryUsesExistingFinanceVisibility(){when(auth.isFinancialOfficer()).thenReturn(true);when(requests.financeDetails(42L)).thenReturn(WorkflowRequestDetailDTO.builder().request(request).build());controller.history(42L);verify(requests).financeDetails(42L);verify(requests,never()).details(any());}
}
