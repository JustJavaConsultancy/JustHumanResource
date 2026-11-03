package com.justjava.humanresource.request.monetary;

import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.grouped.GroupedApprovalConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service @RequiredArgsConstructor
public class FundingRequestService {
    private final CashAdvanceDetailRepository advances;
    private final FundRequestDetailRepository funds;
    private final GroupedApprovalConfigurationService employees;
    @Value("${app.requests.funding.enabled:false}") private boolean enabled;
    public boolean isEnabled() {return enabled;}
    public static boolean supports(RequestType type) {return type==RequestType.CASH_ADVANCE||type==RequestType.FUND_REQUEST;}
    public void requireEnabled(RequestType type) {
        if (supports(type)&&!enabled) throw new IllegalArgumentException("New funding requests/submissions are disabled.");
    }
    public void validate(CreateWorkflowRequestCommand command) {
        if(command.getItems()!=null&&!command.getItems().isEmpty()) throw new IllegalArgumentException("Funding requests use a single header amount and do not accept request items.");
        var p=payload(command);
        if (p==null) throw new IllegalArgumentException("Funding request details are required.");
        new RequestMonetaryContext(p.getAmount(),p.getCurrency());
        if(p.getPurpose()==null||p.getPurpose().isBlank()||p.getPurpose().length()>2000
                ||p.getCostCenter()==null||p.getCostCenter().isBlank()||p.getCostCenter().length()>100)
            throw new IllegalArgumentException("Purpose and cost centre are required and must fit their field limits.");
        if(p.getRequiredDate()==null||p.getRequiredDate().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Required date must be today or later.");
        if(command.getRequestType()==RequestType.CASH_ADVANCE) {
            var a=command.getCashAdvance();
            if(a.getExpenseStartDate()==null||a.getExpenseEndDate()==null||a.getSettlementDueDate()==null
                    ||a.getExpenseStartDate().isAfter(a.getExpenseEndDate())||p.getRequiredDate().isAfter(a.getExpenseStartDate())
                    ||a.getSettlementDueDate().isBefore(a.getExpenseEndDate()))
                throw new IllegalArgumentException("Advance dates must satisfy required date <= expense start <= expense end <= settlement due date.");
        } else {
            var f=command.getFundRequest();
            if(f.getBeneficiaryEmployeeId()==null) throw new IllegalArgumentException("An employee beneficiary is required.");
            employees.eligibleEmployee(f.getBeneficiaryEmployeeId());
        }
    }
    @Transactional public void save(WorkflowRequest request,CreateWorkflowRequestCommand command) {
        validate(command);
        if(request.getRequestType()==RequestType.CASH_ADVANCE) {
            var p=command.getCashAdvance();var d=advances.findByRequestId(request.getId()).orElseGet(CashAdvanceDetail::new);
            d.setRequestId(request.getId());d.setAmount(p.getAmount());d.setCurrency(MonetaryValues.currency(p.getCurrency()));
            d.setPurpose(p.getPurpose().trim());d.setCostCenter(p.getCostCenter().trim());d.setRequiredDate(p.getRequiredDate());
            d.setExpenseStartDate(p.getExpenseStartDate());d.setExpenseEndDate(p.getExpenseEndDate());d.setSettlementDueDate(p.getSettlementDueDate());advances.save(d);
        } else {
            var p=command.getFundRequest();var d=funds.findByRequestId(request.getId()).orElseGet(FundRequestDetail::new);
            d.setRequestId(request.getId());d.setAmount(p.getAmount());d.setCurrency(MonetaryValues.currency(p.getCurrency()));
            d.setPurpose(p.getPurpose().trim());d.setCostCenter(p.getCostCenter().trim());d.setRequiredDate(p.getRequiredDate());
            d.setBeneficiaryEmployeeId(p.getBeneficiaryEmployeeId());d.setBeneficiaryName(employees.eligibleEmployee(p.getBeneficiaryEmployeeId()).getFullName());funds.save(d);
        }
    }
    @Transactional(readOnly=true) public Object detail(WorkflowRequest r) {
        return r.getRequestType()==RequestType.CASH_ADVANCE?advances.findByRequestId(r.getId()).orElseThrow():funds.findByRequestId(r.getId()).orElseThrow();
    }
    @Transactional(readOnly=true) public CreateWorkflowRequestCommand edit(WorkflowRequest r) {
        var c=new CreateWorkflowRequestCommand();c.setRequestType(r.getRequestType());
        if(r.getRequestType()==RequestType.CASH_ADVANCE) {
            var d=advances.findByRequestId(r.getId()).orElseThrow();var p=new CreateWorkflowRequestCommand.CashAdvancePayload();
            p.setAmount(d.getAmount());p.setCurrency(d.getCurrency());p.setPurpose(d.getPurpose());p.setCostCenter(d.getCostCenter());p.setRequiredDate(d.getRequiredDate());
            p.setExpenseStartDate(d.getExpenseStartDate());p.setExpenseEndDate(d.getExpenseEndDate());p.setSettlementDueDate(d.getSettlementDueDate());c.setCashAdvance(p);
        } else {
            var d=funds.findByRequestId(r.getId()).orElseThrow();var p=new CreateWorkflowRequestCommand.FundRequestPayload();
            p.setAmount(d.getAmount());p.setCurrency(d.getCurrency());p.setPurpose(d.getPurpose());p.setCostCenter(d.getCostCenter());p.setRequiredDate(d.getRequiredDate());
            p.setBeneficiaryEmployeeId(d.getBeneficiaryEmployeeId());c.setFundRequest(p);
        }
        return c;
    }
    @Transactional(readOnly=true) public RequestMonetaryContext context(WorkflowRequest r) {
        var p=payload(edit(r));return new RequestMonetaryContext(p.getAmount(),p.getCurrency());
    }
    @Transactional(readOnly=true) public void beforeSubmit(WorkflowRequest r) {requireEnabled(r.getRequestType());validate(edit(r));}
    private CreateWorkflowRequestCommand.FundingPayload payload(CreateWorkflowRequestCommand c) {
        return c.getRequestType()==RequestType.CASH_ADVANCE?c.getCashAdvance():c.getFundRequest();
    }
}
