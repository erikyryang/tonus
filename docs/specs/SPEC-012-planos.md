# SPEC-012 — Planos (substitui assinatura)

> **PROPOSTA — não implementada.** Escrita antes de qualquer código, como a
> mudança 5 exigia. Nada nesta spec vale como decisão até ser aprovada; o que
> está no ar continua sendo a SPEC-004 (assinatura única por aluno).

## Por que uma spec própria

As mudanças 1 a 4 couberam nas specs existentes porque cada uma mexia num
lugar. Esta muda o modelo de cobrança inteiro:

- três tabelas novas (`plan`, `plan_enrollment`, `billing_settings`);
- a chave da fatura deixa de ser `(subscription, competência)` e passa a ser
  `(aluno, competência)` — é a definição de "o que é uma fatura" mudando;
- o valor deixa de ser um campo copiado e passa a ser **calculado** (soma dos
  planos ativos, com desconto);
- `subscription` morre, e com ela metade dos testes de `billing`;
- migração de dados em quatro passos, com ordem que importa;
- três telas do ADMIN.

Nenhuma dessas partes é opcional para as outras funcionarem, então não dá para
fatiar em PRs independentes — o que dá é fatiar em **etapas dentro do PR**, com
o banco consistente ao fim de cada uma (ver "Migração", abaixo).

## Objetivo

Um aluno pode assinar N planos. A fatura do mês é **uma por aluno**, com o
valor somado dos planos ativos e um desconto configurável quando há mais de um.

## Regras

### Cadastro
- `plan`: `name`, `monthly_price`, `active`. CRUD do ADMIN em `/plans`.
  Não existe DELETE — plano desativado some das listagens e das novas
  matrículas, mas continua explicando faturas antigas.
- `plan_enrollment`: `student_id`, `plan_id`, `active`.
  `UNIQUE(student_id, plan_id)` — o mesmo plano não se assina duas vezes; para
  cobrar em dobro, o caminho é um plano com preço diferente.
- `billing_settings`: **linha única**, com `multi_plan_discount_percent`
  (0–100). Editável pelo ADMIN em `/admin/settings`. A unicidade é constraint
  (`id` fixo com `CHECK (id = 1)`), não convenção: duas linhas tornariam
  ambíguo qual desconto vale, e a resposta viraria "a que o `LIMIT 1` pegou".

### Faturamento
- Uma fatura por aluno por competência: `uk_invoice_student_period`
  `(student_id, reference_month)`, no lugar de
  `uk_invoice_subscription_period`. A idempotência do job continua sendo
  constraint, só muda a chave.
- Valor = soma de `monthly_price` dos planos **ativos** do aluno cujo plano
  também está ativo. Se o aluno tem 2 ou mais, aplica-se
  `multi_plan_discount_percent` sobre a soma.
- Arredondamento: `HALF_UP`, duas casas, uma vez só, no total — nunca por
  plano. Descontar plano a plano e somar depois produz centavos diferentes da
  mesma conta feita na ordem inversa, e o aluno vê a diferença no boleto.
- Aluno sem plano ativo não gera fatura (hoje: aluno sem assinatura ativa).
- `due_day` **não vira coluna**: o job resolve o vencimento pelo dia do
  cadastro do aluno (mudança 3, SPEC-004). Consequência que o desenho precisa
  resolver: o job passa a precisar da data de cadastro de vários alunos de uma
  vez, e uma consulta por aluno seria N+1 no job diário. Proposta:
  `RegistrationFacade.findRegistrationDates(Collection<Long>)`, em lote, ao
  lado do `findRegistrationDate` que já existe.

### Eventos — contrato preservado
`InvoiceDueSoon`, `InvoiceOverdue` e `PaymentRegistered` **não mudam de
payload**: os três já carregam `studentId`, e nenhum deles menciona assinatura.
`notification` não é tocada por esta spec, e é o teste dela que prova isso —
se algum evento mudar, `AnnouncementFanOutIT` e companhia quebram, e é sinal de
que o desenho escorregou.

### Endpoints
- `/plans` — POST, GET (paginado, filtro `?active=`), PUT, PATCH
  `/{id}/activate` e `/{id}/deactivate` (ADMIN).
- `/students/{id}/plans` — hmm: **não**. O vínculo é de billing, e um caminho
  sob `/students` sugeriria que ele é do registration. Proposta:
  `/plan-enrollments` (POST, GET com filtro `?studentId=`, PATCH de ativação),
  e a tela do aluno consome esse filtro.
