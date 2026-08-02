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
 * A configuração de cobrança do estúdio: uma linha, um campo (SPEC-012).
 *
 * Não tem POST nem DELETE: a linha existe desde a migration e não se cria nem
 * se apaga. Só se lê e se edita.
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

    /** Zero é válido e é como o estúdio nasce: desconto é escolha, não default. */
    @Test
    void acceptsZero() throws Exception {
        putDiscount("0.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.multiPlanDiscountPercent").value(0.00));
    }

    /**
     * A linha única é constraint, não convenção. Sem o CHECK, uma segunda linha
     * faria "qual desconto vale?" ter como resposta "a que o LIMIT 1 pegou".
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
