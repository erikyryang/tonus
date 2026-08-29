package br.com.tonus.billing;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whoever configures a subscription, lists an invoice and registers a
 * payment is the ADMIN. A student only reads their own slice, via
 * /me/invoices.
 */
class BillingAccessControlIT extends AbstractBillingIntegrationTest {

    @Test
    void studentCannotCreatePlan() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Acesso Negado"));
        long studentId = createStudent(unique("Aluno Sem Poder De Cobranca"), teacherId);

        mockMvc.perform(post("/plans")
                        .cookie(linkedStudentCookie(studentId))
                        .with(csrf())
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
     * The list of who is overdue is a billing situation for the whole
     * studio: the teacher doesn't see it, even though it's just a list of
     * ids.
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

        mockMvc.perform(post("/invoices/{id}/payment", 1L).cookie(linkedTeacherCookie(teacherId)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRejectedEverywhere() throws Exception {
        mockMvc.perform(get("/plans")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/invoices")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/me/invoices")).andExpect(status().isUnauthorized());
    }
}
