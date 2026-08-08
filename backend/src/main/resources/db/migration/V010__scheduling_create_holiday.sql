-- Holiday is global: one row applies to the whole studio. There is no teacher
-- or class holiday — if there were, "is there a holiday on D?" would stop
-- having a single answer and the schedule expansion would have to decide per
-- whom.
CREATE TABLE holiday (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_holiday_date UNIQUE (date)
);
