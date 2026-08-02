package br.com.tonus.billing;

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
    void studentCannotCreatePlan() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Acesso Negado"));
        long studentId = createStudent(unique("Aluno Sem Poder De Cobranca"), teacherId);

        mockMvc.perform(post("/plans")
                        .cookie(linkedStudentCookie(studentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Plano Proibido","monthlyPrice":"200.00"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotListInvoices() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Acesso As Faturas"));

        mockMvc.perform(get("/invoices").cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    /**
     * A lista de quem está atrasado é situação de cobrança do estúdio inteiro:
     * o professor não a vê, mesmo sendo uma lista de ids.
     */
    @Test
    void teacherCannotListOverdueStudentIds() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Acesso A Inadimplencia"));

        mockMvc.perform(get("/invoices/overdue-student-ids").cookie(linkedTeacherCookie(teacherId)))
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
        mockMvc.perform(get("/plans")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/invoices")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/me/invoices")).andExpect(status().isUnauthorized());
    }
}
