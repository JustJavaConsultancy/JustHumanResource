package com.justjava.humanresource.approval.service;

import com.justjava.humanresource.approval.model.ApprovalContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalRouteResolverFactory {

    private final List<ApprovalRouteResolver> resolvers;

    public ApprovalRouteResolver getResolver(ApprovalContext context) {
        return resolvers.stream()
                .filter(r -> r.supports(context))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No approval route resolver found for module " + context.getModuleType()));
    }
}
