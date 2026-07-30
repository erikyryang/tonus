# SPEC-009 — Telas CRUD e /me (guarda-chuva)

## Objetivo
As telas restantes, todas repetição de padrão (tabela paginada +
dialog de formulário, ou leitura simples). Uma spec fina de propósito:
o detalhe normativo mora no frontend/CLAUDE.md; aqui só o que cada
tela tem de específico.

## Padrão comum (vale para todas as telas ADMIN abaixo)
- Tabela paginada; filtro ?active=true|false onde o back suportar
  (default: ativas); criação/edição em Dialog com react-hook-form +
  zod espelhando os limites do back (telefone E.164, nome ≤ 255).
- Desativar/ativar com confirmação; NUNCA delete onde o back não tem.
- Mutação → invalidate da listagem → toast. Erros: Problem Details.

## Telas ADMIN
- /students: colunas nome, telefone, professor, situação. Form: nome,
  telefone, professor (select só de ATIVOS — regra do back refletida).
  Desativar avisa: "o acesso do aluno será revogado". Ações de acesso
  na linha: "Criar acesso" (dialog login+senha → POST /users com
  studentId) e "Reativar acesso" (PATCH /users/{id}/activate) — a
  reativação do login é manual por design (revogar é automático,
  conceder é manual). Se o back não expuser consulta usuário-por-
  vínculo para saber qual ação mostrar, acrescentar (toque documentado).
- /teachers: idem sem o select de professor. Desativação avisa que
  alunos dele precisarão de remanejamento.
- /default-schedules: aluno (select de ativos) + hora (HH:mm).
  Excluir avisa: "todas as exceções deste aluno serão apagadas junto"
  (decisão da SPEC-003 refletida na UI).
- /holidays: data + descrição; criar avisa: "todos os alunos ativos
  serão notificados por WhatsApp". Excluir com confirmação.
- /subscriptions: aluno (select de ativos), valor mensal (R$),
  dia de vencimento (1–31, hint do ajuste para fim de mês).

## Telas /me (mobile-first, read-only)
- /me/schedule (STUDENT): o horário padrão + as próximas exceções e
  feriados que o afetam; estado "sem horário cadastrado".
- /me/invoices (STUDENT): lista com competência, valor, vencimento,
  status; OVERDUE em destaque.
- /me/grid (TEACHER): o dia agrupado por hora com os alunos; mesma
  navegação de data da SPEC-007, sem nenhuma ação.

## Critérios de aceite (por amostragem — o padrão se testa uma vez)
- /students cobre o ciclo completo: criar (validação E.164 no zod),
  editar, desativar com aviso, filtro ?active, criar acesso.
- Demais CRUDs: um teste de renderização + um de criação cada.
- /me/schedule e /me/grid: renderização com MSW + guard do papel.
- Selects de aluno/professor nunca oferecem inativos.

## Toques no back que esta spec exigiu (decididos na implementação)
- `GET /users?studentIds=1,2,3` → lista dos usuários vinculados àqueles
  alunos, e `UserResponse` passa a expor `active`. É a consulta
  usuário-por-vínculo prevista acima: sem ela o front não tem o id do
  usuário, e "Reativar acesso" seria impossível. Em lote e não por aluno
  porque a tela pergunta por uma página inteira de cada vez; não paginado
  porque o tamanho já está limitado pela pergunta.
- `GET /me/schedule` passa a devolver o dia do aluno **com** a descrição do
  feriado e os cancelamentos dele (`SchedulingService.dayOfStudent`, que
  filtra `dayOf` em vez de reexpandir). Sem isso a tela do aluno não teria
  como mostrar "os feriados que o afetam" — e "não tem aula hoje" ficaria
  idêntico a "sua aula de hoje foi cancelada".

## Decisões de UI registradas
- `/me/schedule` mostra uma janela de 7 dias a partir de hoje, uma chamada
  por dia (o endpoint responde por dia). O "horário padrão" é derivado das
  aulas de origem TEMPLATE da janela — o back não expõe campo próprio para
  ele no contrato /me. O aviso de "sem horário cadastrado" olha só para
  aulas e exceções: feriado não é evidência de horário.
- Filtro de situação nas listagens vive na URL: ausência de parâmetro é
  "ativos" (o default), `?active=false` é inativos e `?active=all` é todos.
- Navegação: `/admin/default-schedules` ("Horários") e
  `/admin/subscriptions` ("Assinaturas") entram no menu do ADMIN;
  `/me/invoices` entra na barra do aluno (o professor, com uma tela só,
  não ganha navegação).
- O recuo da página que deixou de existir (nascido na SPEC-008) virou o
  hook `usePageClamp` e vale para todas as listagens: desativar o único
  registro da última página não pode deixar a tela dizendo "nenhum aluno
  ativo" com a página 1 cheia.
- 409 na criação de horário, feriado e assinatura é estado legítimo
  ("alguém chegou primeiro"), não erro genérico: a tela mostra a mensagem
  do back e refaz a listagem, que já estava desatualizada. No 409 de login
  já em uso o botão continua armado — escolher outro login é a saída.

## Fora de escopo
Busca textual, ordenação custom, operações em massa, exportação,
troca de senha pelo próprio usuário, PWA (fase própria, depois).