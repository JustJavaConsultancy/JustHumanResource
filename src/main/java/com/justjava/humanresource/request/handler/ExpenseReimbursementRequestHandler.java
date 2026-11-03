package com.justjava.humanresource.request.handler;

import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.ExpenseReimbursementDetail;
import com.justjava.humanresource.request.entity.ExpenseReimbursementItem;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.repository.ExpenseReimbursementDetailRepository;
import com.justjava.humanresource.request.repository.ExpenseReimbursementItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExpenseReimbursementRequestHandler implements WorkflowRequestTypeHandler {

    private final ExpenseReimbursementDetailRepository detailRepository;
    private final ExpenseReimbursementItemRepository itemRepository;

    public RequestType supportedType() {
        return RequestType.EXPENSE_REIMBURSEMENT;
    }

    public void validate(CreateWorkflowRequestCommand command) {
        var payload = command.getExpenseReimbursement();
        if (payload == null) {
            throw new IllegalArgumentException("Expense reimbursement details are required.");
        }
        if (payload.getExpenseStartDate().isAfter(payload.getExpenseEndDate())) {
            throw new IllegalArgumentException("Expense start date cannot be after expense end date.");
        }
        for (var item : payload.getExpenseItems()) {
            if (item.getExpenseDate().isBefore(payload.getExpenseStartDate()) || item.getExpenseDate().isAfter(payload.getExpenseEndDate())) {
                throw new IllegalArgumentException("Expense line date must fall within the reimbursement period.");
            }
        }
    }

    public void saveDetails(WorkflowRequest request, CreateWorkflowRequestCommand command) {
        var payload = command.getExpenseReimbursement();
        BigDecimal total = calculateTotal(payload);
        String currency = payload.getCurrency().toUpperCase();

        ExpenseReimbursementDetail detail = new ExpenseReimbursementDetail();
        detail.setWorkflowRequestId(request.getId());
        applyDetailPayload(detail, request, payload, total, currency);
        detailRepository.save(detail);

        saveItems(request, payload, currency);
    }

    public void updateDetails(WorkflowRequest request, CreateWorkflowRequestCommand command) {
        // Same validation as create.
        validate(command);

        var payload = command.getExpenseReimbursement();
        BigDecimal total = calculateTotal(payload);
        String currency = payload.getCurrency().toUpperCase();

        ExpenseReimbursementDetail detail = detailRepository.findByWorkflowRequestId(request.getId())
                .orElseGet(ExpenseReimbursementDetail::new);
        if (detail.getWorkflowRequestId() == null) {
            detail.setWorkflowRequestId(request.getId());
        }
        applyDetailPayload(detail, request, payload, total, currency);
        detailRepository.save(detail);

        // Replace existing expense line rows with the new set from the edit payload.
        itemRepository.deleteByWorkflowRequestId(request.getId());
        saveItems(request, payload, currency);
    }

    private BigDecimal calculateTotal(CreateWorkflowRequestCommand.ExpenseReimbursementPayload payload) {
        return payload.getExpenseItems().stream()
                .map(CreateWorkflowRequestCommand.ExpenseItemPayload::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void applyDetailPayload(ExpenseReimbursementDetail detail, WorkflowRequest request,
                                    CreateWorkflowRequestCommand.ExpenseReimbursementPayload payload,
                                    BigDecimal total, String currency) {
        detail.setClaimantEmployeeId(request.getRequesterEmployeeId());
        detail.setDepartmentId(request.getDepartmentId());
        detail.setExpenseStartDate(payload.getExpenseStartDate());
        detail.setExpenseEndDate(payload.getExpenseEndDate());
        detail.setBusinessPurpose(payload.getBusinessPurpose());
        detail.setPaymentMethod(payload.getPaymentMethod());
        detail.setCurrency(currency);
        detail.setTotalClaimAmount(total);
    }

    private void saveItems(WorkflowRequest request, CreateWorkflowRequestCommand.ExpenseReimbursementPayload payload, String currency) {
        for (var payloadItem : payload.getExpenseItems()) {
            ExpenseReimbursementItem item = new ExpenseReimbursementItem();
            item.setWorkflowRequestId(request.getId());
            item.setExpenseDate(payloadItem.getExpenseDate());
            item.setExpenseCategory(payloadItem.getExpenseCategory());
            item.setDescription(payloadItem.getDescription());
            item.setVendorName(payloadItem.getVendorName());
            item.setAmount(payloadItem.getAmount());
            item.setCurrency(currency);
            item.setRemarks(payloadItem.getRemarks());
            itemRepository.save(item);
        }
    }

    public java.util.Optional<com.justjava.humanresource.request.monetary.RequestMonetaryContext> monetaryContext(WorkflowRequest request) {
        var detail=detailRepository.findByWorkflowRequestId(request.getId()).orElseThrow();
        var items=itemRepository.findByWorkflowRequestIdOrderByExpenseDateAscIdAsc(request.getId());
        BigDecimal total=BigDecimal.ZERO;
        for(var item:items) {
            if(!detail.getCurrency().equalsIgnoreCase(item.getCurrency())) throw new IllegalArgumentException("Expense currencies do not agree.");
            com.justjava.humanresource.request.monetary.MonetaryValues.amount(item.getAmount(),item.getCurrency(),false);
            total=total.add(item.getAmount());
        }
        if(total.compareTo(detail.getTotalClaimAmount())!=0) throw new IllegalArgumentException("Saved expense total does not match expense lines.");
        return java.util.Optional.of(new com.justjava.humanresource.request.monetary.RequestMonetaryContext(total,detail.getCurrency()));
    }

    public Map<String, Object> buildWorkflowVariables(WorkflowRequest request) {
        return detailRepository.findByWorkflowRequestId(request.getId())
                .map(detail -> Map.<String, Object>of(
                        "totalClaimAmount", detail.getTotalClaimAmount(),
                        "currency", detail.getCurrency()
                ))
                .orElseGet(Map::of);
    }
}