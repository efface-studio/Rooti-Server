package com.rooti.global.config;

import java.util.Map;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Physical naming strategy that rewrites V2 table names to the Django v1 ones
 * already living in the production RDS instance.
 *
 * <p>Activated only under the {@code legacy} Spring profile (see
 * {@code application-legacy.yml}). The map below is the authoritative migration
 * map -- when a new V2 entity is added, append its V1 counterpart here so it
 * keeps reading/writing the right table.
 *
 * <p>Column names that don't match Django's (rare in practice) can still be
 * overridden per-entity with {@code @Column(name = ...)} in a {@code legacy}-
 * conditional subclass, but the bulk of the work is the table mapping below.
 */
public class LegacySchemaNamingStrategy extends PhysicalNamingStrategyStandardImpl {

    /**
     * V2 table name (lowercase) -> v1 (Django) table name.
     * Django convention: `<app_label>_<modelname>` lowercased.
     */
    private static final Map<String, String> TABLE_MAP =
            Map.<String, String>ofEntries(
                    // ----- auth (Django built-in user) -----
                    Map.entry("users", "auth_user"),

                    // ----- works app -----
                    Map.entry("challenged_workers", "works_challengedworker"),
                    Map.entry("companies", "works_company"),
                    Map.entry("company_chargers", "works_companycharger"),
                    Map.entry("company_workers", "works_companyworker"),
                    Map.entry("job_standards", "works_jobstandard"),
                    Map.entry("job_processes", "works_jobprocess"),
                    Map.entry("job_workers", "works_jobworker"),
                    Map.entry("work_schedules", "works_workschedule"),
                    Map.entry("work_records", "works_workrecord"),
                    Map.entry("work_process_records", "works_workprocessrecord"),
                    Map.entry("option_variables", "works_optionvariable"),
                    Map.entry("company_kiosks", "works_companytohivitspetkiosk"),

                    // ----- care app -----
                    Map.entry("caregivers", "care_caregiver"),
                    Map.entry("caregiver_worker_relations", "care_caregivertochallengedworker"),
                    Map.entry("caregiver_document_types", "care_caregiverdocumenttype"),
                    Map.entry("caregiver_documents", "care_caregiverdocument"),
                    Map.entry("caregiver_document_logs", "care_caregiverdocumentlog"),
                    Map.entry("caregiver_boards", "care_caregiverboard"));

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        if (name == null) return null;
        String mapped = TABLE_MAP.get(name.getText().toLowerCase());
        return mapped != null ? Identifier.toIdentifier(mapped, name.isQuoted()) : name;
    }
}
