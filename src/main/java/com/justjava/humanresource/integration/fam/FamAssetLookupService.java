package com.justjava.humanresource.integration.fam;

import com.justjava.humanresource.integration.fam.service.AssetCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FamAssetLookupService {

    private final FamAssetClient client;
    private final FamIntegrationProperties properties;
    private final AssetCatalogService assetCatalogService;

    public List<FamAssetDTO> listRequestableAssets() {
        if (properties.getMode() == FamAssetSourceMode.SELF) {
            return normalize(assetCatalogService.listAvailableAsFamAssets());
        }
        try {
            return normalize(client.listRequestableAssets());
        } catch (FamIntegrationException ex) {
            return sampleAssets();
        }
    }

    private List<FamAssetDTO> normalize(List<FamAssetDTO> assets) {
        return assets.stream()
                .filter(asset -> asset.assetId() != null && !asset.assetId().isBlank())
                .filter(asset -> asset.assetName() != null && !asset.assetName().isBlank())
                .sorted(Comparator.comparing(FamAssetDTO::assetName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public FamAssetDTO findRequestableAsset(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("Asset selection is required.");
        }

        return listRequestableAssets().stream()
                .filter(asset -> asset.assetId().equals(assetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected asset was not found in Fixed Asset Management."));
    }

    private List<FamAssetDTO> sampleAssets() {
        return List.of(
                new FamAssetDTO("FAM-SAMPLE-LAPTOP-001", "Dell Latitude 5420 Laptop", "LAPTOP-001", "Laptop", "Unit", BigDecimal.ONE, "AVAILABLE"),
                new FamAssetDTO("FAM-SAMPLE-MONITOR-001", "Dell 24-inch Monitor", "MONITOR-001", "Monitor", "Unit", BigDecimal.ONE, "AVAILABLE")
        );
    }
}
