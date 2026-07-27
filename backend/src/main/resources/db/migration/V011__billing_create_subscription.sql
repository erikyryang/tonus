-- Assinatura mensal de um aluno: valor e dia de vencimento.
--
-- student_id sem FK, mesma decisão de default_schedule.student_id (ver
-- docs/ddl.md): FK entre módulos acopla no schema de um jeito que o
-- ApplicationModules.verify() não enxerga. A integridade é garantida em
-- código, via RegistrationFacade.
CREATE TABLE subscription (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    monthly_amount NUMERIC(10, 2) NOT NULL,
    due_day INT NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- Um aluno tem no máximo uma assinatura: duas tornariam ambíguo qual
    -- delas o job diário deveria faturar.
    CONSTRAINT uk_subscription_student UNIQUE (student_id),
    CONSTRAINT ck_subscription_due_day CHECK (due_day BETWEEN 1 AND 31)
);
