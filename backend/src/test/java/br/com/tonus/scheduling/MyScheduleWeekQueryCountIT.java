package br.com.tonus.scheduling;

import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A janela do aluno custa o mesmo que um dia: as consultas são pelo intervalo
 * inteiro, não uma rodada por data.
 *
 * Sem este teste a garantia não existe. Voltar a expandir dia a dia não quebra
 * nenhuma asserção de comportamento — a resposta fica idêntica —, e o custo só
 * apareceria em produção, na tela que todo aluno abre. Era assim antes: sete
 * chamadas HTTP, cada uma carregando todos os alunos ativos e todos os horários
 * padrão do estúdio para devolver a linha de um aluno.
 *
 * Classe separada porque `generate_statistics` muda as propriedades do
 * contexto, e ligá-lo para a suíte inteira acrescentaria overhead e um resumo
 * de métricas por sessão em todo log.
 *
 * O teto é folgado de propósito: o número exato depende de detalhes do
 * Hibernate que podem mudar sem que o desenho mude. O que ele separa é "por
 * intervalo" de "por dia" — com 14 dias, uma consulta por dia passaria longe.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"})
class MyScheduleWeekQueryCountIT extends AbstractSchedulingIntegrationTest {

    private static final int DAYS = 14;

    /**
     * Folga sobre o desenho em lote: aluno, horário padrão, feriados do
     * intervalo, exceções do intervalo e remarcações para o intervalo.
     */
    private static final int CEILING = 12;

    @Autowired
    private SessionFactory sessionFactory;

    @Test
    void windowDoesNotQueryPerDay() throws Exception {
        String marker = unique("Semana");
        long teacherId = createTeacher(marker + " Professor");
        long studentId = createStudent(marker + " Aluno", teacherId);
        createDefaultSchedule(studentId, "07:00");
        // Alunos vizinhos: a janela não pode ficar mais cara porque o estúdio
        // tem gente, que é exatamente o que a expansão completa fazia.
        for (int i = 0; i < 5; i++) {
            createDefaultSchedule(createStudent(marker + " Vizinho " + i, teacherId), "08:00");
        }
        Cookie student = linkedStudentCookie(studentId);

        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        mockMvc.perform(get("/me/schedule/week")
                        .param("from", MONDAY)
                        .param("days", String.valueOf(DAYS))
                        .cookie(student))
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount())
                .as("consultas para montar uma janela de %d dias", DAYS)
                .isLessThanOrEqualTo(CEILING);
    }
}
