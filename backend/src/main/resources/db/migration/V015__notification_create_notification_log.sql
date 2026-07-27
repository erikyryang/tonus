-- Registro dos avisos já entregues ao aluno.
--
-- A linha nasce ANTES do envio e só sobrevive se o envio deu certo: a falha
-- derruba a transação inteira. Por isso não há coluna de status nem contador
-- de tentativas — tentativa malsucedida nunca chega a existir aqui. Quem
-- rastreia entrega que não fechou é event_publication.completion_date.
--
-- student_id sem FK, mesmo padrão de default_schedule/subscription: a
-- integridade é garantida em código, via RegistrationFacade.
CREATE TABLE notification_log (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    template VARCHAR(30) NOT NULL,
    reference_key VARCHAR(60) NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- O critério de aceite "evento reprocessado não reenvia mensagem" expresso
    -- no banco. A entrega é at-least-once: a verificação em código sozinha tem
    -- janela entre consultar e enviar, e é a constraint que fecha essa janela.
    CONSTRAINT uk_notification_log_template_student_reference
        UNIQUE (template, student_id, reference_key),
    CONSTRAINT ck_notification_log_template CHECK (template IN (
        'CLASS_CANCELED', 'CLASS_RESCHEDULED', 'HOLIDAY_CREATED',
        'INVOICE_DUE_SOON', 'INVOICE_OVERDUE', 'PAYMENT_REGISTERED'
    ))
);

-- Histórico de avisos de um aluno.
CREATE INDEX idx_notification_log_student ON notification_log (student_id);