- `/billing-settings` — GET e PUT (ADMIN), a linha única.
- `/invoices` e `/me/invoices` continuam iguais no contrato de saída, exceto
  por `subscriptionId`, que deixa de existir no DTO.

## Migração (a parte perigosa)

Quatro migrations, nesta ordem. `subscription` morre por último.

| Versão | O que faz |
|---|---|
| V019 | cria `plan`, `plan_enrollment`, `billing_settings` (com a linha única, desconto 0) |
| V020 | um `plan` por **valor distinto** de assinatura existente, nome "Plano R$ X"; cada aluno matriculado no plano do seu valor, com `active` igual ao da assinatura |
| V021 | `invoice.student_id` — cria, preenche via `subscription`, torna NOT NULL, troca a UNIQUE e derruba `subscription_id` |
| V022 | `DROP TABLE subscription` |

Pontos que exigem decisão consciente:

- **Nome do plano gerado** ("Plano R$ 350,00") é feio de propósito: é rótulo de
  migração, e o ADMIN vai renomeá-lo na tela. Inventar nome bonito ("Mensal
  Padrão") seria fingir que a informação existia.
- **Valores iguais viram o mesmo plano.** Dois alunos que pagavam 350 passam a
  compartilhar o plano de 350; mudar o preço dele depois muda para os dois. É a
  consequência de usar valor como identidade — e é o que "um plano por valor
  distinto" significa. A alternativa (um plano por aluno) preservaria
  independência e criaria N planos idênticos, que é exatamente o que a tela de
  planos existe para evitar.
- **Faturas antigas mantêm o valor congelado.** `invoice.amount` continua sendo
  cópia do que foi cobrado; mudar o preço do plano não reescreve histórico.

## O que fica de fora (e por quê)

- **`invoice_item`** (a fatura guardando quais planos a compuseram). É a
  próxima pergunta óbvia — "por que essa fatura deu 620?" — e a resposta hoje
  seria "some os planos ativos dele hoje", que mente assim que alguém trocar de
  plano. Fica de fora **desta** spec por tamanho, e entra registrado aqui como
  dívida conhecida, não como esquecimento.
- Proporcional por dias (aluno que assina no dia 20), cobrança retroativa,
  plano com duração/vigência, pausa de matrícula.

## Critérios de aceite

- Aluno com 1 plano de 350 → fatura de 350,00.
- Aluno com 2 planos (350 + 270) e desconto de 10% → fatura de 558,00.
- Aluno com 2 planos e desconto 0 → 620,00 (o desconto é configuração, não
  regra embutida).
- Plano desativado não entra na soma, mesmo com matrícula ativa.
- Matrícula desativada não entra na soma, mesmo com plano ativo.
- Job rodando 2× no mesmo dia não duplica a fatura do aluno (constraint).
- Aluno sem plano ativo não gera fatura.
- Migração: aluno com assinatura de 350 termina com um plano de 350 ativo, e a
  fatura de competência anterior continua apontando para ele.
- Os três eventos de billing mantêm payload idêntico (teste de contrato).

## Decisões tomadas antes de implementar

1. **Desativar plano com matrícula ativa é permitido**, e a matrícula
   simplesmente deixa de ser cobrada. Travar obrigaria a desmatricular aluno por
   aluno só para encerrar um plano, que é o caso comum de fim de ano. O que
   compensa o risco é a tela: antes de confirmar, ela diz **quantos alunos**
   perdem a cobrança daquele plano — desativar por engano tem de ser difícil de
   não perceber, não impossível de fazer.
2. **Desconto é um percentual único a partir de 2 planos.** Um campo em
   `billing_settings`, mesmo percentual para 2 ou 5 planos. Escalonar por
   quantidade exigiria tabela de faixas e uma tela de configuração que o estúdio
   não pediu; quando pedir, o que muda é a origem do percentual, não o cálculo.
3. **`plan_enrollment.active` em vez de apagar a linha** — igual ao resto do
   sistema, onde nada some. Custa uma coluna e preserva "esse aluno já teve esse
   plano", que é pergunta de cobrança.
4. **Tela `/admin/settings`** para o desconto, e não um campo dentro de
   `/admin/plans`: desconto não é atributo de plano nenhum, é do estúdio.
