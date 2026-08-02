-- As assinaturas existentes viram planos e matrículas (SPEC-012).
--
-- Um plano por VALOR DISTINTO, não um por aluno. A consequência é assumida e
-- está na spec: dois alunos que pagavam 350 passam a compartilhar o plano de
-- 350, e mudar o preço dele depois muda para os dois. Um plano por aluno
-- preservaria independência ao custo de N planos idênticos — exatamente o que
-- a tela de planos existe para evitar.
--
-- O nome é feio de propósito ("Plano R$ 350,00"): é rótulo de migração, e o
-- ADMIN renomeia na tela. Inventar "Mensal Padrão" seria fingir que essa
-- informação existia em algum lugar.
--
-- A formatação é feita à mão, sem to_char com G/D: aqueles dependem do
-- lc_numeric do servidor, e o nome do plano mudaria conforme a configuração do
-- banco em que a migration roda.
INSERT INTO plan (name, monthly_price, active, created_at, updated_at)
SELECT DISTINCT
       'Plano R$ ' || replace(to_char(monthly_amount, 'FM9999990.00'), '.', ','),
       monthly_amount,
       TRUE,
       now(),
       now()
FROM subscription;

-- Cada aluno na matrícula do seu valor, preservando se estava ativa. O
-- created_at vem da assinatura: a matrícula não nasceu agora, ela é a mesma
-- relação com outro nome.
--
-- O join por VALOR só é inequívoco porque `plan` está vazia neste ponto: a V019
-- acabou de criá-la e o Flyway aplica as duas no mesmo passe, então não existe
-- plano anterior com o mesmo preço para o join casar duas vezes. Separar as
-- duas migrations no tempo — subir uma versão só com a V019 — quebraria essa
-- garantia.
INSERT INTO plan_enrollment (student_id, plan_id, active, created_at, updated_at)
SELECT s.student_id, p.id, s.active, s.created_at, s.updated_at
FROM subscription s
JOIN plan p ON p.monthly_price = s.monthly_amount;
