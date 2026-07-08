package com.justjava.humanresource.request.handler;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.repository.AssetRequestDetailRepository;
import com.justjava.humanresource.integration.fam.FamAssetDTO;
import com.justjava.humanresource.integration.fam.FamAssetLookupService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowRequestHandlerRegistryTest {
 @Test void resolvesEachRegisteredType(){var registry=new WorkflowRequestHandlerRegistry(List.of(new GeneralRequestHandler(),new AssetRequestHandler(mock(AssetRequestDetailRepository.class),mock(FamAssetLookupService.class))));assertInstanceOf(GeneralRequestHandler.class,registry.get(RequestType.GENERAL_REQUEST));assertInstanceOf(AssetRequestHandler.class,registry.get(RequestType.ASSET_REQUEST));}
 @Test void rejectsUnsupportedType(){var registry=new WorkflowRequestHandlerRegistry(List.of(new GeneralRequestHandler()));assertThrows(IllegalArgumentException.class,()->registry.get(RequestType.FILE_REQUEST));}
 @Test void assetRequestRequiresDetailsAndItems(){var fam=mock(FamAssetLookupService.class);when(fam.findRequestableAsset("AST-1")).thenReturn(new FamAssetDTO("AST-1","Laptop","LAP-1","Laptop","Unit",BigDecimal.TEN,"AVAILABLE"));var handler=new AssetRequestHandler(mock(AssetRequestDetailRepository.class),fam);var command=new CreateWorkflowRequestCommand();assertThrows(IllegalArgumentException.class,()->handler.validate(command));command.setAssetRequest(new CreateWorkflowRequestCommand.AssetRequestPayload());assertThrows(IllegalArgumentException.class,()->handler.validate(command));var item=new CreateWorkflowRequestCommand.Item();item.setFamAssetId("AST-1");item.setItemName("Laptop");item.setQuantity(BigDecimal.ONE);command.setItems(List.of(item));assertDoesNotThrow(()->handler.validate(command));}
 @Test void assetRequestRejectsQuantityAboveFamAvailability(){var fam=mock(FamAssetLookupService.class);when(fam.findRequestableAsset("AST-1")).thenReturn(new FamAssetDTO("AST-1","Laptop","LAP-1","Laptop","Unit",BigDecimal.ONE,"AVAILABLE"));var handler=new AssetRequestHandler(mock(AssetRequestDetailRepository.class),fam);var command=new CreateWorkflowRequestCommand();command.setAssetRequest(new CreateWorkflowRequestCommand.AssetRequestPayload());var item=new CreateWorkflowRequestCommand.Item();item.setFamAssetId("AST-1");item.setQuantity(BigDecimal.TEN);command.setItems(List.of(item));assertThrows(IllegalArgumentException.class,()->handler.validate(command));}
}
