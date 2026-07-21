package com.justjava.humanresource.integration.fam.service;

import com.justjava.humanresource.integration.fam.FamAssetDTO;
import com.justjava.humanresource.integration.fam.dto.CreateAssetCatalogItemCommand;
import com.justjava.humanresource.integration.fam.dto.UpdateAssetCatalogItemCommand;
import com.justjava.humanresource.integration.fam.entity.AssetCatalogItem;

import java.util.List;

public interface AssetCatalogService {

    List<AssetCatalogItem> listAll();

    List<FamAssetDTO> listAvailableAsFamAssets();

    AssetCatalogItem create(CreateAssetCatalogItemCommand command);

    AssetCatalogItem update(Long id, UpdateAssetCatalogItemCommand command);

    AssetCatalogItem retire(Long id);
}