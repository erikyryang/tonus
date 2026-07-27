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
- dia_vencimento inexistente no mês (31 em fev) → último dia do mês.
- Baixa (ADMIN): fatura → PAGA + cria pagamento + publica PagamentoRegistrado
    + recalcula dia_vencimento da assinatura (aniversário do pagamento).
      Tudo na MESMA transação.
- Endpoints: CRUD /assinaturas, GET /faturas (filtros por status, paginado),
  POST /faturas/{id}/pagamento (ADMIN), GET /me/faturas (ALUNO).
- Transições de status de fatura são auditadas.

## Critérios de aceite
- Job rodando 2× no mesmo dia não duplica fatura nem eventos.
- Baixa em fatura já PAGA → 409.
- Pagamento dia 10 → próxima fatura vence dia 10 do mês seguinte.
- Datas testáveis via Clock injetado (nunca LocalDate.now()).

## Fora de escopo
PIX/gateway, multa/juros, parcelamento, reenvio periódico do alerta de atraso.