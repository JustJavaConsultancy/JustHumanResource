package com.justjava.humanresource.request.handler;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.repository.AssetRequestDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class AssetRequestHandler implements WorkflowRequestTypeHandler {
 private final AssetRequestDetailRepository repository;
 public RequestType supportedType(){return RequestType.ASSET_REQUEST;}
 public void validate(CreateWorkflowRequestCommand c){if(c.getAssetRequest()==null) throw new IllegalArgumentException("Asset request details are required."); if(c.getItems()==null||c.getItems().isEmpty()) throw new IllegalArgumentException("At least one item is required for an asset request.");}
 public void saveDetails(WorkflowRequest r,CreateWorkflowRequestCommand c){var p=c.getAssetRequest(); AssetRequestDetail d=new AssetRequestDetail(); d.setWorkflowRequestId(r.getId()); d.setCostCenter(p.getCostCenter()); d.setRequiredDate(p.getRequiredDate()); d.setBusinessJustification(p.getBusinessJustification()); repository.save(d);}
}
