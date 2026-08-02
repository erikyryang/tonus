-- A assinatura morre por último (SPEC-012).
--
-- Só aqui, depois de os planos existirem (V019), os dados terem migrado (V020)
-- e a fatura já apontar para o aluno (V021). Derrubá-la antes deixaria as duas
-- migrations anteriores sem a origem de onde leem.
DROP TABLE subscription;
