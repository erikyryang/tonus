# SPEC-008 — Faturas e renovação (ADMIN)

## Objetivo
Lista de faturas com filtro por status e o fluxo de registrar o pagamento —
a ação de maior consequência da UI: dispara recibo real por WhatsApp.

> **Mudança 8 renomeou a ação para "Renovar" na UI.** Endpoint e semântica de
> backend não mudam — continua `POST /invoices/{id}/payment`, e fatura paga
> continua sendo ciclo renovado. Só o texto deixou de falar de dívida
> ("dar baixa") para falar de renovação. Onde este documento diz "baixa",
> leia "renovação" — é o mesmo fluxo.

A baixa deixou de mexer no vencimento (SPEC-004, regra substituída), e a
fatura deixou de ser de uma assinatura para ser do aluno (SPEC-012). O
DTO perdeu `subscriptionId`; o resto do contrato desta tela não mudou.

## Regras
- Rota /invoices (shell ADMIN). Fonte: GET /invoices paginado,
  filtro ?status=OPEN|PAID|OVERDUE. Query key
  ['billing','invoices',{status,page}].
- Tabela: aluno, competência (MM/yyyy), valor (R$), vencimento
  (dd/MM/yyyy), status como badge — OVERDUE visualmente distinto
  (é o que o admin caça nessa tela). Filtro default: todas;
  atalho visível "Atrasadas".
- Renovar (rótulo da baixa, mudança 8): ação apenas em OPEN/OVERDUE →
  dialog de confirmação com data do pagamento (default hoje, editável,
  zod: não futura) e o aviso do efeito real: "O ciclo do aluno é
  renovado e ele receberá a confirmação por WhatsApp." O vencimento não
  entra na frase — a baixa não o move (SPEC-004), e prometer isso seria
  mentir. Confirmou → POST /invoices/{id}/payment {paymentDate} →
  invalidar ['billing','invoices'] → toast ("Renovação registrada...").
- 409 (fatura já paga — corrida com outro admin ou duplo clique):
  mensagem do Problem Details no dialog + refetch da lista. Não é
  erro genérico: é estado legítimo que a UI explica.
- Fatura PAGA exibe a data do pagamento (se o DTO da listagem não a
  carregar, acrescentar no back — toque documentado).
- Botão de renovar desabilitado durante a mutação (dupla proteção com
  o 409; o idempotente de verdade é o uk_payment_invoice do banco).

## Toques no backend (mesmo PR)
A tela não fecha só com frontend. Cada mudança tem teste de integração
(Testcontainers):

- `POST /invoices/{id}/payment` passa a exigir corpo `{paymentDate}`.
  Altera o contrato de SPEC-004, que não tinha corpo e usava o Clock.
  Obrigatória, e não "hoje por omissão": este endpoint dispara recibo
  por WhatsApp e move o vencimento da assinatura, e nenhuma das duas
  tem desfazer — um corpo esquecido tem de virar 400, não um pagamento
  numa data que ninguém escolheu. Corpo ausente ou `paymentDate` nulo
  → 400.
- Data futura → 400 (`FuturePaymentDateException`). A recusa não usa
  `@PastOrPresent`: o Bean Validation compara com o relógio do sistema,
  e "hoje" neste projeto só vem do Clock injetado. Quem valida é o
  `BillingService` — o Clock segue sendo a autoridade do "hoje", agora
  como critério de recusa em vez de valor padrão.
- `InvoiceResponse` ganha `studentName` e `paymentDate` (nulo enquanto
  não paga). Resolver os dois no cliente custaria uma chamada por linha
  da listagem; quem sabe juntar isso é o back.
- A listagem resolve assinatura, nome e pagamento **em lote**: 5
  consultas por página, independente do tamanho (eram 20 para 6 faturas
  na versão linha a linha). Exigiu `RegistrationFacade.findStudentNames`
  — `findStudentContact` seria uma consulta por linha e entregaria
  telefone a quem só precisa do nome. `payment` só é consultada para
  faturas PAGAS: as demais não têm linha lá por construção, e o filtro
  "Atrasadas" é o uso mais frequente da tela.
  Fixado por `InvoiceListingQueryCountIT`, que assere um teto de
  consultas — sem ele, voltar ao N+1 não quebraria nenhuma asserção de
  comportamento.

## Critérios de aceite (Vitest + RTL + MSW)
- Filtro por status altera a query string enviada (asserção no MSW).
- Fluxo de renovar completo: dialog → request com paymentDate →
  invalidação → badge PAID na lista atualizada.
- 409 exibe a mensagem do back e a lista é refeita.
- Data de pagamento futura é barrada pelo zod antes de qualquer
  request.
- OVERDUE renderiza o destaque visual.

## Bordas fechadas na finalização
- `studentName` pode vir nulo (subscription.student_id não tem FK), e a
  tela cai para `Aluno #{id}` em vez de célula em branco — numa linha
  sobre dinheiro, em branco é pior do que um identificador feio.
- Vazio fala do filtro ativo: "Nenhuma fatura atrasada" sob o atalho,
  não "Nenhuma fatura", que leria como "não existe fatura nenhuma".
- Página que deixa de existir recua sozinha (`replace`, sem entrada no
  histórico). Acontece de verdade: renovar o único item da última
  página sob "Atrasadas" tira a fatura do filtro e a página some. Cobre
  também `?page=` fora do intervalo digitado na URL.
- Depois do 409 o botão de confirmar fica desabilitado: o conflito não é
  transitório e repetir só produziria o mesmo 409 — a saída é Voltar.
- O toast diz "será avisado", não "recebeu": a entrega do WhatsApp é
  assíncrona (evento + reentrega at-least-once) e no instante da
  resposta HTTP nada foi entregue.
- Tabela com nome acessível (`<caption>`).

## Fora de escopo
CRUD de assinaturas (SPEC-009), estorno/edição de pagamento (não
existe no back — pagamento é imutável), multa/juros, exportação,
gráficos/relatórios.