-- One-off change in a student's schedule on a day.
--
-- schedule_exception, not "exception": besides "exception" being an unlucky
-- name for a Java entity, the prefix says which exception it is.
CREATE TABLE schedule_exception (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    new_date DATE,
    new_time TIME,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- One exception per student per day: two would make ambiguous what happens
    -- that day — canceled and rescheduled? rescheduled to where?
    CONSTRAINT uk_schedule_exception_student_date UNIQUE (student_id, date),
    CONSTRAINT ck_schedule_exception_type CHECK (type IN ('CANCELED', 'RESCHEDULED')),
    -- The target pair exists if and only if it is a reschedule. A reschedule
    -- without a target is a class that disappears without reappearing; a
    -- cancellation with a target has no meaning at all.
    CONSTRAINT ck_schedule_exception_target CHECK (
        (type = 'RESCHEDULED' AND new_date IS NOT NULL AND new_time IS NOT NULL)
        OR (type = 'CANCELED' AND new_date IS NULL AND new_time IS NULL)
    )
);

-- The schedule expansion asks two things per day: who has an exception on D
-- (to subtract) and who was rescheduled to D (to add).
CREATE INDEX idx_schedule_exception_date ON schedule_exception (date);
CREATE INDEX idx_schedule_exception_new_date ON schedule_exception (new_date);
