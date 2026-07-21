package com.justjava.humanresource.integration.fam.repository;

import com.justjava.humanresource.integration.fam.AssetCatalogStatus;
import com.justjava.humanresource.integration.fam.entity.AssetCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetCatalogItemRepository extends JpaRepository<AssetCatalogItem, Long> {
    List<AssetCatalogItem> findByStatus(AssetCatalogStatus status);
}