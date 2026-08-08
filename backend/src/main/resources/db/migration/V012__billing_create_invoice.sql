-- Invoice of a competence period (month) of a subscription.
--
-- reference_month always stores day 1 of the month: it is the YearMonth
-- representation in the database, to reuse DATE instead of two separate
-- columns for year and month.
--
-- Normal FK to subscription: the two tables are in the same module, so
-- there is no cross-module coupling like the one avoided in
-- student_id/teacher_id.
CREATE TABLE invoice (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscription (id),
    reference_month DATE NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- One invoice per subscription per competence period: it is the
    -- acceptance criterion "job running 2x doesn't duplicate invoice"
    -- expressed in the database — the job idempotency becomes a constraint,
    -- not just a code check.
    CONSTRAINT uk_invoice_subscription_period UNIQUE (subscription_id, reference_month),
    CONSTRAINT ck_invoice_status CHECK (status IN ('OPEN', 'PAID', 'OVERDUE'))
);

-- The daily job scans invoices by status (open expiring -> overdue) and
-- by due date (who is due today).
CREATE INDEX idx_invoice_status ON invoice (status);
CREATE INDEX idx_invoice_due_date ON invoice (due_date);
