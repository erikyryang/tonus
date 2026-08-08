package br.com.tonus.registration;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract that the scheduling, billing and notification modules will consume:
 * phone/name by studentId, active students and students by teacher.
 *
 * Does not use @ApplicationModuleTest because SPEC-002 does not publish any event —
 * what needs to be exercised here is the query against the real Postgres. The
 * boundaries between modules are already checked by ArchitectureTest.
 */
class RegistrationFacadeIT extends AbstractRegistrationIntegrationTest {

    @Autowired
    private RegistrationFacade registrationFacade;

    @Test
    void exposesStudentNameAndPhoneById() throws Exception {
        long teacherId = createTeacher(unique("Professora Vera"));
        String name = unique("Aluno Wagner");
        long studentId = createStudent(name, teacherId);

        StudentContact contact = registrationFacade.findStudentContact(studentId).orElseThrow();

        assertThat(contact.studentId()).isEqualTo(studentId);
        assertThat(contact.name()).isEqualTo(name);
        assertThat(contact.phone()).isEqualTo("+5511988880000");
    }

    @Test
    void returnsEmptyForUnknownStudent() {
        assertThat(registrationFacade.findStudentContact(99_999_999L)).isEmpty();
    }

    @Test
    void listsOnlyActiveStudents() throws Exception {
        long teacherId = createTeacher(unique("Professor Xavier"));
        long active = createStudent(unique("Aluna Ativa"), teacherId);
        long inactive = createStudent(unique("Aluno Inativo"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", inactive).cookie(admin))
                .andExpect(status().isOk());

        List<Long> ids = registrationFacade.findActiveStudents().stream()
                .map(StudentSummary::studentId)
                .toList();

        assertThat(ids).contains(active).doesNotContain(inactive);
    }

    @Test
    void listsStudentsOfAGivenTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professora Yara"));
        long otherTeacherId = createTeacher(unique("Professor Zeca"));
        long mine = createStudent(unique("Aluno Da Yara"), teacherId);
        long other = createStudent(unique("Aluno Do Zeca"), otherTeacherId);

        List<StudentSummary> students = registrationFacade.findStudentsByTeacher(teacherId);

        assertThat(students).extracting(StudentSummary::studentId).contains(mine).doesNotContain(other);
        assertThat(students).allSatisfy(s -> assertThat(s.teacherId()).isEqualTo(teacherId));
    }

    @Test
    void returnsEmptyListForTeacherWithoutStudents() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Alunos"));

        assertThat(registrationFacade.findStudentsByTeacher(teacherId)).isEmpty();
    }
}
