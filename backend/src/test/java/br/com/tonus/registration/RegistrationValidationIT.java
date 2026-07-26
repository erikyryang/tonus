package br.com.tonus.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entrada inválida tem que virar 400 com Problem Details. Sem os limites no
 * DTO, um nome maior que a coluna só estouraria no INSERT e viraria 500.
 */
class RegistrationValidationIT extends AbstractRegistrationIntegrationTest {

    @Test
    void rejectsNameLongerThanColumn() throws Exception {
        String tooLong = "x".repeat(256);

        mockMvc.perform(post("/teachers")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511999990000"}
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void acceptsNameAtColumnLimit() throws Exception {
        mockMvc.perform(post("/teachers")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511999990000"}
                                """.formatted("x".repeat(255))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsPhoneOutOfE164Format() throws Exception {
        for (String invalid : new String[]{"11999990000", "+55 11 99999-0000", "+0511999990000", "telefone"}) {
            mockMvc.perform(post("/teachers")
                            .cookie(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Professor Formato","phone":"%s"}
                                    """.formatted(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsBlankPhoneOnStudent() throws Exception {
        long teacherId = createTeacher(unique("Professora Formato"));

        mockMvc.perform(post("/students")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aluno Sem Telefone","phone":"","teacherId":%d}
                                """.formatted(teacherId)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Teto de página: sem limite, um size gigante viraria varredura da tabela
     * inteira numa única resposta.
     */
    @Test
    void capsPageSize() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/teachers").cookie(admin).param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }
}
