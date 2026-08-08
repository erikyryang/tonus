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
 * The teacher's grid is their schedule grouped by time.
 *
 * There is no class-group entity: the group **emerges** from teacher + time.
 * Creating a class-group as an entity would force keeping it in sync with the
 * individual schedules, and the two would diverge at the first reschedule.
 *
 * The class's teacher comes from the student's link, not from the schedule —
 * that is why changing a student's teacher moves the class between grids
 * without touching anything in the schedule.
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
      * The teacher comes from the student's link: moving the student to
      * another teacher takes the class out of one grid and puts it in the
      * other, with no schedule change at all.
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
      * The teacher needs to talk to the student of their own class — to cancel,
      * to warn about a delay. The phone is on their row for that purpose, and
      * the phone that comes is that student's, not anyone's.
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
      * The purpose is the student themselves, and it bounds the data: a phone
      * of another teacher's student cannot appear anywhere in the response.
      *
      * {@link #gridShowsOnlyOwnStudents} only looks at the ids of the queried
      * time, and would pass even if the foreign phone came hanging off another
      * field. Here the assertion is over the whole body.
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
      * The phone travels through the path that has a purpose, and only through
      * it. The ADMIN's schedule shows the whole studio and is not a
      * customer-service tool: data that does not travel does not leak
      * (minimization, LGPD).
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
