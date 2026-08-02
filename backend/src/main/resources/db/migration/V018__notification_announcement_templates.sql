-- O aviso de feriado virou dois: o comunicado que suspende as aulas e o que só
-- informa (SPEC-005). O CHECK lista os templates existentes, então trocar o
-- nome exige mexer nele.
--
-- As linhas antigas são reescritas antes de o CHECK mudar — na ordem inversa o
-- banco recusaria a constraint por causa das linhas que ela não aceita mais.
-- HOLIDAY_CREATED sempre significou "não vai ter aula", então o destino é o
-- template de suspensão.
--
-- A chave de idempotência (template, student_id, reference_key) continua
-- valendo: o reference_key é a data, e quem já foi avisado do feriado de D
-- carrega agora a linha de ANNOUNCEMENT_SUSPENSION com o mesmo D — uma
-- reentrega do evento não manda a mensagem de novo.
UPDATE notification_log SET template = 'ANNOUNCEMENT_SUSPENSION' WHERE template = 'HOLIDAY_CREATED';

ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_template;

ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_template CHECK (template IN (
    'CLASS_CANCELED', 'CLASS_RESCHEDULED', 'ANNOUNCEMENT_SUSPENSION', 'ANNOUNCEMENT_NOTICE',
    'INVOICE_DUE_SOON', 'INVOICE_OVERDUE', 'PAYMENT_REGISTERED'));
