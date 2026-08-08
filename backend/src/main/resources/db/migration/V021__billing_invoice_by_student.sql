-- The invoice now belongs to the student, not the subscription (SPEC-012).
--
-- The key changes from (subscription, competence period) to (student,
-- competence period): with N plans per student, "one invoice per
-- subscription" would leave the student with one invoice per plan, which is
-- exactly what the change came to avoid.
ALTER TABLE invoice ADD COLUMN student_id BIGINT;

-- The backfill runs BEFORE the NOT NULL on purpose: if any invoice had no
-- student — a subscription deleted by hand, for example —, the migration
-- fails here, loudly, instead of leaving the column partially filled.
UPDATE invoice i SET student_id = s.student_id FROM subscription s WHERE s.id = i.subscription_id;

ALTER TABLE invoice ALTER COLUMN student_id SET NOT NULL;

ALTER TABLE invoice DROP CONSTRAINT uk_invoice_subscription_period;
ALTER TABLE invoice ADD CONSTRAINT uk_invoice_student_period UNIQUE (student_id, reference_month);

ALTER TABLE invoice DROP COLUMN subscription_id;

-- The per-student listing (/me/invoices) and the delinquency cross-check.
CREATE INDEX idx_invoice_student ON invoice (student_id);
