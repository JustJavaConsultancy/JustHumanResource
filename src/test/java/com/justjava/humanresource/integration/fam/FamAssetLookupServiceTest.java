package com.justjava.humanresource.integration.fam;

import com.justjava.humanresource.integration.fam.service.AssetCatalogService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FamAssetLookupServiceTest {

    @Test
    void returnsTemporarySampleAssetsWhenFamCatalogIsUnavailable() {
        FamAssetClient client = mock(FamAssetClient.class);
        FamIntegrationProperties properties = mock(FamIntegrationProperties.class);
        AssetCatalogService assetCatalogService = mock(AssetCatalogService.class);

        when(client.listRequestableAssets()).thenThrow(new FamIntegrationException("FAM unavailable"));

        FamAssetLookupService service = new FamAssetLookupService(client, properties, assetCatalogService);

        var assets = service.listRequestableAssets();

        assertEquals(2, assets.size());
        assertEquals("FAM-SAMPLE-LAPTOP-001", assets.get(0).assetId());
        assertEquals("FAM-SAMPLE-MONITOR-001", assets.get(1).assetId());
    }

    @Test
    void findsTemporarySampleAssetByIdWhenFamCatalogIsUnavailable() {
        FamAssetClient client = mock(FamAssetClient.class);
        FamIntegrationProperties properties = mock(FamIntegrationProperties.class);
        AssetCatalogService assetCatalogService = mock(AssetCatalogService.class);

        when(client.listRequestableAssets()).thenThrow(new FamIntegrationException("FAM unavailable"));

        FamAssetLookupService service = new FamAssetLookupService(client, properties, assetCatalogService);

        FamAssetDTO asset = service.findRequestableAsset("FAM-SAMPLE-LAPTOP-001");

        assertNotNull(asset);
        assertEquals("Dell Latitude 5420 Laptop", asset.assetName());
    }
}