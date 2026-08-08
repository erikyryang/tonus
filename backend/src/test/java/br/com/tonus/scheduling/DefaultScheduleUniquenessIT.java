package br.com.tonus.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance criterion: second schedule for the same student → 409, **via
 * constraint, not just validation**.
 *
 * That is why there are two tests and not one. The first proves the REST
 * contract; the second writes directly to the database, bypassing the whole
 * API, and proves that the "one schedule per student" rule still holds even
 * if the application validation is removed by mistake. A single test going
 * through the API would not distinguish one from the other.
 */
class DefaultScheduleUniquenessIT extends AbstractSchedulingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void secondScheduleForSameStudentIsRejected() throws Exception {
        long studentId = seedStudent("Aluno De Um Horario So");

        createDefaultSchedule(studentId, "07:00");

        postDefaultSchedule(studentId, "08:00")
                .andExpect(status().isConflict());
    }

    @Test
    void databaseRejectsSecondScheduleEvenBypassingTheApi() throws Exception {
        long studentId = seedStudent("Aluno Do Insert Direto");
        createDefaultSchedule(studentId, "07:00");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO default_schedule (student_id, time_of_day, created_at, updated_at)
                VALUES (?, TIME '09:00', now(), now())
                """, studentId))
                .isInstanceOf(DuplicateKeyException.class);
    }

     /**
      * A schedule points to a student that exists. Without an FK between
      * modules (same decision as {@code users.student_id}, see docs/ddl.md),
      * what guarantees this is scheduling querying the RegistrationFacade — so
      * it needs a test.
      */
    @Test
    void scheduleForUnknownStudentIsRejected() throws Exception {
        postDefaultSchedule(999_999_999L, "07:00")
                .andExpect(status().isBadRequest());
    }

    private long seedStudent(String name) throws Exception {
        return createStudent(unique(name), createTeacher(unique("Professor Do Horario")));
    }
}
