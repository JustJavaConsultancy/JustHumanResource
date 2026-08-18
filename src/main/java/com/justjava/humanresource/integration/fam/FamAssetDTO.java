package com.justjava.humanresource.integration.fam;

import java.math.BigDecimal;

public record FamAssetDTO(
        String assetId,
        String assetName,
        String assetCode,
        String category,
        String unitOfMeasure,
        BigDecimal availableQuantity,
        String status
) {
}
