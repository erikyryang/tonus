-- Planos, matrículas e a configuração de cobrança do estúdio (SPEC-012).
--
-- Aditiva de propósito: nada aqui derruba `subscription` nem mexe em `invoice`.
-- Enquanto o código não existe, estas tabelas ficam vazias e inertes — e é isso
-- que permite a migração acontecer em etapas com o banco consistente no fim de
-- cada uma. A assinatura morre na V022, depois de tudo.
CREATE TABLE plan (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    monthly_price NUMERIC(10, 2) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_plan_monthly_price CHECK (monthly_price > 0)
);

-- O vínculo aluno -> plano. student_id sem FK, mesma decisão de
-- subscription.student_id (ver docs/ddl.md): FK entre módulos acopla no schema
-- de um jeito que o ApplicationModules.verify() não enxerga. plan_id com FK
-- normal, porque plano e matrícula são do mesmo módulo.
CREATE TABLE plan_enrollment (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL REFERENCES plan (id),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- O mesmo plano não se assina duas vezes: a segunda linha tornaria ambíguo
    -- quanto cobrar. Para cobrar em dobro, o caminho é um plano de outro preço.
    CONSTRAINT uk_plan_enrollment_student_plan UNIQUE (student_id, plan_id)
);

-- O job diário monta a fatura por aluno; a tela do aluno pergunta os planos dele.
CREATE INDEX idx_plan_enrollment_student ON plan_enrollment (student_id);

-- Configuração de cobrança do estúdio. Linha única garantida por constraint, e
-- não por convenção: duas linhas tornariam ambíguo qual desconto vale, e a
-- resposta viraria "a que o LIMIT 1 pegou".
CREATE TABLE billing_settings (
    id BIGINT PRIMARY KEY,
    multi_plan_discount_percent NUMERIC(5, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_billing_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_billing_settings_discount CHECK (multi_plan_discount_percent BETWEEN 0 AND 100)
);

-- Nasce sem desconto: o estúdio decide o valor na tela, e um default inventado
-- aqui daria desconto a quem nunca pediu.
INSERT INTO billing_settings (id, multi_plan_discount_percent, created_at, updated_at)
VALUES (1, 0, now(), now());
