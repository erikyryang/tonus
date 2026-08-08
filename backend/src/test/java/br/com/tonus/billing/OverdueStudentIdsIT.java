package br.com.tonus.billing;

import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who has an overdue invoice, as student ids.
 *
 * It exists so the students screen can show the billing status without
 * {@code registration} starting to query {@code billing} — the question is
 * answered by whoever owns the invoice, and the one who crosses the two
 * lists is the client. The edge of the module graph still does not exist.
 */
class OverdueStudentIdsIT extends AbstractBillingIntegrationTest {

    @Test
    void listsOnlyStudentsWithAnOverdueInvoice() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Inadimplencia"));
        long overdue = studentWithInvoice(teacherId, "Aluno Atrasado", "OVERDUE");
        long open = studentWithInvoice(teacherId, "Aluno Em Dia", "OPEN");
        long paid = studentWithInvoice(teacherId, "Aluno Que Pagou", "PAID");

        assertThat(overdueStudentIds())
                .contains(overdue)
                .doesNotContain(open, paid);
    }

    /**
     * The student appears once, not once per invoice. The consumer builds a
     * set to cross with the student list, and a repeated id is noise the
     * client would have to clean up — the answer is about students, not
     * invoices.
     */
    @Test
    void studentWithTwoOverdueInvoicesAppearsOnce() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Aluno Muito Atrasado"));
        long studentId = createStudent(unique("Aluno Com Duas Atrasadas"), teacherId);
        seedInvoice(studentId, "2026-06-01", "200.00", "2026-06-10", "OVERDUE");
        seedInvoice(studentId, "2026-07-01", "200.00", "2026-07-10", "OVERDUE");

        assertThat(overdueStudentIds()).filteredOn(id -> id == studentId).hasSize(1);
    }

    /**
     * Whoever left owing still owes. The invoice survives deactivation (there
     * is no student DELETE, precisely so the billing history does not
     * disappear), and filtering out the inactive one here would hide from the
     * screen the most expensive case there is: the student who left with an
     * open invoice.
     */
    @Test
    void deactivatedStudentWithOverdueInvoiceStaysInTheList() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Aluno Que Saiu"));
        long studentId = studentWithInvoice(teacherId, "Aluno Que Saiu Devendo", "OVERDUE");

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());

        assertThat(overdueStudentIds()).contains(studentId);
    }

    private long studentWithInvoice(long teacherId, String name, String status) throws Exception {
        long studentId = createStudent(unique(name), teacherId);
        seedInvoice(studentId, "2026-07-01", "200.00", "2026-07-10", status);
        return studentId;
    }

    private List<Long> overdueStudentIds() throws Exception {
        MvcResult result = mockMvc.perform(get("/invoices/overdue-student-ids").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        List<Long> ids = new ArrayList<>();
        for (JsonNode id : json(result)) {
            ids.add(id.asLong());
        }
        return ids;
    }
}
