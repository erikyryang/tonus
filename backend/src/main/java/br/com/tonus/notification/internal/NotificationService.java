package br.com.tonus.notification.internal;

import br.com.tonus.notification.internal.domain.MessageTemplate;
import br.com.tonus.notification.internal.domain.NotificationLog;
import br.com.tonus.notification.internal.domain.NotificationLogRepository;
import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentContact;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avisa um aluno, uma vez só.
 *
 * Roda em transação própria (REQUIRES_NEW) porque um único evento pode virar N
 * avisos: no feriado, cada aluno precisa commitar sozinho. Tudo num commit só
 * significaria que falhar no aluno 150 desfaz as 149 linhas já gravadas e o
 * reprocessamento reenvia 149 mensagens — falha parcial virando duplicação em
 * massa, que é justamente o pior efeito colateral possível aqui.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RegistrationFacade registration;
    private final NotificationLogRepository logs;
    private final WhatsAppSender sender;
    private final Clock clock;

    NotificationService(RegistrationFacade registration, NotificationLogRepository logs,
                        WhatsAppSender sender, Clock clock) {
        this.registration = registration;
        this.logs = logs;
        this.sender = sender;
        this.clock = clock;
    }

    /**
     * Grava primeiro, envia depois. O INSERT tem de chegar ao banco antes da
     * chamada externa: é a constraint única, e não a consulta acima dela, que
     * segura duas entregas concorrentes do mesmo evento — entre consultar e
     * enviar existe uma janela, e o registry entrega at-least-once.
     *
     * Se o envio falhar, a exceção derruba esta transação, a linha some e a
     * publicação continua incompleta para ser reprocessada.
     *
     * Na corrida — duas entregas do mesmo evento passando juntas pela consulta
     * — o INSERT perdedor viola a constraint e essa exceção também sobe. É o
     * comportamento certo, e de propósito não é tratada aqui: uma vez violada
     * a constraint a transação já está condenada, então engolir o erro só
     * trocaria o rollback por um UnexpectedRollbackException no commit. O
     * reprocessamento resolve, e na segunda passada a consulta acha a linha.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyStudent(Long studentId, MessageTemplate template,
                              String referenceKey, Object... args) {
        if (logs.existsByTemplateAndStudentIdAndReferenceKey(template, studentId, referenceKey)) {
            return;
        }

        // Quem saiu do estúdio não recebe mais nada — nem aviso de agenda, que
        // não tem sentido para quem não tem aula, nem cobrança, porque perseguir
        // dívida de ex-aluno é decisão de quem administra e não efeito colateral
        // de um job noturno. Vale a mesma assimetria do resto do projeto: o
        // automático serve para parar, não para continuar.
        //
        // studentIsActive cobre junto o aluno que nunca existiu. Nos dois casos
        // não se lança: reprocessar não traria o aluno de volta, e a publicação
        // ficaria pendente para sempre entupindo o registry.
        if (!registration.studentIsActive(studentId)) {
            log.info("Aviso {} descartado: aluno {} não está ativo no cadastro", template, studentId);
            return;
        }

        Optional<StudentContact> contact = registration.findStudentContact(studentId);
        if (contact.isEmpty()) {
            log.warn("Aviso {} descartado: aluno {} sumiu do cadastro", template, studentId);
            return;
        }

        logs.saveAndFlush(new NotificationLog(studentId, template, referenceKey, clock.instant()));
        sender.send(contact.get().phone(), template.render(contact.get().name(), args));

        // Nem telefone nem corpo da mensagem no log: os dois são dado pessoal.
        log.info("Aviso {} entregue ao aluno {}", template, studentId);
    }
}
