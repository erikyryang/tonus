package br.com.tonus.billing;

import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os dois caminhos da SPEC-012 que prometem trabalhar em lote: a listagem de
 * planos (que conta matriculados por plano) e o job diário (que resolve preço e
 * data de cadastro do estúdio inteiro).
 *
 * Sem estes testes as duas promessas são só comentário: voltar ao N+1 não
 * quebra nenhuma asserção de comportamento, e o custo aparece em produção, no
 * job que roda de madrugada sobre todo mundo.
 *
 * As asserções são tetos, não contagens exatas — o número certo depende de
 * detalhes do Hibernate que mudam sem o desenho mudar. O teto separa "lote" de
 * "por linha", que é o que importa.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"})
class BillingQueryCountIT extends AbstractBillingIntegrationTest {

    private static final int PLANS = 6;

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private br.com.tonus.billing.internal.BillingService billingService;

    /**
     * A contagem de matriculados sai de uma consulta para a página inteira, e
     * não de uma por plano — era assim que estava escrito primeiro, e o teto
     * abaixo é o que impede a volta.
     */
    @Test
    void planListingDoesNotCountPerPlan() throws Exception {
        String marker = unique("Contagem");
        for (int i = 0; i < PLANS; i++) {
            long planId = createPlan(marker + "-plano-" + i, "100.00");
            enroll(createStudent(marker + "-aluno-" + i, createTeacher(marker + "-prof-" + i)), planId);
        }

        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        mockMvc.perform(get("/plans").param("size", String.valueOf(PLANS)).cookie(admin))
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount())
                .as("consultas para montar uma página de %d planos", PLANS)
                .isLessThanOrEqualTo(5);
    }

    /**
     * O job resolve planos, matrículas, desconto e datas de cadastro **por
     * conjunto**, antes de decidir quem faturar. Perguntar a data de cadastro
     * aluno a aluno seria N consultas a mais na única rotina que trabalha com o
     * estúdio inteiro de madrugada.
     *
     * Os alunos daqui vencem em outro dia de propósito: assim o job faz todas
     * as buscas e não gera nada, e a contagem isola o que se quer garantir — o
     * caminho de leitura — do custo de inserir fatura, que é proporcional por
     * natureza (insert + auditoria + registro do evento) e não é N+1.
     */
    @Test
    void dailyJobResolvesEverythingInBulkBeforeDeciding() throws Exception {
        // O job varre o estúdio inteiro e o Postgres é compartilhado pela
        // suíte: matrícula deixada por outra classe entraria na conta e o teto
        // mediria o resto da suíte, não este desenho. Mesmo recurso do
        // AnnouncementFanOutIT — silencia o resto e devolve no fim.
        List<Long> previouslyActive = deactivateExistingEnrollments();
        try {
            String marker = unique("Job");
            long planId = createPlan(marker + "-plano", "100.00");
            for (int i = 0; i < PLANS; i++) {
                long studentId = createStudent(marker + "-aluno-" + i, createTeacher(marker + "-prof-" + i));
                enroll(studentId, planId);
                // Cadastrado num dia 5; "hoje" para o relógio da suíte é dia 27.
                jdbcTemplate.update("UPDATE student SET created_at = ?::timestamp WHERE id = ?",
                        "2026-03-05 09:00:00", studentId);
            }

            Statistics statistics = sessionFactory.getStatistics();
            statistics.clear();

            billingService.generateDueInvoices();

            // Planos, matrículas, configuração e datas de cadastro: quatro
            // consultas, e o número não anda com o tamanho do estúdio.
            assertThat(statistics.getPrepareStatementCount())
                    .as("consultas de leitura do job para %d alunos com plano ativo", PLANS)
                    .isLessThanOrEqualTo(5);
        } finally {
            reactivate(previouslyActive);
        }
    }

    private List<Long> deactivateExistingEnrollments() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM plan_enrollment WHERE active = TRUE", Long.class);
        jdbcTemplate.update("UPDATE plan_enrollment SET active = FALSE WHERE active = TRUE");
        return ids;
    }

    private void reactivate(List<Long> ids) {
        for (Long id : ids) {
            jdbcTemplate.update("UPDATE plan_enrollment SET active = TRUE WHERE id = ?", id);
        }
    }
}
