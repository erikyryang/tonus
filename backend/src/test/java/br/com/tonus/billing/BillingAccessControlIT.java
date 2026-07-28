package br.com.tonus.billing;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem configura assinatura, lista fatura e dá baixa é o ADMIN. Aluno só lê a
 * própria fatia, por /me/invoices.
 */
class BillingAccessControlIT extends AbstractBillingIntegrationTest {

    @Test
    void studentCannotCreateSubscription() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Acesso Negado"));
        long studentId = createStudent(unique("Aluno Sem Poder De Cobranca"), teacherId);

        mockMvc.perform(post("/subscriptions")
                        .cookie(linkedStudentCookie(studentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"monthlyAmount":"200.00","dueDay":10}
                                """.formatted(studentId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotListInvoices() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Acesso As Faturas"));

        mockMvc.perform(get("/invoices").cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotRegisterPayment() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Poder De Baixa"));

        mockMvc.perform(post("/invoices/{id}/payment", 1L).cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRejectedEverywhere() throws Exception {
        mockMvc.perform(get("/subscriptions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/invoices")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/me/invoices")).andExpect(status().isUnauthorized());
    }

    private Cookie linkedTeacherCookie(long teacherId) throws Exception {
        String login = unique("billing-teacher") + "@tonus.com";
        mockMvc.perform(post("/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s","role":"TEACHER","teacherId":%d}
                                """.formatted(login, DEFAULT_PASSWORD, teacherId)))
                .andExpect(status().isCreated());
        return extractCookie(login(login, DEFAULT_PASSWORD), ACCESS_COOKIE);
    }
}
