package com.justjava.humanresource.integration.fam.dto;

import com.justjava.humanresource.integration.fam.AssetCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;


@Data
public class CreateAssetCatalogItemCommand {

    @NotBlank
    private String assetName;

    @NotNull
    private AssetCategory category;

    private String unitOfMeasure;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal availableQuantity;
}