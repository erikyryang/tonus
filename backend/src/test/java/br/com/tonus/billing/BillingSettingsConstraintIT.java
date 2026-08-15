package br.com.tonus.billing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What survived of {@code BillingSettingsIT} after the configuration screen and
 * both {@code /billing-settings} verbs were removed.
 *
 * The endpoints are gone; the row is not. It still governs the invoice
 * calculation, and its single-row guarantee is a database constraint — a
 * business rule, and therefore something that stays tested even with no HTTP in
 * front of it. Without the CHECK, "which discount counts?" would have
 * "whichever LIMIT 1 picked" as the answer.
 */
class BillingSettingsConstraintIT extends AbstractBillingIntegrationTest {

    @Test
    void databaseRefusesASecondSettingsRow() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO billing_settings (id, multi_plan_discount_percent, created_at, updated_at)
                VALUES (2, 10, now(), now())
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM billing_settings", Integer.class))
                .isEqualTo(1);
    }
}
