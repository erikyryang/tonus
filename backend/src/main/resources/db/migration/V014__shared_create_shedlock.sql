-- Tabela de lock do ShedLock: schema padrão exigido pelo
-- JdbcTemplateLockProvider. Fica em shared, não em billing, porque é infra
-- genérica do agendador — mesmo raciocínio de event_publication (V006), que
-- também é infra do Modulith e não dado de domínio de um módulo de negócio.
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
