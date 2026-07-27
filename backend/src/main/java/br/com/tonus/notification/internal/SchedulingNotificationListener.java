package br.com.tonus.notification.internal;

import br.com.tonus.notification.internal.domain.MessageTemplate;
import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentSummary;
import br.com.tonus.scheduling.ClassCanceled;
import br.com.tonus.scheduling.ClassRescheduled;
import br.com.tonus.scheduling.HolidayCreated;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * O que muda na agenda do aluno, ele fica sabendo.
 *
 * A entrega é at-least-once, então cada método aqui roda mais de uma vez para
 * o mesmo fato; a idempotência mora na constraint única de notification_log,
 * checada em NotificationService.
 */
@Component
class SchedulingNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SchedulingNotificationListener.class);

    private final NotificationService notifications;
    private final RegistrationFacade registration;
    private final NotificationProperties properties;

    SchedulingNotificationListener(NotificationService notifications, RegistrationFacade registration,
                                   NotificationProperties properties) {
        this.notifications = notifications;
        this.registration = registration;
        this.properties = properties;
    }

    @ApplicationModuleListener
    public void on(ClassCanceled event) {
        notifications.notifyStudent(event.studentId(), MessageTemplate.CLASS_CANCELED,
                event.date().toString(), MessageTemplate.date(event.date()));
    }

    /**
     * A chave leva origem E destino. A mesma aula pode ser remarcada duas
     * vezes a partir do mesmo dia — apagar a exceção e recriá-la republica o
     * evento com originalDate igual e destino diferente —, e chavear só pela
     * origem faria o segundo aviso ser confundido com reentrega do primeiro.
     */
    @ApplicationModuleListener
    public void on(ClassRescheduled event) {
        String reference = "%s|%s|%s".formatted(event.originalDate(), event.newDate(), event.newTime());
        notifications.notifyStudent(event.studentId(), MessageTemplate.CLASS_RESCHEDULED, reference,
                MessageTemplate.date(event.originalDate()),
                MessageTemplate.date(event.newDate()),
                MessageTemplate.time(event.newTime()));
    }

    /**
     * Feriado é o único evento que vira N mensagens.
     *
     * Quem são os avisados vem do registration, não da agenda: o consumidor
     * natural seria quem sabe quem tem aula no dia, mas notification não
     * depende de scheduling para consulta e abrir essa aresta por um evento
     * não se paga. O preço é avisar também aluno ativo sem horário padrão.
     *
     * A falha de um aluno não interrompe os demais — cada um commita por conta
     * própria —, mas ao final a exceção é obrigatória: sem ela o Modulith
     * marcaria o evento como entregue e quem ficou de fora nunca seria avisado.
     *
     * Falhas seguidas, porém, interrompem. Não é azar com N alunos, é o
     * gateway fora do ar, e insistir custa um read timeout por aluno: com 500
     * alunos e uma Evolution pendurada, o laço segura uma thread do executor
     * por mais de uma hora e o feriado seguinte espera atrás dela. Abortar cedo
     * chega no mesmo lugar — a publicação fica incompleta e o job de
     * republicação tenta de novo depois — sem pagar o pedágio.
     */
    @ApplicationModuleListener
    public void on(HolidayCreated event) {
        String reference = event.date().toString();
        int limit = properties.consecutiveFailureLimit();
        List<Long> failed = new ArrayList<>();
        int consecutiveFailures = 0;

        for (StudentSummary student : registration.findActiveStudents()) {
            try {
                notifications.notifyStudent(student.studentId(), MessageTemplate.HOLIDAY_CREATED,
                        reference, MessageTemplate.date(event.date()));
                consecutiveFailures = 0;
            } catch (RuntimeException e) {
                log.warn("Falha ao avisar o aluno {} do feriado {}", student.studentId(), reference, e);
                failed.add(student.studentId());
                if (++consecutiveFailures >= limit) {
                    throw new WhatsAppDeliveryException(
                            "Feriado %s abortado após %d falhas seguidas; pendentes a partir de %s"
                                    .formatted(reference, consecutiveFailures, failed));
                }
            }
        }

        if (!failed.isEmpty()) {
            throw new WhatsAppDeliveryException(
                    "Feriado %s não avisado aos alunos %s".formatted(reference, failed));
        }
    }
}
