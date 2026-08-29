-- A row now records how a notice was handled: DELIVERED (the message went
-- out) or DISCARDED (the student was deliberately not notified — left the
-- studio or vanished from registration).
--
-- The DISCARDED marker exists for the idempotency of at-least-once delivery:
-- without it, a redelivery of the same event (a fan-out that failed on
-- another student keeps the publication incomplete) could send a stale notice
-- to a student who was reactivated in the meantime. Recording the skip makes
-- "already handled" hold for the skip too.
--
-- This is not a step backwards from the old rationale: a SEND FAILURE still
-- leaves no row (the send and the row share a transaction, and a failed send
-- rolls it back). A discard is a decision, not a failure.
ALTER TABLE notification_log
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DELIVERED';

ALTER TABLE notification_log
    ADD CONSTRAINT ck_notification_log_status CHECK (status IN ('DELIVERED', 'DISCARDED'));