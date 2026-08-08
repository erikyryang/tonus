-- Manual announcement broadcast (change 9): the ADMIN sends a free-form
-- message to all active students, outside the schedule or billing cycle. It
-- needs its own template because the text is not any of the registered ones —
-- it is free-form, so what identifies the broadcast in the log is the
-- broadcastId (UUID generated on the spot), not the date: two legitimate
-- broadcasts on the same day can't be swallowed by UNIQUE(template,
-- student_id, reference_key).
ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_template;

ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_template CHECK (template IN (
    'CLASS_CANCELED', 'CLASS_RESCHEDULED', 'ANNOUNCEMENT_SUSPENSION', 'ANNOUNCEMENT_NOTICE',
    'INVOICE_DUE_SOON', 'INVOICE_OVERDUE', 'PAYMENT_REGISTERED', 'BROADCAST'));
