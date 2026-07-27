package br.com.tonus.notification.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Um aviso entregue. A linha só existe se a mensagem saiu — a falha de envio
 * derruba a transação que a criou.
 *
 * referenceKey identifica o fato dentro do evento (a data, o id da fatura,
 * origem+destino da remarcação). Junto com template e studentId forma a chave
 * única que impede o reprocessamento de reenviar.
 */
@Entity
@Table(name = "notification_log")
public class NotificationLog extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template", nullable = false, length = 30)
    private MessageTemplate template;

    @Column(name = "reference_key", nullable = false, length = 60)
    private String referenceKey;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected NotificationLog() {
    }

    public NotificationLog(Long studentId, MessageTemplate template, String referenceKey, Instant sentAt) {
        this.studentId = studentId;
        this.template = template;
        this.referenceKey = referenceKey;
        this.sentAt = sentAt;
    }

    public Long getStudentId() {
        return studentId;
    }

    public MessageTemplate getTemplate() {
        return template;
    }

    public String getReferenceKey() {
        return referenceKey;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
