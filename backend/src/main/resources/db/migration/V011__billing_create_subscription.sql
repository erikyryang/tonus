-- Student monthly subscription: amount and due day.
--
-- student_id without FK, same decision as default_schedule.student_id (see
-- docs/ddl.md): FK between modules couples in the schema in a way that
-- ApplicationModules.verify() doesn't see. Integrity is guaranteed in code,
-- via RegistrationFacade.
CREATE TABLE subscription (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    monthly_amount NUMERIC(10, 2) NOT NULL,
    due_day INT NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- A student has at most one subscription: two would make ambiguous which
    -- one the daily job should invoice.
    CONSTRAINT uk_subscription_student UNIQUE (student_id),
    CONSTRAINT ck_subscription_due_day CHECK (due_day BETWEEN 1 AND 31)
);
