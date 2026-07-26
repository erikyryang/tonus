package br.com.tonus.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feriado é global: uma linha vale para o estúdio inteiro. Data duplicada
 * tornaria ambíguo o "existe feriado em D?" que a expansão da agenda pergunta.
 */
class HolidayCrudIT extends AbstractSchedulingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAndListsHoliday() throws Exception {
        String date = "2026-09-07";
        createHoliday(date, "Independencia");

        MvcResult result = mockMvc.perform(get("/holidays").param("size", "100").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(content).anySatisfy(node -> assertThat(node.get("date").asText()).isEqualTo(date));
    }

    @Test
    void duplicatedHolidayDateIsRejected() throws Exception {
        String date = "2026-10-12";
        createHoliday(date, "Nossa Senhora Aparecida");

        postHoliday(date, "Duplicado")
                .andExpect(status().isConflict());
    }

    @Test
    void databaseRejectsDuplicatedHolidayDate() throws Exception {
        String date = "2026-11-02";
        createHoliday(date, "Finados");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO holiday (date, description, created_at, updated_at)
                VALUES (DATE '%s', 'Duplicado no banco', now(), now())
                """.formatted(date)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void deletesHoliday() throws Exception {
        long id = createHoliday("2026-11-15", "Proclamacao da Republica");

        mockMvc.perform(delete("/holidays/{id}", id).cookie(admin))
                .andExpect(status().isNoContent());
    }

    /**
     * O intervalo filtra pelas duas pontas, inclusive. Feriado é justamente o
     * caso em que a borda importa: quem pede "o mês de setembro" espera o dia 1
     * e o dia 30 dentro, não de fora.
     */
    @Test
    void listFiltersByDateRange() throws Exception {
        createHoliday("2027-03-10", "Dentro do intervalo");
        createHoliday("2027-04-20", "Depois do intervalo");

        MvcResult result = mockMvc.perform(get("/holidays")
                        .param("from", "2027-03-01")
                        .param("to", "2027-03-31")
                        .param("size", "100")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode dates = json(result).get("content");
        assertThat(dates).isNotEmpty();
        assertThat(dates.findValues("date")).allSatisfy(node ->
                assertThat(node.asText()).startsWith("2027-03"));
    }

    @Test
    void rangeBoundsAreInclusive() throws Exception {
        createHoliday("2027-06-01", "Primeiro dia");
        createHoliday("2027-06-30", "Ultimo dia");

        MvcResult result = mockMvc.perform(get("/holidays")
                        .param("from", "2027-06-01")
                        .param("to", "2027-06-30")
                        .param("size", "100")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json(result).get("content").findValues("date"))
                .extracting(JsonNode::asText)
                .contains("2027-06-01", "2027-06-30");
    }

    @Test
    void rejectsHolidayWithoutDate() throws Exception {
        mockMvc.perform(post("/holidays")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Sem data"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
