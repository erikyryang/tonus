package br.com.tonus.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static br.com.tonus.support.Eventually.eventually;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Manual dispatch of a free-form message to every active student (change 9).
 *
 * Endpoint and validation belong to scheduling (owner of announcement); the fan-out
 * itself — idempotency by broadcastId, partial failure, reprocessing — has its
 * own test in notification (BroadcastFanOutIT), publishing the event
 * directly. Here just the end-to-end proof: the HTTP dispatch really
 * reaches the student.
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
