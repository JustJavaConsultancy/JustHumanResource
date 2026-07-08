package com.justjava.humanresource.request.handler;

import com.justjava.humanresource.integration.fam.FamAssetLookupService;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.AssetRequestDetail;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.repository.AssetRequestDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssetRequestHandler implements WorkflowRequestTypeHandler {

    private final AssetRequestDetailRepository repository;
    private final FamAssetLookupService famAssetLookupService;

    public RequestType supportedType() {
        return RequestType.ASSET_REQUEST;
    }

    public void validate(CreateWorkflowRequestCommand command) {
        if (command.getAssetRequest() == null) {
            throw new IllegalArgumentException("Asset request details are required.");
        }
        if (command.getItems() == null || command.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one asset is required for an asset request.");
        }
        for (var item : command.getItems()) {
            if (item.getFamAssetId() == null || item.getFamAssetId().isBlank()) {
                throw new IllegalArgumentException("Each asset request item must select an asset from Fixed Asset Management.");
            }
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new IllegalArgumentException("Each asset request item must have a valid quantity.");
            }
            var asset = famAssetLookupService.findRequestableAsset(item.getFamAssetId());
            if (asset.availableQuantity() != null && item.getQuantity().compareTo(asset.availableQuantity()) > 0) {
                throw new IllegalArgumentException("Requested quantity for " + asset.assetName() + " exceeds the available quantity in Fixed Asset Management.");
            }
        }
    }

    public void saveDetails(WorkflowRequest request, CreateWorkflowRequestCommand command) {
        var payload = command.getAssetRequest();
        AssetRequestDetail detail = new AssetRequestDetail();
        detail.setWorkflowRequestId(request.getId());
        detail.setCostCenter(payload.getCostCenter());
        detail.setRequiredDate(payload.getRequiredDate());
        detail.setBusinessJustification(payload.getBusinessJustification());
        repository.save(detail);
    }
}
