package br.com.tonus.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério de aceite: segundo horário para o mesmo aluno → 409, **via
 * constraint, não só validação**.
 *
 * Por isso são dois testes e não um. O primeiro prova o contrato REST; o
 * segundo escreve direto no banco, contornando a API inteira, e prova que a
 * regra "um horário por aluno" continua valendo mesmo se a validação de
 * aplicação for removida por engano. Um teste só que passasse pela API não
 * distinguiria uma coisa da outra.
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
     * Horário aponta para aluno que existe. Sem FK entre módulos (mesma decisão
     * de {@code users.student_id}, ver docs/ddl.md), quem garante isso é o
     * scheduling consultando a RegistrationFacade — então precisa de teste.
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
