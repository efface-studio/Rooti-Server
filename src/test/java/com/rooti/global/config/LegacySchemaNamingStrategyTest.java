package com.rooti.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.boot.model.naming.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacySchemaNamingStrategyTest {

    private final LegacySchemaNamingStrategy strategy = new LegacySchemaNamingStrategy();

    @Test
    @DisplayName("Known V2 table names get rewritten to Django (v1) ones")
    void rewrites_known_tables() {
        assertThat(physical("users")).isEqualTo("auth_user");
        assertThat(physical("challenged_workers")).isEqualTo("works_challengedworker");
        assertThat(physical("job_standards")).isEqualTo("works_jobstandard");
        assertThat(physical("caregiver_boards")).isEqualTo("care_caregiverboard");
        assertThat(physical("company_kiosks")).isEqualTo("works_companytohivitspetkiosk");
    }

    @Test
    @DisplayName("Unmapped names pass through unchanged (so new V2-only tables still work)")
    void passthrough_unknown_tables() {
        assertThat(physical("brand_new_table")).isEqualTo("brand_new_table");
    }

    @Test
    @DisplayName("Null identifier is tolerated")
    void null_safe() {
        assertThat(strategy.toPhysicalTableName(null, null)).isNull();
    }

    private String physical(String v2Name) {
        Identifier id = strategy.toPhysicalTableName(Identifier.toIdentifier(v2Name), null);
        return id == null ? null : id.getText();
    }
}
