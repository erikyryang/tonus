package br.com.tonus.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério de aceite: "Criar aluno sem professor → 400".
 *
 * O vínculo é verificado nas duas camadas: a API rejeita com Problem Details e
 * o banco rejeita por conta própria (NOT NULL + FK). A constraint é regra de
 * negócio — se um dia alguém remover o @NotNull do DTO, o teste de banco ainda
 * segura.
 */
class StudentTeacherLinkIT extends AbstractRegistrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsStudentWithoutTeacher() throws Exception {
        mockMvc.perform(post("/students")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511944443333"}
                                """.formatted(unique("Aluno Sem Professor"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void rejectsStudentWithNullTeacher() throws Exception {
        mockMvc.perform(post("/students")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511944443333","teacherId":null}
                                """.formatted(unique("Aluno Professor Nulo"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsStudentWithUnknownTeacher() throws Exception {
        mockMvc.perform(post("/students")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511944443333","teacherId":99999999}
                                """.formatted(unique("Aluno Professor Inexistente"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseRejectsStudentWithoutTeacher() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
                VALUES (?, ?, NULL, TRUE, NOW(), NOW())
                """, unique("Aluno Direto No Banco"), "+5511933332222"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsStudentWithUnknownTeacher() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
                VALUES (?, ?, 99999999, TRUE, NOW(), NOW())
                """, unique("Aluno FK Quebrada"), "+5511933332222"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
