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
 * Mudança em default_schedule gera registro em audit — decisão de arquitetura
 * (docs/ddl.md), junto com invoice.status.
 *
 * O motivo é concreto: trocar o horário de um aluno muda a agenda de todo mundo
 * daquele horário, e semanas depois ninguém lembra quem mudou o quê. Cadastro
 * de aluno e professor, por contraste, não é auditado — a diferença é
 * proposital e por isso merece teste.
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
     * A contraprova: aluno não é auditado. Sem isto, o teste acima passaria
     * mesmo que alguém anotasse @Audited em tudo, e a decisão de auditar só o
     * que importa viraria letra morta.
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
