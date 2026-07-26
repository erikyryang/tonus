CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    entity_name VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('CREATE', 'UPDATE')),
    changed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_name, entity_id);
