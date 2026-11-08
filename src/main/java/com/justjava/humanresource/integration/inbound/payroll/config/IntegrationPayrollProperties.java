package com.justjava.humanresource.integration.inbound.payroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;



@ConfigurationProperties(prefix = "integration.payroll-accounting")
@Component
public class IntegrationPayrollProperties {
    public static final String CURRENCY = "NGN";

    private boolean enabled;
    private Long companyId;
    private String allowedClientId = "jjeip-humanresource";
    private String exchange = "justjava.business.events";
    private String routingKey = "justhr.payroll.finalised";
    private long relayInterval = 5000;
    private int batchSize = 20;
    private int maxAttempts = 8;
    private long backoffBaseMs = 1000;
    private long backoffCapMs = 300000;
    private long confirmTimeoutMs = 10000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCurrency() { return CURRENCY; }
    public String getAllowedClientId() { return allowedClientId; }
    public void setAllowedClientId(String allowedClientId) { this.allowedClientId = allowedClientId; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getRoutingKey() { return routingKey; }
    public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
    public long getRelayInterval() { return relayInterval; }
    public void setRelayInterval(long relayInterval) { this.relayInterval = relayInterval; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public long getBackoffBaseMs() { return backoffBaseMs; }
    public void setBackoffBaseMs(long backoffBaseMs) { this.backoffBaseMs = backoffBaseMs; }
    public long getBackoffCapMs() { return backoffCapMs; }
    public void setBackoffCapMs(long backoffCapMs) { this.backoffCapMs = backoffCapMs; }
    public long getConfirmTimeoutMs() { return confirmTimeoutMs; }
    public void setConfirmTimeoutMs(long confirmTimeoutMs) { this.confirmTimeoutMs = confirmTimeoutMs; }
}
