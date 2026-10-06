package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.dto.LoanProductCommand;
import com.justjava.humanresource.loan.dto.LoanProductResponse;
import com.justjava.humanresource.loan.dto.LoanProductSummaryResponse;
import com.justjava.humanresource.loan.entity.LoanProduct;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;

import java.util.List;

public interface LoanProductService {

    /** HR/admin only. */
    LoanProductResponse create(LoanProductCommand command);

    /** HR/admin only. Financial terms and approval route are locked once the product is used. */
    LoanProductResponse update(Long id, LoanProductCommand command);

    /** HR/admin only. */
    LoanProductResponse deactivate(Long id);

    /** HR/admin only. */
    LoanProductResponse reactivate(Long id);

    /** HR/admin only. Only unused products can be deleted. */
    void delete(Long id);

    /** HR, admin and finance. */
    LoanProductResponse getById(Long id);

    /** HR, admin and finance. Includes inactive products. */
    List<LoanProductResponse> listAll();

    /** Any authenticated user. Active products only, for new applications. */
    List<LoanProductSummaryResponse> listActive();

    /** Validates route type and custom path (exists, enabled, has at least one step). */
    void validateApprovalRoute(LoanApprovalRouteType routeType, Long customApprovalPathId);

    /** Internal: flags a product as used (locks its terms). Called when an application is first created. */
    void markUsed(Long productId);

    /** Internal: loads the entity with no access check. */
    LoanProduct getEntity(Long id);
}
