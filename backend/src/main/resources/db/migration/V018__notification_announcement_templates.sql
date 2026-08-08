-- The holiday notice became two: the announcement that suspends classes and
-- the one that only informs (SPEC-005). The CHECK lists the existing
-- templates, so changing the name requires touching it.
--
-- The old rows are rewritten before the CHECK changes — in the reverse order
-- the database would reject the constraint because of the rows it no longer
-- accepts. HOLIDAY_CREATED always meant "there won't be class", so the
-- destination is the suspension template.
--
-- The idempotency key (template, student_id, reference_key) still holds: the
-- reference_key is the date, and whoever was already notified of the D
-- holiday now carries the ANNOUNCEMENT_SUSPENSION row with the same D — an
-- event redelivery doesn't send the message again.
UPDATE notification_log SET template = 'ANNOUNCEMENT_SUSPENSION' WHERE template = 'HOLIDAY_CREATED';

ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_template;

ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_template CHECK (template IN (
    'CLASS_CANCELED', 'CLASS_RESCHEDULED', 'ANNOUNCEMENT_SUSPENSION', 'ANNOUNCEMENT_NOTICE',
    'INVOICE_DUE_SOON', 'INVOICE_OVERDUE', 'PAYMENT_REGISTERED'));
