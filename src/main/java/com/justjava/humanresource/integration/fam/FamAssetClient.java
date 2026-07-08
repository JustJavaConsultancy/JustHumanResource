package com.justjava.humanresource.integration.fam;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FamAssetClient {

    private static final ParameterizedTypeReference<List<FamAssetDTO>> ASSET_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final FamIntegrationProperties properties;

    public List<FamAssetDTO> listRequestableAssets() {
        if (!properties.isConfigured()) {
            throw new FamIntegrationException("Fixed Asset Management API is not configured.");
        }

        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(properties.getTimeout());
            requestFactory.setReadTimeout(properties.getTimeout());

            RestClient client = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .requestFactory(requestFactory)
                    .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                    .build();

            RestClient.RequestHeadersSpec<?> request = client.get().uri(properties.getAssetsPath());
            if (properties.getBearerToken() != null && !properties.getBearerToken().isBlank()) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getBearerToken());
            }

            List<FamAssetDTO> assets = request.retrieve().body(ASSET_LIST_TYPE);
            return assets == null ? List.of() : assets;
        } catch (RestClientException ex) {
            throw new FamIntegrationException("Fixed Asset Management asset list could not be loaded.", ex);
        }
    }
}
