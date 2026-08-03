-- Disparo manual de comunicado (mudança 9): o ADMIN manda mensagem livre para
-- todos os alunos ativos, fora do ciclo de agenda ou cobrança. Precisa de
-- template próprio porque o texto não é nenhum dos já cadastrados — é livre,
-- então quem identifica o disparo no log é o broadcastId (UUID gerado na
-- hora), não a data: dois disparos legítimos no mesmo dia não podem ser
-- engolidos pela UNIQUE(template, student_id, reference_key).
ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_template;

ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_template CHECK (template IN (
    'CLASS_CANCELED', 'CLASS_RESCHEDULED', 'ANNOUNCEMENT_SUSPENSION', 'ANNOUNCEMENT_NOTICE',
    'INVOICE_DUE_SOON', 'INVOICE_OVERDUE', 'PAYMENT_REGISTERED', 'BROADCAST'));
