-- Baixa de uma fatura: quando foi paga e quem registrou.
--
-- registered_by guarda o id do usuário ADMIN que deu baixa, sem FK: é uma
-- referência solta, mesmo padrão de student_id/teacher_id em outras tabelas
-- — billing não pode depender de auth (fecharia ciclo com auth->registration).
CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoice (id),
    payment_date DATE NOT NULL,
    registered_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- Uma fatura só pode ser paga uma vez: é o critério de aceite "baixa em
    -- fatura já paga -> 409" expresso no banco.
    CONSTRAINT uk_payment_invoice UNIQUE (invoice_id)
);
