package br.com.tonus.billing;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O dia de vencimento é o dia do mês em que o aluno se cadastrou, e mais nada.
 *
 * A regra anterior — aniversário do pagamento, recalculado a cada baixa — foi
 * substituída (ver SPEC-004): o vencimento andava sozinho pelo calendário, e
 * nem o ADMIN nem o aluno tinham como saber de que dia era a mensalidade sem
 * consultar a última baixa.
 */
class SubscriptionDueDayFromRegistrationIT extends AbstractBillingIntegrationTest {

    @Test
    void dueDayComesFromTheDayOfMonthTheStudentWasRegistered() throws Exception {
        long studentId = studentRegisteredOn("2026-03-05");

        postSubscription(studentId, "200.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDay").value(5));
    }

    /**
     * Dia 31 entra como 31, sem aparar na criação. Quem apara é a resolução por
     * competência ({@code Subscription.dueDateFor}), no mês em que o dia não
     * existe — guardar 28 aqui perderia a informação para março, que tem 31.
     */
    @Test
    void registrationOnTheLastDayOfALongMonthKeepsDay31() throws Exception {
        long studentId = studentRegisteredOn("2026-01-31");

        postSubscription(studentId, "200.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDay").value(31));
    }

    /**
     * Não é editável: o dia vem do cadastro, e um campo que o cliente pudesse
     * mandar faria a assinatura discordar do aluno a que ela pertence.
     */
    @Test
    void dueDayIsNotEditable() throws Exception {
        long studentId = studentRegisteredOn("2026-03-05");
        long subscriptionId = createSubscription(studentId, "200.00");

        mockMvc.perform(put("/subscriptions/{id}", subscriptionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"monthlyAmount":"300.00","dueDay":20}
                                """.formatted(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyAmount").value(300.00))
                .andExpect(jsonPath("$.dueDay").value(5));
    }

    /**
     * O aluno é criado pela API e a data de cadastro é reescrita por SQL: o
     * {@code created_at} vem do JPA auditing com o Clock fixo do teste, então
     * todo aluno nasceria no mesmo dia e o teste não distinguiria "dia do
     * cadastro" de "dia de hoje".
     */
    private long studentRegisteredOn(String date) throws Exception {
        long studentId = createStudent(unique("Aluno De " + date), createTeacher(unique("Professor De " + date)));
        jdbcTemplate.update("UPDATE student SET created_at = ?::timestamp WHERE id = ?", date + " 09:00:00", studentId);
        return studentId;
    }
}
