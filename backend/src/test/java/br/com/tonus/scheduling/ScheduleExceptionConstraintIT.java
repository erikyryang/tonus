package br.com.tonus.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A exceção tem uma regra que o tipo sozinho não expressa: REMARCADO exige
 * nova data e nova hora, CANCELADO exige que elas sejam nulas. Isso é um CHECK
 * no banco, não só validação de DTO — uma remarcação sem destino seria uma aula
 * que some sem reaparecer, e um cancelamento com destino seria ambíguo.
 *
 * Os testes vão pelos dois caminhos de propósito: API para o contrato, SQL
 * direto para provar que a constraint existe mesmo.
 */
class ScheduleExceptionConstraintIT extends AbstractSchedulingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsCancellation() throws Exception {
        long studentId = seedStudentWithSchedule("Aluno Que Cancela");

        cancelClass(studentId, MONDAY);
    }

    @Test
    void createsReschedule() throws Exception {
        long studentId = seedStudentWithSchedule("Aluno Que Remarca");

        rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");
    }

    @Test
    void rescheduleWithoutNewDateIsRejected() throws Exception {
        long studentId = seedStudentWithSchedule("Aluno Remarcado Sem Destino");

        postException("""
                {"studentId":%d,"date":"%s","type":"RESCHEDULED"}
                """.formatted(studentId, MONDAY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancellationWithNewDateIsRejected() throws Exception {
        long studentId = seedStudentWithSchedule("Aluno Cancelado Com Destino");

        postException("""
                {"studentId":%d,"date":"%s","type":"CANCELED","newDate":"%s","newTime":"08:00"}
                """.formatted(studentId, MONDAY, WEDNESDAY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secondExceptionForSameStudentAndDayIsRejected() throws Exception {
        long studentId = seedStudentWithSchedule("Aluno De Uma Excecao So");
        cancelClass(studentId, MONDAY);

        postException("""
                {"studentId":%d,"date":"%s","type":"CANCELED"}
                """.formatted(studentId, MONDAY))
                .andExpect(status().isConflict());
    }

    @Test
    void databaseRejectsRescheduleWithoutNewDate() throws Exception {
        long studentId = seedStudentWithSchedule("Aluno Do Check Remarcado");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO schedule_exception (student_id, date, type, new_date, new_time, created_at, updated_at)
                VALUES (?, DATE '%s', 'RESCHEDULED', NULL, NULL, now(), now())
                """.formatted(MONDAY), studentId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsCancellationWithNewDate() throws Exception {
        long studentId = seedStudentWithSchedule("Aluno Do Check Cancelado");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO schedule_exception (student_id, date, type, new_date, new_time, created_at, updated_at)
                VALUES (?, DATE '%s', 'CANCELED', DATE '%s', TIME '08:00', now(), now())
                """.formatted(MONDAY, WEDNESDAY), studentId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsSecondExceptionForSameStudentAndDay() throws Exception {
        long studentId = seedStudentWithSchedule("Aluno Do Unique Da Excecao");
        cancelClass(studentId, MONDAY);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO schedule_exception (student_id, date, type, new_date, new_time, created_at, updated_at)
                VALUES (?, DATE '%s', 'CANCELED', NULL, NULL, now(), now())
                """.formatted(MONDAY), studentId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void databaseRejectsUnknownExceptionType() throws Exception {
        long studentId = seedStudentWithSchedule("Aluno Do Tipo Invalido");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO schedule_exception (student_id, date, type, new_date, new_time, created_at, updated_at)
                VALUES (?, DATE '%s', 'FERIAS', NULL, NULL, now(), now())
                """.formatted(TUESDAY), studentId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long seedStudentWithSchedule(String name) throws Exception {
        long teacherId = createTeacher(unique("Professor Da Excecao"));
        long studentId = createStudent(unique(name), teacherId);
        createDefaultSchedule(studentId, "07:00");
        return studentId;
    }
}
