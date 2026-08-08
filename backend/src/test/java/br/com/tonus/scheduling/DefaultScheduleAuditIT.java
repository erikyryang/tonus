package br.com.tonus.scheduling;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A change in default_schedule generates an audit record — architecture
 * decision (docs/ddl.md), along with invoice.status.
 *
 * The reason is concrete: changing a student's time changes everyone's
 * schedule in that time slot, and weeks later no one remembers who changed
 * what. Student and teacher registration, by contrast, is not audited — the
 * difference is deliberate and therefore deserves a test.
 */
class DefaultScheduleAuditIT extends AbstractSchedulingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void creatingScheduleIsAudited() throws Exception {
        long studentId = seedStudent("Aluno Auditado Na Criacao");

        long id = createDefaultSchedule(studentId, "07:00");

        assertThat(auditActions(id)).contains("CREATE");
    }

    @Test
    void updatingScheduleIsAudited() throws Exception {
        long studentId = seedStudent("Aluno Auditado Na Troca");
        long id = createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(put("/default-schedules/{id}", id)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"time":"09:00"}
                                """.formatted(studentId)))
                .andExpect(status().isOk());

        assertThat(auditActions(id)).contains("CREATE", "UPDATE");
    }

     /**
      * The counter-proof: students are not audited. Without this, the test
      * above would pass even if someone annotated @Audited everywhere, and the
      * decision to audit only what matters would become dead letter.
      */
    @Test
    void studentChangesAreNotAudited() throws Exception {
        long studentId = seedStudent("Aluno Nao Auditado");

        Long rows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_log WHERE entity_name = 'Student' AND entity_id = ?
                """, Long.class, String.valueOf(studentId));

        assertThat(rows).isZero();
    }

    private List<String> auditActions(long scheduleId) {
        return jdbcTemplate.queryForList("""
                SELECT action FROM audit_log
                WHERE entity_name = 'DefaultSchedule' AND entity_id = ?
                ORDER BY id
                """, String.class, String.valueOf(scheduleId));
    }

    private long seedStudent(String name) throws Exception {
        return createStudent(unique(name), createTeacher(unique("Professor Da Auditoria")));
    }
}
