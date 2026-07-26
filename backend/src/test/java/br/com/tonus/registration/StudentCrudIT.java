package br.com.tonus.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentCrudIT extends AbstractRegistrationIntegrationTest {

    @Test
    void createsStudentActiveByDefaultLinkedToTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professora Dora"));
        String name = unique("Aluno Eduardo");

        mockMvc.perform(post("/students")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511988887777","teacherId":%d}
                                """.formatted(name, teacherId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.phone").value("+5511988887777"))
                .andExpect(jsonPath("$.teacherId").value(teacherId))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getsStudentById() throws Exception {
        long teacherId = createTeacher(unique("Professor Fabio"));
        String name = unique("Aluna Gabi");
        long id = createStudent(name, teacherId);

        mockMvc.perform(get("/students/{id}", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.teacherId").value(teacherId));
    }

    @Test
    void updatesStudent() throws Exception {
        long teacherId = createTeacher(unique("Professor Hugo"));
        long id = createStudent(unique("Aluno Igor"), teacherId);
        String newName = unique("Aluno Igor Lima");

        mockMvc.perform(put("/students/{id}", id)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511966665555","teacherId":%d}
                                """.formatted(newName, teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.phone").value("+5511966665555"));
    }

    /**
     * Trocar o professor de um aluno é operação individual válida — a spec só
     * põe a troca em lote fora de escopo. Até agora só os caminhos de recusa
     * (professor inexistente, professor inativo) tinham teste.
     */
    @Test
    void movesStudentToAnotherActiveTeacher() throws Exception {
        long originalTeacher = createTeacher(unique("Professora Original"));
        long newTeacher = createTeacher(unique("Professor Novo"));
        String name = unique("Aluno Remanejado");
        long studentId = createStudent(name, originalTeacher);

        mockMvc.perform(put("/students/{id}", studentId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511988880000","teacherId":%d}
                                """.formatted(name, newTeacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(newTeacher));

        mockMvc.perform(get("/students/{id}", studentId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(newTeacher));
    }

    /**
     * Minimização LGPD: a API não aceita campo fora do escopo da spec.
     */
    @Test
    void ignoresFieldsOutsideSpecScope() throws Exception {
        long teacherId = createTeacher(unique("Professora Julia"));

        mockMvc.perform(post("/students")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511955554444","teacherId":%d,
                                 "cpf":"12345678900","address":"Rua X, 100"}
                                """.formatted(unique("Aluna Kelly"), teacherId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownStudent() throws Exception {
        mockMvc.perform(get("/students/{id}", 99_999_999L).cookie(admin))
                .andExpect(status().isNotFound());
    }
}
