package com.justjava.humanresource.request.config;
import com.justjava.humanresource.request.entity.WorkflowRequestType;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.repository.WorkflowRequestTypeRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration public class RequestTypeConfiguration {
 @org.springframework.beans.factory.annotation.Value("${app.requests.funding.enabled:false}") private boolean fundingEnabled;
 @Bean ApplicationRunner seedRequestTypes(WorkflowRequestTypeRepository repo){return args->{seed(repo,RequestType.STAFF_REQUISITION,"Staff Requisition",false,false,"staffRequisitionRequestHandler");seed(repo,RequestType.FILE_REQUEST,"File Request",true,false,"fileRequestHandler");seed(repo,RequestType.ASSET_REQUEST,"Asset Request",false,true,"assetRequestHandler");seed(repo,RequestType.EXPENSE_REIMBURSEMENT,"Expense Reimbursement",true,false,"expenseReimbursementRequestHandler");seed(repo,RequestType.GENERAL_REQUEST,"General Request",false,false,"generalRequestHandler");seed(repo,RequestType.CASH_ADVANCE,"Cash Advance",true,false,"cashAdvanceRequestHandler");seed(repo,RequestType.FUND_REQUEST,"Fund Request",true,false,"fundRequestHandler");};}
 private void seed(WorkflowRequestTypeRepository repo,RequestType type,String name,boolean attachment,boolean items,String handler){if(repo.findByCode(type.name()).isEmpty()){WorkflowRequestType t=new WorkflowRequestType();t.setEnabled(!com.justjava.humanresource.request.monetary.FundingRequestService.supports(type)||fundingEnabled);t.setCode(type.name());t.setName(name);t.setRequiresAttachment(attachment);t.setSupportsItems(items);t.setHandlerBeanName(handler);repo.save(t);}}
}
