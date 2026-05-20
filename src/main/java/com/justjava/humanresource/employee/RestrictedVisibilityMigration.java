package com.justjava.humanresource.employee;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestrictedVisibilityMigration {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void addRestrictedVisibilityColumn() {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute(
                    "ALTER TABLE employees ADD COLUMN IF NOT EXISTS restricted_visibility boolean NOT NULL DEFAULT false"
            );
            log.info("restricted_visibility column ensured on employees table.");
        } catch (Exception e) {
            log.error("Failed to ensure restricted_visibility column: {}", e.getMessage(), e);
        }
    }
}