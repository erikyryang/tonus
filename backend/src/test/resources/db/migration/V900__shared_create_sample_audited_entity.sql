-- Tabela existe só em ambiente de teste, para exercitar o mecanismo
-- genérico de auditoria do módulo shared (BaseEntity + @Audited) sem
-- depender de entidades de outros módulos ainda não implementados.
CREATE TABLE sample_audited_entity (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
