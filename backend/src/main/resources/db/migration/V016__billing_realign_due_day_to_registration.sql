-- SPEC-004: due_day stopped being the anniversary of the last payment and
-- became the day of the month the student registered.
--
-- Existing subscriptions carry the old rule value, and nothing would
-- reconcile them by itself: the field is no longer editable and the write-off
-- no longer touches it. Without this UPDATE the studio would live with two
-- rules at once — the new one for those who subscribe from now on, the old
-- one frozen for those who already subscribed —, and nobody could explain why
-- two students registered on the same day are due on different days.
--
-- This migration reads `student`, which is from another module. It is a
-- one-off data fix, not schema coupling: it creates no FK, view or permanent
-- dependency, and at runtime billing still doesn't see registration — the
-- registration day comes through RegistrationFacade.
--
-- `created_at` is written in UTC by Hibernate, in a TIMESTAMP without time
-- zone. The day that matters is the studio time zone (see ClockConfig):
-- without the conversion, someone registered at 9:30 pm Brasília time would
-- be counted on the following day.
UPDATE subscription s
SET due_day = EXTRACT(DAY FROM (st.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo'))::int,
    updated_at = now()
FROM student st
WHERE st.id = s.student_id
  AND s.due_day <> EXTRACT(DAY FROM (st.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo'))::int;
