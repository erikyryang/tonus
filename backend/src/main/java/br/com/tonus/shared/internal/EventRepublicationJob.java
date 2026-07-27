package br.com.tonus.shared.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reentrega o que ficou pendente no registry de eventos.
 *
 * Sem este job, "at-least-once" seria só uma frase: a linha em
 * event_publication ficava com completion_date nulo para sempre e ninguém a
 * lia. O sintoma concreto era Evolution fora do ar por dez minutos e os avisos
 * daquele período nunca saindo — registrados como pendentes, sem nada
 * reclamando.
 *
 * Fica em shared porque o registry é de shared: republicar é infra do
 * mecanismo de eventos, não do módulo que por acaso falhou mais. auth também
 * consome eventos, e um job dentro de notification estaria reprocessando
 * entrega alheia.
 *
 * Complementa {@code spring.modulith.events.republish-outstanding-events-on-restart}:
 * aquilo cobre o que ficou pendente enquanto a aplicação estava fora do ar,
 * isto cobre o que falha com ela no ar — que é o caso comum.
 */
@Component
public class EventRepublicationJob {

    private static final Logger log = LoggerFactory.getLogger(EventRepublicationJob.class);

    private final IncompleteEventPublications incompletePublications;
    private final EventRegistryProperties properties;
    private final Clock clock;

    EventRepublicationJob(IncompleteEventPublications incompletePublications,
                          EventRegistryProperties properties, Clock clock) {
        this.incompletePublications = incompletePublications;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Reentrega só o que está dentro da janela.
     *
     * O piso ({@code minAge}) separa entrega que falhou de listener que ainda
     * está trabalhando. O teto ({@code maxAge}) existe porque o registry não
     * guarda contador de tentativas: sem ele, o que falha em definitivo volta a
     * ser tentado a cada rodada, indefinidamente. Passado o teto, a linha
     * continua no banco com completion_date nulo — é o registro de que aquele
     * aviso nunca saiu, e apagá-la seria esconder a única evidência disso.
     *
     * Usa o predicado, e não {@code resubmitIncompletePublicationsOlderThan},
     * porque aquela variante só tem piso.
     *
     * {@code lockAtLeastFor} zerado de propósito: reprocessar de novo é
     * inofensivo — todo listener é idempotente — e um piso de tempo tornaria o
     * job intestável, que é o problema documentado em {@code BillingJobIT}.
     */
    @Scheduled(cron = "${shared.events.republish-cron:0 */5 * * * *}")
    @SchedulerLock(name = "event-republication-job", lockAtMostFor = "PT5M", lockAtLeastFor = "PT0S")
    public void republishIncompletePublications() {
        Instant now = clock.instant();
        Instant oldestWorthRetrying = now.minus(properties.maxAge());
        Instant youngestWorthRetrying = now.minus(properties.minAge());
        AtomicInteger abandoned = new AtomicInteger();

        incompletePublications.resubmitIncompletePublications(publication -> {
            Instant publishedAt = publication.getPublicationDate();
            if (publishedAt.isBefore(oldestWorthRetrying)) {
                abandoned.incrementAndGet();
                return false;
            }
            return publishedAt.isBefore(youngestWorthRetrying);
        });

        if (abandoned.get() > 0) {
            // Em WARN, e todo rodada: são avisos que nunca chegaram a ninguém.
            // O alerta só cala quando alguém olhar as linhas e resolver — que é
            // exatamente o comportamento desejado para entrega perdida.
            log.warn("{} entrega(s) pendente(s) há mais de {} — desistido de reentregar; "
                            + "consultar event_publication com completion_date nulo",
                    abandoned.get(), properties.maxAge());
        }
    }
}
