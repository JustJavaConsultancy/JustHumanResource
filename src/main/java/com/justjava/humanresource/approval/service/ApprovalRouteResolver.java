package com.justjava.humanresource.approval.service;

import com.justjava.humanresource.approval.model.ApprovalContext;
import com.justjava.humanresource.approval.model.ApproverRef;

import java.util.List;

public interface ApprovalRouteResolver {
    boolean supports(ApprovalContext context);
    List<ApproverRef> resolveApprovers(ApprovalContext context);
}
