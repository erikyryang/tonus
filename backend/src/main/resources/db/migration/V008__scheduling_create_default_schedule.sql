-- Horário padrão do aluno: uma linha por aluno, seg-sex.
--
-- Sem teacher_id de propósito: o professor da aula é sempre o do vínculo atual
-- em student. Guardar aqui também criaria duas verdades, que divergem na
-- primeira troca de professor.
--
-- student_id sem FK, mesma decisão de users.student_id (ver docs/ddl.md): FK
-- entre módulos acopla no schema de um jeito que o ApplicationModules.verify()
-- não enxerga. A integridade é garantida em código, via RegistrationFacade.
--
-- time_of_day, e não "time", porque TIME é nome de tipo em SQL.
CREATE TABLE default_schedule (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    time_of_day TIME NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- "Um horário por aluno" é regra de negócio, não detalhe de modelagem:
    -- é o critério de aceite "segundo horário -> 409" expresso no banco.
    CONSTRAINT uk_default_schedule_student UNIQUE (student_id)
);
