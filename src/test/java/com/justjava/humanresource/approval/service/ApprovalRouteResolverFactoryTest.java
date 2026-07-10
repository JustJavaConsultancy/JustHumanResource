package com.justjava.humanresource.approval.service;

import com.justjava.humanresource.approval.enums.ApprovalModuleType;
import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.approval.model.ApprovalContext;
import com.justjava.humanresource.approval.service.impl.LineManagerApprovalRouteResolver;
import com.justjava.humanresource.orgStructure.repositories.EmployeeReportingLineRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ApprovalRouteResolverFactoryTest {
    @Test
    void lineManagerResolverDoesNotClaimCustomRequestRoutes() {
        var lineManager = new LineManagerApprovalRouteResolver(mock(EmployeeReportingLineRepository.class));
        var custom = new StubResolver(ApprovalRouteType.CUSTOM);
        var factory = new ApprovalRouteResolverFactory(List.of(lineManager, custom));

        var resolver = factory.getResolver(ApprovalContext.builder()
                .moduleType(ApprovalModuleType.REQUEST)
                .routeType(ApprovalRouteType.CUSTOM)
                .build());

        assertSame(custom, resolver);
    }

    @Test
    void nullRequestRouteDefaultsToLineManager() {
        var lineManager = new LineManagerApprovalRouteResolver(mock(EmployeeReportingLineRepository.class));

        assertTrue(lineManager.supports(ApprovalContext.builder()
                .moduleType(ApprovalModuleType.REQUEST)
                .build()));
    }

    private record StubResolver(ApprovalRouteType routeType) implements ApprovalRouteResolver {
        @Override
        public boolean supports(ApprovalContext context) {
            return context.getRouteType() == routeType;
        }

        @Override
        public java.util.List<com.justjava.humanresource.approval.model.ApproverRef> resolveApprovers(ApprovalContext context) {
            return java.util.List.of();
        }
    }
}
