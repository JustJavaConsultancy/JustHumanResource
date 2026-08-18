package com.justjava.humanresource.integration.fam;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FamAssetLookupService {

    private final FamAssetClient client;

    public List<FamAssetDTO> listRequestableAssets() {
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

    private List<FamAssetDTO> sampleAssets() {
        // Temporary testing fallback. Remove when the FAM asset catalog integration is live.
        return List.of(
                new FamAssetDTO(
                        "FAM-SAMPLE-LAPTOP-001",
                        "Dell Latitude 5420 Laptop",
                        "LAP-DELL-5420",
                        "Laptop",
                        "Unit",
                        BigDecimal.TEN,
                        "AVAILABLE"
                ),
                new FamAssetDTO(
                        "FAM-SAMPLE-MONITOR-001",
                        "HP 24-inch Monitor",
                        "MON-HP-24",
                        "Monitor",
                        "Unit",
                        BigDecimal.valueOf(15),
                        "AVAILABLE"
                )
        );
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
}
