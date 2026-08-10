package com.staging.sg.onboarding.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Increment1MigrationContractTest {
    @Test
    void portalMigrationIsAdditivePersistentAndIdempotent() throws Exception {
        String sql = Files.readString(Path.of("..", "sql", "merchant-onboarding",
                "V3__merchant_legal_profile_and_multi_outlet.sql"));
        assertTrue(sql.contains("legacy_outlet_migration"));
        assertTrue(sql.contains("migration_run"));
        assertTrue(sql.contains("ON CONFLICT(case_id) DO NOTHING"));
        assertTrue(sql.contains("WHERE active AND principal"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS"));
        assertTrue(sql.contains("responsible_birth_date"));
        assertTrue(sql.contains("responsible_id_number"));
        assertTrue(sql.contains("onboarding_field_rule"));
        assertTrue(!sql.toUpperCase().contains("DROP TABLE"));
        assertTrue(!sql.toUpperCase().contains("DROP COLUMN"));
    }

    @Test
    void acquiringMigrationKeepsLegacySchemaAndSelectsOnePrincipal() throws Exception {
        String sql = Files.readString(Path.of("..", "sql", "acquiring",
                "V4__merchant_legal_profile_and_structured_outlet.sql"));
        assertTrue(sql.contains("merchant_legal_profile"));
        assertTrue(sql.contains("merchant_representative"));
        assertTrue(sql.contains("merchant_beneficial_owner"));
        assertTrue(sql.contains("row_number() OVER (PARTITION BY merchant_id"));
        assertTrue(sql.contains("WHERE active AND principal"));
        assertTrue(sql.contains("responsible_birth_date"));
        assertTrue(sql.contains("responsible_id_number"));
        assertTrue(!sql.toUpperCase().contains("DROP TABLE"));
        assertTrue(!sql.toUpperCase().contains("DROP COLUMN"));
    }
}
