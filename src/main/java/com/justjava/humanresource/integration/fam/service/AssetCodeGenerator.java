package com.justjava.humanresource.integration.fam.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class AssetCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initSequence() {
        jdbcTemplate.execute(
                "CREATE SEQUENCE IF NOT EXISTS asset_code_seq START WITH 1 INCREMENT BY 1 NO CYCLE");
    }

    public String nextAssetCode() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('asset_code_seq')", Long.class);
        return "AST-" + String.format("%06d", next);
    }
}