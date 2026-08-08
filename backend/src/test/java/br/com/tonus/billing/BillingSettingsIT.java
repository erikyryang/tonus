package br.com.tonus.billing;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The studio's billing configuration: one row, one field (SPEC-012).
 *
 * Has no POST or DELETE: the row exists since the migration and is neither
 * created nor deleted. It's only read and edited.
 */
class BillingSettingsIT extends AbstractBillingIntegrationTest {

    @Test
    void readsAndUpdatesDiscount() throws Exception {
        putDiscount("15.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.multiPlanDiscountPercent").value(15.00));

        mockMvc.perform(get("/billing-settings").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.multiPlanDiscountPercent").value(15.00));
    }

    @Test
    void rejectsDiscountOutOfRange() throws Exception {
        putDiscount("-1.00").andExpect(status().isBadRequest());
        putDiscount("100.01").andExpect(status().isBadRequest());
    }

    /** Zero is valid and is how the studio starts out: the discount is a choice, not a default. */
    @Test
    void acceptsZero() throws Exception {
        putDiscount("0.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.multiPlanDiscountPercent").value(0.00));
    }

    /**
     * The single row is a constraint, not a convention. Without the CHECK,
     * a second row would make "which discount counts?" have "whichever
     * LIMIT 1 picked" as the answer.
     */
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

    @Test
    void teacherCannotReadOrChangeSettings() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Acesso A Configuracao"));

        mockMvc.perform(get("/billing-settings").cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    private ResultActions putDiscount(String percent) throws Exception {
        return mockMvc.perform(put("/billing-settings")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"multiPlanDiscountPercent":"%s"}
                        """.formatted(percent)));
    }
}
