-- Holiday becomes announcement (SPEC-003).
--
-- The concept was too narrow: the studio needs to notify the student of things
-- that don't cancel class (address change, year-end party, street
-- construction), and the only tool that existed to reach everyone suppressed
-- the day as a side effect. `suspends_classes` separates the two things: the
-- notice and the suppression.
--
-- Each existing holiday becomes an announcement that suspends — exactly what
-- it meant. The old table dies after the copy, not before.
CREATE TABLE announcement (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    suspends_classes BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- Inherited from uk_holiday_date, and for the same two reasons: "is day D
    -- suspended?" needs a single answer for the schedule expansion, and the
    -- date is the notice idempotency key in notification_log (reference_key).
    -- Two announcements on the same day would make the second notice get
    -- swallowed by the UNIQUE there, and the student wouldn't receive it.
    CONSTRAINT uk_announcement_date UNIQUE (date)
);

INSERT INTO announcement (date, description, suspends_classes, created_at, updated_at)
SELECT date, description, TRUE, created_at, updated_at FROM holiday;

DROP TABLE holiday;
