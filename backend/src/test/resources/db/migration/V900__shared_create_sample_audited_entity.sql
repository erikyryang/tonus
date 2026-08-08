-- Table exists only in the test environment, to exercise the shared module's
-- generic audit mechanism (BaseEntity + @Audited) without depending on
-- entities from other modules not yet implemented.
CREATE TABLE sample_audited_entity (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
