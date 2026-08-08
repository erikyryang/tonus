-- Student default schedule: one row per student, Mon-Fri.
--
-- Without teacher_id on purpose: the class teacher is always the one from the
-- current link in student. Storing it here too would create two truths, which
-- diverge at the first teacher change.
--
-- student_id without FK, same decision as users.student_id (see docs/ddl.md):
-- FK between modules couples in the schema in a way that
-- ApplicationModules.verify() doesn't see. Integrity is guaranteed in code,
-- via RegistrationFacade.
--
-- time_of_day, not "time", because TIME is an SQL type name.
CREATE TABLE default_schedule (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    time_of_day TIME NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- "One schedule per student" is a business rule, not a modeling detail:
    -- it is the acceptance criterion "second schedule -> 409" expressed in the
    -- database.
    CONSTRAINT uk_default_schedule_student UNIQUE (student_id)
);
