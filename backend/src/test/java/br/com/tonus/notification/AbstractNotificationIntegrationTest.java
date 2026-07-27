package br.com.tonus.notification;

import br.com.tonus.notification.support.NotificationTestConfig;
import br.com.tonus.notification.support.RecordingWhatsAppSender;
import br.com.tonus.support.PostgresContainer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes do módulo notification.
 *
 * Roda como {@code @ApplicationModuleTest} de propósito: sobe notification e as
 * dependências que ele declara, e falha se o módulo precisar de alguém que não
 * declarou. Um {@code @SpringBootTest} inteiro esconderia exatamente esse erro.
 *
 * O seed vai por SQL porque a fachada do registration é só de consulta, por
 * decisão de arquitetura — não há como criar aluno por ela.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ActiveProfiles("test")
@Import(NotificationTestConfig.class)
abstract class AbstractNotificationIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    /**
     * O Postgres é compartilhado entre classes de teste e não há rollback entre
     * elas. Telefone único por aluno é o que deixa cada asserção olhar só para
     * os seus — sem isso, um aluno criado por outra classe entraria na conta.
     */
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected RecordingWhatsAppSender sender;

    @Autowired
    protected ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void resetSender() {
        sender.reset();
    }

    /**
     * Publica dentro de uma transação porque {@code @ApplicationModuleListener}
     * é {@code @TransactionalEventListener} em AFTER_COMMIT: sem transação que
     * comite, o evento é descartado e o listener nunca roda. É também o que
     * cria a linha em event_publication, sem a qual não há reprocessamento.
     */
    protected void publish(Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    protected Student seedStudent(String name) {
        String phone = "+55119%08d".formatted(PHONE_SEQUENCE.incrementAndGet());
        Long teacherId = jdbcTemplate.queryForObject("""
                INSERT INTO teacher (name, phone, active, created_at, updated_at)
                VALUES (?, ?, TRUE, now(), now())
                RETURNING id
                """, Long.class, "Professor " + name, phone.replace("+55119", "+55118"));
        Long studentId = jdbcTemplate.queryForObject("""
                INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, now(), now())
                RETURNING id
                """, Long.class, name, phone, teacherId);
        return new Student(studentId, name, phone);
    }

    protected long countLogs(long studentId, String template) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification_log
                WHERE student_id = ? AND template = ?
                """, Long.class, studentId, template);
    }

    /**
     * As duas contagens abaixo são recortadas pelo aluno dentro do payload
     * serializado. O Postgres é compartilhado e outras classes publicam os
     * mesmos tipos de evento: contar por tipo apenas misturaria testes.
     */
    protected long countCompletedPublications(String eventTypeSuffix, long studentId) {
        return countPublications(eventTypeSuffix, studentId, "completion_date IS NOT NULL");
    }

    protected long countIncompletePublications(String eventTypeSuffix, long studentId) {
        return countPublications(eventTypeSuffix, studentId, "completion_date IS NULL");
    }

    /**
     * O recorte por aluno usa regex, e não LIKE: {@code '%"studentId":15%'}
     * casa também com 150 e 151. Os ids são sequenciais e uma classe semeia
     * vários alunos, então o contador inflava de vez em quando e uma espera
     * terminava cedo — falha que só aparece em certas faixas de id.
     */
    private long countPublications(String eventTypeSuffix, long studentId, String completionClause) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM event_publication
                WHERE event_type LIKE ?
                  AND serialized_event ~ ?
                  AND %s
                """.formatted(completionClause),
                Long.class, "%" + eventTypeSuffix, "\"studentId\":" + studentId + "(\\D|$)");
    }

    /**
     * Desativa quem estiver ativo e devolve os ids, para o chamador restaurar.
     *
     * Só o fan-out precisa disso — ele pergunta ao registration quem está ativo
     * e a resposta é o banco inteiro, inclusive alunos de outras classes. A
     * restauração é obrigatória: o Postgres é compartilhado e sem rollback
     * entre classes, e deixar o estúdio inteiro desativado é a espécie de
     * pegadinha que quebra um teste distante sem relação aparente.
     */
    protected List<Long> deactivateExistingStudents() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM student WHERE active = TRUE", Long.class);
        jdbcTemplate.update("UPDATE student SET active = FALSE WHERE active = TRUE");
        return ids;
    }

    protected void reactivate(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return;
        }
        // IN com placeholders, e não ANY(?): o JdbcTemplate trata o array como
        // lista de parâmetros posicionais, não como um array de SQL.
        String placeholders = String.join(",", Collections.nCopies(studentIds.size(), "?"));
        jdbcTemplate.update("UPDATE student SET active = TRUE WHERE id IN (%s)".formatted(placeholders),
                studentIds.toArray());
    }

    protected record Student(Long id, String name, String phone) {
    }
}
