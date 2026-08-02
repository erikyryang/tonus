-- SPEC-004: due_day deixou de ser o aniversário do último pagamento e passou a
-- ser o dia do mês em que o aluno se cadastrou.
--
-- As assinaturas que já existem carregam o valor da regra antiga, e nada as
-- reconciliaria sozinho: o campo não é mais editável e a baixa não o toca mais.
-- Sem este UPDATE o estúdio conviveria com duas regras ao mesmo tempo — a nova
-- para quem assinar de agora em diante, a antiga congelada para quem já
-- assinou —, e ninguém conseguiria explicar por que dois alunos cadastrados no
-- mesmo dia vencem em dias diferentes.
--
-- Esta migration lê `student`, que é de outro módulo. É correção pontual de
-- dado, não acoplamento de schema: não cria FK, view nem dependência
-- permanente, e em tempo de execução o billing continua sem enxergar o
-- registration — o dia do cadastro chega pela RegistrationFacade.
--
-- O `created_at` é gravado em UTC pelo Hibernate, num TIMESTAMP sem fuso. O dia
-- que interessa é o do fuso do estúdio (ver ClockConfig): sem a conversão, quem
-- se cadastrou às 21h30 de Brasília seria contado no dia seguinte.
UPDATE subscription s
SET due_day = EXTRACT(DAY FROM (st.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo'))::int,
    updated_at = now()
FROM student st
WHERE st.id = s.student_id
  AND s.due_day <> EXTRACT(DAY FROM (st.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo'))::int;
