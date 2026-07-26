-- Mudança pontual na agenda de um aluno num dia.
--
-- schedule_exception, e não "exception": além de "exception" ser um nome
-- infeliz para uma entidade Java, o prefixo diz de que exceção se trata.
CREATE TABLE schedule_exception (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    new_date DATE,
    new_time TIME,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- Uma exceção por aluno por dia: duas tornariam ambíguo o que acontece
    -- naquele dia — cancelou e remarcou? remarcou para onde?
    CONSTRAINT uk_schedule_exception_student_date UNIQUE (student_id, date),
    CONSTRAINT ck_schedule_exception_type CHECK (type IN ('CANCELED', 'RESCHEDULED')),
    -- O par destino existe se e somente se for remarcação. Remarcação sem
    -- destino é uma aula que some sem reaparecer; cancelamento com destino não
    -- tem significado nenhum.
    CONSTRAINT ck_schedule_exception_target CHECK (
        (type = 'RESCHEDULED' AND new_date IS NOT NULL AND new_time IS NOT NULL)
        OR (type = 'CANCELED' AND new_date IS NULL AND new_time IS NULL)
    )
);

-- A expansão da agenda pergunta duas coisas por dia: quem tem exceção em D
-- (para subtrair) e quem foi remarcado para D (para somar).
CREATE INDEX idx_schedule_exception_date ON schedule_exception (date);
CREATE INDEX idx_schedule_exception_new_date ON schedule_exception (new_date);
