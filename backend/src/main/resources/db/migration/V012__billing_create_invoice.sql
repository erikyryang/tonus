-- Fatura de uma competência (mês) de uma assinatura.
--
-- reference_month guarda sempre o dia 1 do mês: é a representação de
-- YearMonth no banco, para reaproveitar DATE em vez de duas colunas
-- separadas de ano e mês.
--
-- FK normal para subscription: as duas tabelas são do mesmo módulo, então
-- não há o acoplamento entre módulos que se evita em student_id/teacher_id.
CREATE TABLE invoice (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscription (id),
    reference_month DATE NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- Uma fatura por assinatura por competência: é o critério de aceite
    -- "job rodando 2x não duplica fatura" expresso no banco — a idempotência
    -- do job vira constraint, não só verificação em código.
    CONSTRAINT uk_invoice_subscription_period UNIQUE (subscription_id, reference_month),
    CONSTRAINT ck_invoice_status CHECK (status IN ('OPEN', 'PAID', 'OVERDUE'))
);

-- O job diário varre faturas por status (abertas vencendo -> atrasadas) e
-- por vencimento (quem vence hoje).
CREATE INDEX idx_invoice_status ON invoice (status);
CREATE INDEX idx_invoice_due_date ON invoice (due_date);
