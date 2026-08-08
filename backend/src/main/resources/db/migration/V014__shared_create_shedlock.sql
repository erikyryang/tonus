-- ShedLock lock table: standard schema required by the
-- JdbcTemplateLockProvider. It lives in shared, not billing, because it is
-- generic scheduler infra — same reasoning as event_publication (V006), which
-- is also Modulith infra and not domain data of a business module.
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
