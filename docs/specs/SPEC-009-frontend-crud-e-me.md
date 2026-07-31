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
  porque o tamanho já está limitado pela pergunta — com teto de 200 ids
  por chamada, que é o que transforma esse limite em garantia em vez de
  confiança no cliente (a lista vira cláusula `IN`, um parâmetro por id).
  Acima disso é 400, não 500.
- `GET /me/schedule` passa a devolver o dia do aluno **com** a descrição do
  feriado e os cancelamentos dele (`SchedulingService.dayOfStudent`, que
  filtra `dayOf` em vez de reexpandir). Sem isso a tela do aluno não teria
  como mostrar "os feriados que o afetam" — e "não tem aula hoje" ficaria
  idêntico a "sua aula de hoje foi cancelada".
- `GET /me/schedule/week?from=&days=` → a janela do aluno numa chamada só,
  com o mesmo contrato do dia avulso repetido por data
  (`SchedulingService.weekOfStudent`). A tela mostra sete dias, e pedir dia
  a dia eram sete requisições — cada uma carregando todos os alunos ativos e
  todos os horários padrão do estúdio para devolver a linha de um aluno.
  Agora as consultas são pelo intervalo: 6 no total para 14 dias, e o número
  não cresce com o tamanho do estúdio (`MyScheduleWeekQueryCountIT` fixa o
  teto). Teto de 31 dias na janela, porque o custo é proporcional ao pedido.
  `RegistrationFacade.findStudentSummary` nasceu disso: perguntar por um
  aluno em vez de listar os ativos e descartar todos menos um.

## Decisões de UI registradas
- `/me/schedule` mostra uma janela de 7 dias a partir de hoje, numa chamada
  só (`GET /me/schedule/week`). O "horário padrão" é derivado das aulas de
  origem TEMPLATE da janela — o back não expõe campo próprio para ele no
  contrato /me. O aviso de "sem horário cadastrado" olha só para aulas e
  exceções: feriado não é evidência de horário. Com uma resposta só, ou a
  janela chegou inteira ou a tela mostra o erro e não conclui nada — não
  existe mais meia semana para conciliar.
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

- A linha de aluno só oferece ação de acesso depois que
  `GET /users?studentIds=` responde: a tabela chega antes (são duas
  chamadas), e "ainda não sei" não pode virar "não tem login". O back não
  impede um segundo usuário para o mesmo aluno — só o login é UNIQUE —,
  então um clique nesse intervalo criaria conta duplicada.
- Desativar aluno não refaz a consulta de acesso: a revogação é por evento
  e chega depois da resposta HTTP, então o refetch traria de volta o
  `active: true` que está prestes a deixar de valer. A revogação é certa,
  só não é imediata, e por isso é escrita direto no cache.
- Campo de dinheiro: sem vírgula, ponto separando grupos de exatamente três
  dígitos é milhar ("1.500" = mil e quinhentos). Lido como decimal viraria
  uma assinatura de R$ 1,50 que passa em toda validação. O primeiro grupo não
  começa em zero — "0.500" é meio real, e lê-lo como quinhentos seria o erro
  inverso, mil vezes mais caro porque cobra a mais.
- Falha na consulta de acesso se explica na tela, com "tentar de novo": a
  ação continua escondida (o risco da conta duplicada é o mesmo do
  carregamento), mas some com motivo em vez de calada.
- O aviso de confirmação carrega número de série, usado como `key` do nó:
  anunciar em leitor de tela depende de mutação dentro da live region, e o
  mesmo texto no mesmo nó não muda nada no DOM — o segundo aviso passaria
  em silêncio justo para quem só tem ele.
- Datas dos testes de agenda são ancoradas na próxima segunda-feira, nunca
  literais: a validação de data passada no formulário de remarcação faz
  literal apodrecer no calendário (mesma lição do Clock fixo no back).

- Caminho novo em `/me` entra na lista do `SecurityConfig` por extenso, sem
  `/**`: `/me/schedule` não cobre `/me/schedule/week`, e sem a linha própria
  o endpoint cairia no `anyRequest().authenticated()` — aberto a qualquer
  papel. Foi um teste pedindo 403 e recebendo 404 que mostrou isso.

## Fora de escopo
Busca textual, ordenação custom, operações em massa, exportação,
troca de senha pelo próprio usuário, PWA (fase própria, depois).
