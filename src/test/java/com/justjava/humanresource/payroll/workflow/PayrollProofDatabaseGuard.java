package com.justjava.humanresource.payroll.workflow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;



public final class PayrollProofDatabaseGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Pattern JDBC_HOST =
            Pattern.compile("^jdbc:postgresql://(?:\\[([^\\]]+)\\]|([^/:?]+))(?::\\d+)?/([^?;]+)(?:[?;].*)?$");

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Refusing payroll integration test: resolved datasource URL is missing.");
        }

        Matcher matcher = JDBC_HOST.matcher(url);
        if (!matcher.matches()) {
            throw new IllegalStateException(
                    "Refusing payroll integration test: resolved datasource URL is not a supported PostgreSQL URL.");
        }

        String host = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        String databaseName = matcher.group(3);
        if (!"localhost".equalsIgnoreCase(host)
                && !"127.0.0.1".equals(host)
                && !"::1".equals(host)) {
            throw new IllegalStateException(
                    "Refusing payroll integration test: datasource host is not an allowed local address.");
        }
        if (!databaseName.endsWith("_test")
                && !("humanresource".equals(databaseName)
                && environment.getProperty("proof.allow.local.dev.db", Boolean.class, false))) {
            throw new IllegalStateException(
                    "Refusing payroll integration test: database must end in _test; humanresource requires the explicit Maven opt-in.");
        }

        System.out.println("Payroll integration test database: " + databaseName);
        System.out.println("Payroll integration test database guard: passed");
    }
}
