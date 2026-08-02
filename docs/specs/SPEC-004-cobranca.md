# SPEC-004 — Cobrança

## Objetivo
Ciclo assinatura → fatura → pagamento, com job diário e eventos.

## Regras
- `assinatura`: aluno_id (FK, UNIQUE), valor_mensal, dia_vencimento, ativa.
- `fatura`: assinatura_id, competencia, valor, vencimento,
  status ABERTA/PAGA/ATRASADA. UNIQUE(assinatura_id, competencia) —
  idempotência do job virando constraint.
- `pagamento`: fatura_id (FK, UNIQUE), data_pagamento, registrado_por.
- Job diário (@Scheduled + ShedLock):
    1. Gera fatura da competência para assinaturas ativas com vencimento hoje
       → publica FaturaVencendo.
    2. Faturas ABERTAS com vencimento < hoje − N dias → ATRASADA
       → publica FaturaAtrasada (só na transição, uma vez).
       N = `cobranca.dias-para-atraso` (default 1).
- dia_vencimento = dia do mês em que o aluno se cadastrou (`student.created_at`),
  copiado para a assinatura na criação. Não vem do cliente e não é editável.
- dia_vencimento inexistente no mês (31 em fev) → último dia do mês. O ajuste é
  por competência, na resolução; o dia guardado continua sendo o do cadastro.
- Baixa (ADMIN): fatura → PAGA + cria pagamento + publica PagamentoRegistrado.
      Tudo na MESMA transação. **Não** mexe no dia_vencimento.
- Endpoints: CRUD /assinaturas, GET /faturas (filtros por status, paginado),
  POST /faturas/{id}/pagamento (ADMIN), GET /me/faturas (ALUNO).
  A baixa passou a exigir corpo `{paymentDate}` em SPEC-008 — a data é do
  ADMIN, não do Clock. O Clock continua decidindo o que é "futuro".
- Transições de status de fatura são auditadas.

## Critérios de aceite
- Job rodando 2× no mesmo dia não duplica fatura nem eventos.
- Baixa em fatura já PAGA → 409.
- Aluno cadastrado no dia 5 → assinatura nasce vencendo dia 5.
- Baixa em qualquer data deixa o dia_vencimento onde estava.
- Cadastro dia 31 → dia_vencimento 31, e a fatura de fevereiro vence dia 28.
- Datas testáveis via Clock injetado (nunca LocalDate.now()).

## Decisões

### Vencimento é o dia do cadastro (substitui "aniversário do pagamento")

**Regra anterior:** a baixa recalculava `due_day` para o dia do pagamento.
**Regra atual:** `due_day` é o dia do mês de `student.created_at`, fixo.

O aniversário do pagamento fazia o vencimento andar sozinho pelo calendário.
Quem pagava com três dias de atraso passava a vencer três dias depois no mês
seguinte, e de novo no outro — o atraso virava o novo normal, e a mensalidade
migrava para o fim do mês sem ninguém ter decidido isso. Pior: ninguém
conseguia responder "que dia vence a minha mensalidade?" sem consultar a
última baixa, porque a resposta mudava a cada pagamento. Um vencimento serve
para ser previsível; um que se move não é vencimento, é consequência.

O dia do cadastro é a única data que já existe, não muda e é a mesma para o
ADMIN e para o aluno. Não precisou de coluna nova nem de escolha na tela.

Consequências assumidas:
- **Não é editável.** Um campo no formulário devolveria o problema: a
  assinatura passaria a discordar do aluno a que pertence. Se um dia for
  preciso mudar o vencimento de alguém, a mudança é no cadastro, e vai
  precisar de decisão própria.
- **`due_day` continua sendo coluna da assinatura**, cópia do dia do cadastro,
  em vez de ser lido do `registration` a cada fatura. A assinatura é quem
  responde "quando vence", e o job diário não pode depender de outro módulo
  para resolver a data de cada fatura que gera. É cópia de valor que não
  retroage.
- `RegistrationFacade.findRegistrationDate` nasceu disso. Devolve `LocalDate`
  no fuso do estúdio, não `Instant`: quem se cadastrou às 21h30 de Brasília se
  cadastrou no dia 5, e em UTC seria dia 6 — a mensalidade venceria um dia
  depois para todo aluno cadastrado no fim da noite.
- O que foi removido: `Subscription.realignDueDayTo`, o `dueDay` de
  `SubscriptionRequest` e do formulário, o aviso de recálculo no diálogo de
  baixa, e os testes da regra antiga (`PaymentIT` agora prova o contrário —
  que a baixa **não** move o vencimento).
- **As assinaturas que já existiam foram realinhadas** (migration V016). Sem
  isso o estúdio conviveria com duas regras ao mesmo tempo — a nova para quem
  assinasse dali em diante, a antiga congelada para quem já tinha assinado —, e
  dois alunos cadastrados no mesmo dia venceriam em dias diferentes sem
  explicação. A migration lê `student`, o que nenhuma outra faz: é correção
  pontual de dado, não acoplamento de schema (não cria FK nem view, e em tempo
  de execução o billing continua falando com o registration só pela fachada).
  O teste executa o próprio arquivo da migration, e não uma cópia do SQL — cópia
  fica verde enquanto o arquivo diverge.
- **`student.created_at` não muda**: é `updatable = false` no `BaseEntity` e não
  existe endpoint que o altere. Por isso o vencimento copiado não precisa de
  reconciliação contínua; a V016 é correção de uma vez, não rotina.
- O fuso do estúdio decide o dia, não UTC — quem se cadastra às 21h30 de
  Brasília se cadastra no dia 5, e em UTC já é dia 6. Vale nos dois caminhos, e
  os dois têm teste que falha se alguém trocar por UTC: `DueDayTimeZoneIT` (a
  regra viva, com relógio de 21h30) e `DueDayBackfillIT` (a migration).

## Fora de escopo
PIX/gateway, multa/juros, parcelamento, reenvio periódico do alerta de atraso.