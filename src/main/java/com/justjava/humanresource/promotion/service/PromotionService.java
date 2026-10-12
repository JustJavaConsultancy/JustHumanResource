package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.promotion.dto.*;
import com.justjava.humanresource.promotion.entity.PromotionStatus;

import java.util.List;

public interface PromotionService {

    PromotionDetailResponse createDraft(PromotionRequestCommand command);

    PromotionDetailResponse updateDraft(Long id, PromotionRequestCommand command);

    PromotionDetailResponse submit(Long id);

    PromotionDetailResponse approve(Long id, PromotionDecisionCommand command);

    PromotionDetailResponse reject(Long id, PromotionDecisionCommand command);

    PromotionDetailResponse returnForCorrection(Long id, PromotionDecisionCommand command);

    PromotionDetailResponse cancel(Long id, PromotionDecisionCommand command);

    PromotionDetailResponse implement(Long id, PromotionImplementationCommand command);

    PromotionSalaryImpactResponse salaryImpact(PromotionRequestCommand command);

    PromotionApprovalRoutePreviewResponse approvalRoutePreview(Long id);

    PromotionDetailResponse getDetail(Long id);

    List<PromotionSummaryResponse> list(PromotionStatus status);

    List<PromotionSummaryResponse> myPromotions(PromotionStatus status);

    PromotionDashboardResponse dashboard();

    PromotionDashboardResponse myDashboard();
}
