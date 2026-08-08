-- Existing subscriptions become plans and enrollments (SPEC-012).
--
-- One plan per DISTINCT AMOUNT, not one per student. The consequence is
-- assumed and is in the spec: two students who paid 350 now share the 350
-- plan, and changing its price later changes it for both. One plan per
-- student would preserve independence at the cost of N identical plans —
-- exactly what the plans screen exists to avoid.
--
-- The name is ugly on purpose ("Plano R$ 350,00"): it is a migration label,
-- and the ADMIN renames it on the screen. Inventing "Mensal Padrão" would be
-- pretending that information existed somewhere.
--
-- The formatting is done by hand, without to_char with G/D: those depend on
-- the server lc_numeric, and the plan name would change depending on the
-- database configuration where the migration runs.
INSERT INTO plan (name, monthly_price, active, created_at, updated_at)
SELECT DISTINCT
       'Plano R$ ' || replace(to_char(monthly_amount, 'FM9999990.00'), '.', ','),
       monthly_amount,
       TRUE,
       now(),
       now()
FROM subscription;

-- Each student in the enrollment of their amount, preserving whether it was
-- active. The created_at comes from the subscription: the enrollment wasn't
-- born now, it is the same relationship under another name.
--
-- The join by VALUE is only unambiguous because `plan` is empty at this
-- point: V019 just created it and Flyway applies both in the same pass, so
-- there is no earlier plan with the same price for the join to match twice.
-- Separating the two migrations in time — deploying a version with only
-- V019 — would break that guarantee.
INSERT INTO plan_enrollment (student_id, plan_id, active, created_at, updated_at)
SELECT s.student_id, p.id, s.active, s.created_at, s.updated_at
FROM subscription s
JOIN plan p ON p.monthly_price = s.monthly_amount;
