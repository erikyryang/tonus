package br.com.tonus.registration;

import br.com.tonus.auth.AbstractAuthIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes do módulo registration.
 *
 * O seed é feito pela própria API REST (e não por repositório) de propósito:
 * os repositórios vivem em {@code registration.internal.domain} e o teste roda
 * no pacote raiz do módulo. Usar HTTP mantém o teste preso ao contrato público,
 * que é justamente o que os critérios de aceite descrevem.
 *
 * Os testes compartilham um Postgres único para a JVM, sem rollback entre
 * classes: todo nome gerado leva um sufixo aleatório e nenhuma asserção depende
 * de contagem global da tabela.
 *
 * É pública porque scheduling (SPEC-003) precisa semear aluno e professor para
 * montar agenda — mesmo motivo de {@link AbstractAuthIntegrationTest} ser
 * pública. O acoplamento com a API do registration fica confinado aqui.
 */
public abstract class AbstractRegistrationIntegrationTest extends AbstractAuthIntegrationTest {

    @Autowired
    protected ObjectMapper objectMapper;

    protected Cookie admin;

    @BeforeEach
    protected void authenticateAsAdmin() throws Exception {
        admin = adminCookie("admin-registration@tonus.com");
    }

    protected String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected long createTeacher(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/teachers")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511999990000"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    protected long createStudent(String name, long teacherId) throws Exception {
        MvcResult result = mockMvc.perform(post("/students")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511988880000","teacherId":%d}
                                """.formatted(name, teacherId)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    protected long idOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }
}
