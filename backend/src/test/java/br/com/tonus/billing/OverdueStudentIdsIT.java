package br.com.tonus.billing;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem está com fatura atrasada, em ids de aluno.
 *
 * Existe para a tela de alunos poder mostrar a situação de cobrança sem que
 * {@code registration} passe a consultar {@code billing} — a pergunta é
 * respondida por quem é dono da fatura, e quem cruza as duas listas é o cliente.
 * A aresta do grafo de módulos continua não existindo.
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
     * O aluno aparece uma vez, não uma por fatura. Quem consome monta um
     * conjunto para cruzar com a lista de alunos, e id repetido é ruído que o
     * cliente teria de limpar — a resposta é sobre alunos, não sobre faturas.
     */
    @Test
    void studentWithTwoOverdueInvoicesAppearsOnce() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Aluno Muito Atrasado"));
        long studentId = createStudent(unique("Aluno Com Duas Atrasadas"), teacherId);
        long subscriptionId = createSubscription(studentId, "200.00", 10);
        seedInvoice(subscriptionId, "2026-06-01", "200.00", "2026-06-10", "OVERDUE");
        seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-10", "OVERDUE");

        assertThat(overdueStudentIds()).filteredOn(id -> id == studentId).hasSize(1);
    }

    /**
     * Quem saiu devendo continua devendo. A fatura sobrevive à desativação (não
     * existe DELETE de aluno, justamente para o histórico de cobrança não sumir),
     * e filtrar o inativo aqui esconderia da tela o caso mais caro que existe:
     * o aluno que foi embora com fatura em aberto.
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
        long subscriptionId = createSubscription(studentId, "200.00", 10);
        seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-10", status);
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
