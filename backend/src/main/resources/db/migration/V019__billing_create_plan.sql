-- Plans, enrollments and the studio billing settings (SPEC-012).
--
-- Additive on purpose: nothing here drops `subscription` or touches `invoice`.
-- While the code doesn't exist, these tables stay empty and inert — and that
-- is what lets the migration happen in stages with the database consistent at
-- the end of each one. The subscription dies in V022, after everything.
CREATE TABLE plan (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    monthly_price NUMERIC(10, 2) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_plan_monthly_price CHECK (monthly_price > 0)
);

-- The student -> plan link. student_id without FK, same decision as
-- subscription.student_id (see docs/ddl.md): FK between modules couples in the
-- schema in a way that ApplicationModules.verify() doesn't see. plan_id with
-- a normal FK, because plan and enrollment are in the same module.
CREATE TABLE plan_enrollment (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL REFERENCES plan (id),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- The same plan is not enrolled twice: a second row would make ambiguous
    -- how much to charge. To charge double, the way is a plan with another
    -- price.
    CONSTRAINT uk_plan_enrollment_student_plan UNIQUE (student_id, plan_id)
);

-- The daily job builds the invoice per student; the student screen asks for
-- their plans.
CREATE INDEX idx_plan_enrollment_student ON plan_enrollment (student_id);

-- Studio billing settings. Single row guaranteed by constraint, not by
-- convention: two rows would make ambiguous which discount applies, and the
-- answer would become "whichever LIMIT 1 picked".
CREATE TABLE billing_settings (
    id BIGINT PRIMARY KEY,
    multi_plan_discount_percent NUMERIC(5, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_billing_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_billing_settings_discount CHECK (multi_plan_discount_percent BETWEEN 0 AND 100)
);

-- Born without discount: the studio decides the value on the screen, and an
-- invented default here would give a discount to someone who never asked for
-- one.
INSERT INTO billing_settings (id, multi_plan_discount_percent, created_at, updated_at)
VALUES (1, 0, now(), now());
