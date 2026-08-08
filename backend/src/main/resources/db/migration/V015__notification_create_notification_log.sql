-- Log of notices already delivered to the student.
--
-- The row is born BEFORE the send and only survives if the send succeeded:
-- a failure brings down the whole transaction. That is why there is no status
-- column or attempt counter — a failed attempt never exists here. What tracks
-- a delivery that didn't close is event_publication.completion_date.
--
-- student_id without FK, same pattern as default_schedule/subscription:
-- integrity is guaranteed in code, via RegistrationFacade.
CREATE TABLE notification_log (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    template VARCHAR(30) NOT NULL,
    reference_key VARCHAR(60) NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- The acceptance criterion "reprocessed event doesn't resend message"
    -- expressed in the database. Delivery is at-least-once: code-only checks
    -- have a window between querying and sending, and it is the constraint
    -- that closes that window.
    CONSTRAINT uk_notification_log_template_student_reference
        UNIQUE (template, student_id, reference_key),
    CONSTRAINT ck_notification_log_template CHECK (template IN (
        'CLASS_CANCELED', 'CLASS_RESCHEDULED', 'HOLIDAY_CREATED',
        'INVOICE_DUE_SOON', 'INVOICE_OVERDUE', 'PAYMENT_REGISTERED'
    ))
);

-- History of a student's notices.
CREATE INDEX idx_notification_log_student ON notification_log (student_id);
