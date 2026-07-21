package com.justjava.humanresource.integration.fam.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.integration.fam.AssetCatalogStatus;
import com.justjava.humanresource.integration.fam.AssetCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
@Entity
@Table(name = "asset_catalog_items")
public class AssetCatalogItem extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String assetCode;

    @Column(nullable = false)
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetCategory category;

    private String unitOfMeasure;

    @Column(nullable = false)
    private BigDecimal availableQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetCatalogStatus status;
}