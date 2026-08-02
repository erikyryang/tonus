package br.com.tonus.billing;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A listagem resolve aluno, nome e pagamento em lote — uma consulta por coisa,
 * não por linha. Sem este teste a garantia não existe: voltar ao N+1 não quebra
 * nenhuma asserção de comportamento, e o custo só apareceria em produção.
 *
 * Classe separada porque `generate_statistics` muda as propriedades do
 * contexto, e ligá-lo em `application-test.yml` acrescentaria overhead e um
 * resumo de métricas por sessão no log de toda a suíte.
 *
 * A asserção é um teto, não uma contagem exata: o número certo depende de
 * detalhes do Hibernate (flush, contagem da paginação) que podem mudar sem que
 * o desenho mude. O teto separa "lote" de "por linha", que é o que importa —
 * com 6 alunos distintos, o N+1 antigo passava de 19 consultas.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"})
class InvoiceListingQueryCountIT extends AbstractBillingIntegrationTest {

    private static final int INVOICES = 6;

    /** Folga sobre o desenho em lote (invoice + subscription + student + payment + count). */
    private static final int CEILING = 10;

    @Autowired
    private SessionFactory sessionFactory;

    @Test
    void listingDoesNotQueryPerInvoice() throws Exception {
        String marker = unique("Lote");
        for (int i = 0; i < INVOICES; i++) {
            // Alunos distintos de propósito: o cache de primeiro nível do
            // Hibernate esconderia o N+1 se todas as faturas fossem do mesmo.
            long studentId = createStudent(marker + "-aluno-" + i, createTeacher(marker + "-prof-" + i));
            String month = "2026-%02d".formatted(i + 1);
            long invoiceId = seedInvoice(studentId, month + "-01", "200.00", month + "-10",
                    i % 2 == 0 ? "OVERDUE" : "PAID");
            if (i % 2 == 1) {
                seedPayment(invoiceId, month + "-08", 1L);
            }
        }

        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        mockMvc.perform(get("/invoices").param("size", String.valueOf(INVOICES)).cookie(admin))
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount())
                .as("consultas para montar uma página de %d faturas", INVOICES)
                .isLessThanOrEqualTo(CEILING);
    }
}
