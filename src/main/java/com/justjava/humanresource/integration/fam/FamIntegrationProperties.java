package com.justjava.humanresource.integration.fam;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "fam.api")
public class FamIntegrationProperties {

    private String baseUrl;
    private String assetsPath = "/api/assets/requestable";
    private String bearerToken;
    private Duration timeout = Duration.ofSeconds(5);
    private FamAssetSourceMode mode;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAssetsPath() {
        return assetsPath;
    }

    public void setAssetsPath(String assetsPath) {
        this.assetsPath = assetsPath;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public FamAssetSourceMode getMode() {
        return mode;
    }

    public void setMode(FamAssetSourceMode mode) {
        this.mode = mode;
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    @PostConstruct
    public void validate() {
        if (mode == null) {
            throw new IllegalStateException("fam.api.mode must be set to INTEGRATION or SELF");
        }
    }
}