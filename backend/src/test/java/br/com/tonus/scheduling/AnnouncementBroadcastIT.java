package br.com.tonus.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static br.com.tonus.support.Eventually.eventually;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Disparo manual de mensagem livre para todo aluno ativo (mudança 9).
 *
 * Endpoint e validação são de scheduling (dono de announcement); o fan-out em
 * si — idempotência por broadcastId, falha parcial, reprocessamento — tem
 * teste próprio em notification (BroadcastFanOutIT), publicando o evento
 * direto. Aqui só a prova de ponta a ponta: o disparo por HTTP realmente
 * chega ao aluno.
 */
class AnnouncementBroadcastIT extends AbstractSchedulingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void broadcastReachesActiveStudent() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Disparo"));
        long studentId = createStudent(unique("Aluno Do Disparo"), teacherId);

        mockMvc.perform(post("/announcements/broadcast")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Aula de sábado cancelada por manutenção."}
                                """))
                .andExpect(status().isAccepted());

        eventually("o aluno recebeu o disparo", () -> countBroadcastLogs(studentId) == 1);
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/announcements/broadcast")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingMessage() throws Exception {
        mockMvc.perform(post("/announcements/broadcast")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMessageOverLimit() throws Exception {
        String tooLong = "x".repeat(501);
        mockMvc.perform(post("/announcements/broadcast")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsMessageAtTheLimit() throws Exception {
        String atLimit = "x".repeat(500);
        mockMvc.perform(post("/announcements/broadcast")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + atLimit + "\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void teacherCannotBroadcast() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Poder De Disparo"));

        mockMvc.perform(post("/announcements/broadcast")
                        .cookie(linkedTeacherCookie(teacherId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Teste"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/announcements/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Teste"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private long countBroadcastLogs(long studentId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_log WHERE student_id = ? AND template = 'BROADCAST'",
                Long.class, studentId);
        return count;
    }
}
