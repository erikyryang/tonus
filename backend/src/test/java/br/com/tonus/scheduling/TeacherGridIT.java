package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A grade do professor é a agenda dele agrupada por hora.
 *
 * Não existe entidade turma: o grupo **emerge** de professor + hora. Criar
 * turma como entidade obrigaria a mantê-la em sincronia com os horários
 * individuais, e as duas divergiriam na primeira remarcação.
 *
 * O professor da aula vem do vínculo do aluno, não do horário — por isso trocar
 * o professor de um aluno move a aula de grade sem tocar em nada da agenda.
 */
class TeacherGridIT extends AbstractSchedulingIntegrationTest {

    @Test
    void groupsStudentsByHour() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Grade"));
        long first = createStudent(unique("Aluno Das Sete A"), teacherId);
        long second = createStudent(unique("Aluno Das Sete B"), teacherId);
        long third = createStudent(unique("Aluno Das Oito"), teacherId);
        createDefaultSchedule(first, "07:00");
        createDefaultSchedule(second, "07:00");
        createDefaultSchedule(third, "08:00");

        JsonNode grid = grid(linkedTeacherCookie(teacherId), MONDAY);

        assertThat(studentIdsAt(grid, "07:00")).containsExactlyInAnyOrder(first, second);
        assertThat(studentIdsAt(grid, "08:00")).containsExactly(third);
    }

    @Test
    void gridShowsOnlyOwnStudents() throws Exception {
        long mine = createTeacher(unique("Professor Dono Da Grade"));
        long other = createTeacher(unique("Professor Alheio"));
        long myStudent = createStudent(unique("Aluno Meu"), mine);
        long otherStudent = createStudent(unique("Aluno Do Outro"), other);
        createDefaultSchedule(myStudent, "07:00");
        createDefaultSchedule(otherStudent, "07:00");

        assertThat(studentIdsAt(grid(linkedTeacherCookie(mine), MONDAY), "07:00"))
                .contains(myStudent)
                .doesNotContain(otherStudent);
    }

    /**
     * O professor sai do vínculo do aluno: mover o aluno para outro professor
     * tira a aula de uma grade e põe na outra, sem nenhuma alteração de agenda.
     */
    @Test
    void changingStudentTeacherMovesTheClassBetweenGrids() throws Exception {
        long origin = createTeacher(unique("Professor De Origem"));
        long destination = createTeacher(unique("Professor De Destino"));
        String name = unique("Aluno Transferido");
        long studentId = createStudent(name, origin);
        createDefaultSchedule(studentId, "07:00");

        assertThat(studentIdsAt(grid(linkedTeacherCookie(origin), MONDAY), "07:00")).contains(studentId);

        mockMvc.perform(put("/students/{id}", studentId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511988880000","teacherId":%d}
                                """.formatted(name, destination)))
                .andExpect(status().isOk());

        assertThat(studentIdsAt(grid(linkedTeacherCookie(origin), MONDAY), "07:00")).doesNotContain(studentId);
        assertThat(studentIdsAt(grid(linkedTeacherCookie(destination), MONDAY), "07:00")).contains(studentId);
    }

    @Test
    void rescheduledClassAppearsInTheGridOfTheNewDay() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Grade Remarcada"));
        long studentId = createStudent(unique("Aluno Da Grade Remarcada"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        rescheduleClass(studentId, MONDAY, WEDNESDAY, "21:00");

        Cookie teacher = linkedTeacherCookie(teacherId);
        assertThat(studentIdsAt(grid(teacher, MONDAY), "07:00")).doesNotContain(studentId);
        assertThat(studentIdsAt(grid(teacher, WEDNESDAY), "21:00")).contains(studentId);
    }

    /**
     * O professor precisa falar com o aluno da própria aula — desmarcar, avisar
     * de atraso. O telefone vem na linha dele por essa finalidade, e o telefone
     * que vem é o daquele aluno, não o de qualquer um.
     */
    @Test
    void gridCarriesThePhoneOfEachStudent() throws Exception {
        long teacherId = createTeacher(unique("Professora Do Contato"));
        long first = createStudent(unique("Aluna Do Primeiro Telefone"), teacherId, "+5511911110000");
        long second = createStudent(unique("Aluno Do Segundo Telefone"), teacherId, "+5511922220000");
        createDefaultSchedule(first, "07:00");
        createDefaultSchedule(second, "07:00");

        JsonNode grid = grid(linkedTeacherCookie(teacherId), MONDAY);

        assertThat(phonesById(grid, "07:00"))
                .containsEntry(first, "+5511911110000")
                .containsEntry(second, "+5511922220000");
    }

    /**
     * A finalidade é o próprio aluno, e ela delimita o dado: o telefone de aluno
     * de outro professor não pode aparecer em lugar nenhum da resposta.
     *
     * {@link #gridShowsOnlyOwnStudents} olha só os ids do horário consultado, e
     * passaria mesmo se o telefone alheio viesse pendurado em outro campo. Aqui
     * a asserção é sobre o corpo inteiro.
     */
    @Test
    void gridNeverCarriesThePhoneOfAnotherTeachersStudent() throws Exception {
        long mine = createTeacher(unique("Professor Da Finalidade"));
        long other = createTeacher(unique("Professor Vizinho"));
        long myStudent = createStudent(unique("Aluno Meu Do Contato"), mine, "+5511944440000");
        long otherStudent = createStudent(unique("Aluno Alheio Do Contato"), other, "+5511955550000");
        createDefaultSchedule(myStudent, "07:00");
        createDefaultSchedule(otherStudent, "07:00");

        String grid = grid(linkedTeacherCookie(mine), MONDAY).toString();

        assertThat(grid).contains("+5511944440000").doesNotContain("+5511955550000");
    }

    /**
     * O telefone anda pelo caminho que tem finalidade, e só por ele. A agenda do
     * ADMIN mostra o estúdio inteiro e não é ferramenta de atendimento: dado que
     * não trafega não vaza (minimização, LGPD).
     */
    @Test
    void adminDayScheduleStillCarriesNoPhone() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Painel"));
        long studentId = createStudent(unique("Aluno Do Painel"), teacherId, "+5511933330000");
        createDefaultSchedule(studentId, "07:00");

        assertThat(daySchedule(MONDAY).toString()).doesNotContain("+5511933330000");
    }

    @Test
    void studentCannotUseTeacherEndpoint() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Bloqueio"));
        long studentId = createStudent(unique("Aluno Bisbilhoteiro"), teacherId);

        mockMvc.perform(get("/me/grid")
                        .param("date", MONDAY)
                        .cookie(linkedStudentCookie(studentId)))
                .andExpect(status().isForbidden());
    }

    private JsonNode grid(Cookie teacher, String date) throws Exception {
        MvcResult result = mockMvc.perform(get("/me/grid").param("date", date).cookie(teacher))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private Map<Long, String> phonesById(JsonNode grid, String time) {
        Map<Long, String> phones = new HashMap<>();
        for (JsonNode student : studentsAt(grid, time)) {
            phones.put(student.get("studentId").asLong(),
                    student.hasNonNull("studentPhone") ? student.get("studentPhone").asText() : null);
        }
        return phones;
    }

    private List<Long> studentIdsAt(JsonNode grid, String time) {
        return studentsAt(grid, time).stream().map(student -> student.get("studentId").asLong()).toList();
    }

    private List<JsonNode> studentsAt(JsonNode grid, String time) {
        for (JsonNode slot : grid.get("slots")) {
            if (slot.get("time").asText().equals(time)) {
                return StreamSupport.stream(slot.get("students").spliterator(), false).toList();
            }
        }
        return List.of();
    }
}
