package com.justjava.humanresource.integration.inbound.payroll.config;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;

import jakarta.annotation.PostConstruct;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;



@Configuration
@ConditionalOnProperty(name = "integration.payroll-accounting.enabled", havingValue = "true")
public class IntegrationPayrollConfiguration {
    private final IntegrationPayrollProperties properties;
    private final Environment environment;

    public IntegrationPayrollConfiguration(
            IntegrationPayrollProperties properties,
            Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validateConfiguration() {
        if (properties.getCompanyId() == null || properties.getCompanyId() <= 0) {
            throw new IllegalStateException("Payroll accounting integration requires a positive company-id.");
        }
        if (properties.getExchange() == null || properties.getExchange().isBlank()
                || properties.getRoutingKey() == null || properties.getRoutingKey().isBlank()
                || properties.getRelayInterval() <= 0 || properties.getBatchSize() <= 0
                || properties.getMaxAttempts() <= 0 || properties.getBackoffBaseMs() <= 0
                || properties.getBackoffCapMs() < properties.getBackoffBaseMs()
                || properties.getConfirmTimeoutMs() <= 0) {
            throw new IllegalStateException("Payroll accounting relay configuration is invalid.");
        }
        String host = environment.getProperty("spring.rabbitmq.host");
        String portValue = environment.getProperty("spring.rabbitmq.port");
        String username = environment.getProperty("spring.rabbitmq.username");
        String password = environment.getProperty("spring.rabbitmq.password");
        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Payroll accounting integration requires valid spring.rabbitmq connection settings.");
        }
        if (host == null || host.isBlank()
                || username == null || username.isBlank()
                || password == null || password.isBlank()
                || port < 1 || port > 65535) {
            throw new IllegalStateException("Payroll accounting integration requires valid spring.rabbitmq connection settings.");
        }
    }

    @Bean
    TopicExchange payrollAccountingExchange() {
        return new TopicExchange(properties.getExchange(), true, false);
    }
}
