package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
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
 * Announcement is global: one row applies to the whole studio. A duplicated date
 * would make ambiguous the "are D's classes suspended?" that the schedule expansion
 * asks — and would swallow the second notice, because WhatsApp's idempotency
 * key is the date.
 */
class AnnouncementCrudIT extends AbstractSchedulingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAndListsAnnouncement() throws Exception {
        String date = "2026-09-07";
        createAnnouncement(date, "Independencia", true);

        MvcResult result = mockMvc.perform(get("/announcements").param("size", "100").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(content).anySatisfy(node -> assertThat(node.get("date").asText()).isEqualTo(date));
    }

    @Test
    void duplicatedAnnouncementDateIsRejected() throws Exception {
        String date = "2026-10-12";
        createAnnouncement(date, "Nossa Senhora Aparecida", true);

        postAnnouncement(date, "Duplicado", false)
                .andExpect(status().isConflict());
    }

    @Test
    void databaseRejectsDuplicatedAnnouncementDate() throws Exception {
        String date = "2026-11-02";
        createAnnouncement(date, "Finados", true);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO announcement (date, description, suspends_classes, created_at, updated_at)
                VALUES (DATE '%s', 'Duplicado no banco', TRUE, now(), now())
                """.formatted(date)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void deletesAnnouncement() throws Exception {
        long id = createAnnouncement("2026-11-15", "Proclamacao da Republica", true);

        mockMvc.perform(delete("/announcements/{id}", id).cookie(admin))
                .andExpect(status().isNoContent());
    }

    /**
     * The interval filters by both ends, inclusive. The announcement is exactly the
     * case where the edge matters: whoever asks for "the month of September" expects day 1
     * and day 30 to be inside, not outside.
     */
    @Test
    void listFiltersByDateRange() throws Exception {
        createAnnouncement("2027-03-10", "Dentro do intervalo", true);
        createAnnouncement("2027-04-20", "Depois do intervalo", true);

        MvcResult result = mockMvc.perform(get("/announcements")
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
        createAnnouncement("2027-06-01", "Primeiro dia", true);
        createAnnouncement("2027-06-30", "Ultimo dia", false);

        MvcResult result = mockMvc.perform(get("/announcements")
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

    /**
     * The effect is mandatory: without it, an informative announcement would empty the
     * day by a typo, and nobody would know why.
     */
    @Test
    void rejectsAnnouncementWithoutSuspendsClasses() throws Exception {
        mockMvc.perform(post("/announcements")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2027-08-10","description":"Sem efeito declarado"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnnouncementWithoutDate() throws Exception {
        mockMvc.perform(post("/announcements")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Sem data","suspendsClasses":true}
                                """))
                .andExpect(status().isBadRequest());
    }
}
