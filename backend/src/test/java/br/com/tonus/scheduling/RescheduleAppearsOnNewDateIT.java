package br.com.tonus.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critério de aceite: remarcação some da data original e aparece na nova data.
 *
 * As duas metades importam. Só sumir é cancelamento; só aparecer duplica o
 * aluno na semana. É o caso que prova que a expansão soma e subtrai da mesma
 * fonte, em vez de tratar exceção como um "extra" colado no fim.
 */
class RescheduleAppearsOnNewDateIT extends AbstractSchedulingIntegrationTest {

    @Test
    void rescheduledClassMovesToTheNewDate() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Remarcacao"));
        long studentId = createStudent(unique("Aluna Remarcada"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");

        assertThat(studentIdsOf(daySchedule(MONDAY))).doesNotContain(studentId);

        JsonNode moved = entryOf(daySchedule(WEDNESDAY), studentId, "RESCHEDULED");
        assertThat(moved.get("time").asText()).isEqualTo("08:00");
        assertThat(moved.get("origin").asText()).isEqualTo("RESCHEDULED");
    }

    /**
     * A remarcação carrega a hora nova, não a do template. Se voltasse a hora
     * padrão, o aluno apareceria no horário errado do dia certo.
     */
    @Test
    void rescheduledClassKeepsItsOwnTimeNotTheTemplateOne() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Hora Nova"));
        long studentId = createStudent(unique("Aluno Da Hora Nova"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        rescheduleClass(studentId, TUESDAY, WEDNESDAY, "19:00");

        assertThat(entryOf(daySchedule(WEDNESDAY), studentId, "RESCHEDULED").get("time").asText())
                .isEqualTo("19:00");
    }

    /**
     * O aluno remarcado para um dia em que já tem aula aparece duas vezes
     * naquele dia — a do template e a remarcada. São duas aulas de fato.
     */
    @Test
    void rescheduleIntoADayWithTemplateShowsBothClasses() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Aula Dobrada"));
        long studentId = createStudent(unique("Aluno Com Duas Aulas"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        rescheduleClass(studentId, MONDAY, TUESDAY, "20:00");

        List<Long> tuesday = studentIdsOf(daySchedule(TUESDAY));
        assertThat(tuesday).filteredOn(id -> id == studentId).hasSize(2);
    }

    /**
     * Remarcar para um sábado funciona: fim de semana não tem template, mas
     * nada impede uma reposição pontual lá.
     */
    @Test
    void rescheduleToWeekendIsVisible() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Reposicao"));
        long studentId = createStudent(unique("Aluno Da Reposicao"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        rescheduleClass(studentId, MONDAY, SATURDAY, "10:00");

        assertThat(entryOf(daySchedule(SATURDAY), studentId, "RESCHEDULED").get("time").asText())
                .isEqualTo("10:00");
    }

    /**
     * A origem faz parte da busca, e não é detalhe: o aluno remarcado para um
     * dia em que já tem aula aparece duas vezes naquele dia. Procurar só pelo
     * id acharia a aula do template — que vem antes na ordenação por hora — e o
     * teste passaria a afirmar algo sobre a aula errada.
     */
    private JsonNode entryOf(JsonNode day, long studentId, String origin) {
        for (JsonNode entry : day.get("entries")) {
            if (entry.get("studentId").asLong() == studentId && entry.get("origin").asText().equals(origin)) {
                return entry;
            }
        }
        throw new AssertionError("Aluno %d não tem aula %s em %s"
                .formatted(studentId, origin, day.get("date").asText()));
    }

    private List<Long> studentIdsOf(JsonNode day) {
        return day.get("entries").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
