-- Feriado vira comunicado (SPEC-003).
--
-- O conceito era estreito demais: o estúdio precisa avisar o aluno de coisas
-- que não cancelam aula (mudança de endereço, festa de fim de ano, obra na
-- rua), e a única ferramenta que existia para falar com todo mundo suprimia o
-- dia como efeito colateral. `suspends_classes` separa as duas coisas: o aviso
-- e a supressão.
--
-- Cada feriado existente vira comunicado que suspende — é exatamente o que ele
-- significava. A tabela antiga morre depois da cópia, não antes.
CREATE TABLE announcement (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    suspends_classes BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- Herdada de uk_holiday_date, e pelas mesmas duas razões: "o dia D está
    -- suspenso?" precisa de resposta única para a expansão da agenda, e a data
    -- é a chave de idempotência do aviso em notification_log (reference_key).
    -- Dois comunicados no mesmo dia fariam o segundo aviso ser engolido pela
    -- UNIQUE de lá, e o aluno não receberia.
    CONSTRAINT uk_announcement_date UNIQUE (date)
);

INSERT INTO announcement (date, description, suspends_classes, created_at, updated_at)
SELECT date, description, TRUE, created_at, updated_at FROM holiday;

DROP TABLE holiday;
