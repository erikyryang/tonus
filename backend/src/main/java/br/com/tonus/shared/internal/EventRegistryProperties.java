package br.com.tonus.shared.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * As duas políticas do registry de eventos: quando reprocessar o que não fechou
 * e por quanto tempo guardar o que fechou.
 *
 * {@code minAge} é o que separa "entrega que falhou" de "entrega que ainda está
 * rodando": o listener é assíncrono e sua linha fica incompleta enquanto ele
 * trabalha. Republicar cedo demais entrega o mesmo evento a um listener que
 * ainda não terminou — os listeners são idempotentes e aguentam, mas é trabalho
 * jogado fora e log confuso.
 *
 * {@code retention} é por quanto tempo a entrega concluída continua consultável
 * antes de ser apagada. Sem poda a tabela só cresce; com poda curta demais some
 * a resposta para "esse aviso chegou a sair?", que é a primeira pergunta quando
 * um aluno reclama.
 */
@ConfigurationProperties(prefix = "shared.events")
public record EventRegistryProperties(Duration minAge, Duration maxAge, Duration retention) {

    public EventRegistryProperties {
        if (minAge == null) {
            minAge = Duration.ofMinutes(5);
        }
        // Um dia. Aviso de aula cancelada não vale mais nada depois disso, e a
        // tabela não tem contador de tentativas: sem teto, uma entrega que
        // falha em definitivo — telefone desligado, aluno que sumiu — é
        // retentada a cada 5 minutos para sempre, ~105 mil vezes por ano.
        if (maxAge == null) {
            maxAge = Duration.ofDays(1);
        }
        if (retention == null) {
            retention = Duration.ofDays(7);
        }
    }
}
