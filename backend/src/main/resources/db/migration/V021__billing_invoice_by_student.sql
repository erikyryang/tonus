-- A fatura passa a ser do aluno, não da assinatura (SPEC-012).
--
-- A chave muda de (assinatura, competência) para (aluno, competência): com N
-- planos por aluno, "uma fatura por assinatura" deixaria o aluno com uma fatura
-- por plano, que é exatamente o que a mudança veio evitar.
ALTER TABLE invoice ADD COLUMN student_id BIGINT;

-- O backfill roda ANTES do NOT NULL de propósito: se algum invoice ficasse sem
-- aluno — assinatura apagada à mão, por exemplo —, a migration falha aqui, alto,
-- em vez de deixar a coluna meio preenchida.
UPDATE invoice i SET student_id = s.student_id FROM subscription s WHERE s.id = i.subscription_id;

ALTER TABLE invoice ALTER COLUMN student_id SET NOT NULL;

ALTER TABLE invoice DROP CONSTRAINT uk_invoice_subscription_period;
ALTER TABLE invoice ADD CONSTRAINT uk_invoice_student_period UNIQUE (student_id, reference_month);

ALTER TABLE invoice DROP COLUMN subscription_id;

-- A listagem por aluno (/me/invoices) e o cruzamento de inadimplência.
CREATE INDEX idx_invoice_student ON invoice (student_id);
