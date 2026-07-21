package com.justjava.humanresource.integration.fam.service.impl;

import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.integration.fam.AssetCatalogStatus;
import com.justjava.humanresource.integration.fam.FamAssetDTO;
import com.justjava.humanresource.integration.fam.dto.CreateAssetCatalogItemCommand;
import com.justjava.humanresource.integration.fam.dto.UpdateAssetCatalogItemCommand;
import com.justjava.humanresource.integration.fam.entity.AssetCatalogItem;
import com.justjava.humanresource.integration.fam.repository.AssetCatalogItemRepository;
import com.justjava.humanresource.integration.fam.service.AssetCatalogService;
import com.justjava.humanresource.integration.fam.service.AssetCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetCatalogServiceImpl implements AssetCatalogService {

    private final AssetCatalogItemRepository repository;
    private final AssetCodeGenerator assetCodeGenerator;

    @Override
    public List<AssetCatalogItem> listAll() {
        return repository.findAll();
    }

    @Override
    public List<FamAssetDTO> listAvailableAsFamAssets() {
        return repository.findByStatus(AssetCatalogStatus.AVAILABLE).stream()
                .map(this::toFamAssetDTO)
                .toList();
    }

    @Override
    @Transactional
    public AssetCatalogItem create(CreateAssetCatalogItemCommand command) {
        AssetCatalogItem item = new AssetCatalogItem();
        item.setAssetName(command.getAssetName());
        item.setCategory(command.getCategory());
        item.setUnitOfMeasure(command.getUnitOfMeasure());
        item.setAvailableQuantity(command.getAvailableQuantity());
        item.setStatus(AssetCatalogStatus.AVAILABLE);
        item.setAssetCode(assetCodeGenerator.nextAssetCode());
        return repository.save(item);
    }

    @Override
    @Transactional
    public AssetCatalogItem update(Long id, UpdateAssetCatalogItemCommand command) {
        AssetCatalogItem item = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AssetCatalogItem", id));
        item.setAssetName(command.getAssetName());
        item.setCategory(command.getCategory());
        item.setUnitOfMeasure(command.getUnitOfMeasure());
        item.setAvailableQuantity(command.getAvailableQuantity());
        item.setStatus(command.getStatus());
        return repository.save(item);
    }

    @Override
    @Transactional
    public AssetCatalogItem retire(Long id) {
        AssetCatalogItem item = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AssetCatalogItem", id));
        item.setStatus(AssetCatalogStatus.RETIRED);
        return repository.save(item);
    }

    private FamAssetDTO toFamAssetDTO(AssetCatalogItem item) {
        return new FamAssetDTO(
                item.getAssetCode(),
                item.getAssetName(),
                item.getAssetCode(),
                item.getCategory().getLabel(),
                item.getUnitOfMeasure(),
                item.getAvailableQuantity(),
                item.getStatus().name()
        );
    }
}