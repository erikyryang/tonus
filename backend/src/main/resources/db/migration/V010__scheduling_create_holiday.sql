-- Feriado é global: uma linha vale para o estúdio inteiro. Não existe feriado
-- de professor nem de turma — se existisse, "há feriado em D?" deixaria de ter
-- resposta única e a expansão da agenda teria de decidir por quem.
CREATE TABLE holiday (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_holiday_date UNIQUE (date)
);
