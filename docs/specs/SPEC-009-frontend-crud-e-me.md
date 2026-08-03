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
- /students: colunas nome, telefone, professor, horário, situação e cobrança
  ("Atrasado" / "Em dia", de `GET /invoices/overdue-student-ids`). Form:
  nome, telefone, professor (select só de ATIVOS — regra do back refletida).
  Desativar avisa: "o acesso do aluno será revogado". Ações de acesso
  na linha: "Criar acesso" (dialog login+senha → POST /users com
  studentId) e "Reativar acesso" (PATCH /users/{id}/activate) — a
  reativação do login é manual por design (revogar é automático,
  conceder é manual). Se o back não expuser consulta usuário-por-
  vínculo para saber qual ação mostrar, acrescentar (toque documentado).
  A coluna "Horário" cruza `GET /default-schedules/by-student` (mapa
  aluno → hora, HH:mm) com a mesma tabela: aluno sem horário mostra "—".
  Mesmo padrão da coluna de cobrança — `registration` não passa a
  consultar `scheduling`, quem cruza as duas listas é o cliente, e a coluna
  fica vazia (não "—") enquanto a consulta não responde, para não confundir
  "ainda não sei" com "sem horário cadastrado".
- /teachers: idem sem o select de professor. Desativação avisa que
  alunos dele precisarão de remanejamento.
- /default-schedules: aluno (select de ativos) + hora (HH:mm).
  Excluir avisa: "todas as exceções deste aluno serão apagadas junto"
  (decisão da SPEC-003 refletida na UI).
- /announcements: data + descrição + caixa "suspende as aulas do dia"
  (desmarcada por padrão — suprimir o dia é o efeito caro, e efeito caro não
  acontece por omissão). Criar avisa: "todos os alunos ativos serão
  notificados por WhatsApp", com o texto mudando conforme a caixa, porque as
  duas mensagens são opostas. A lista mostra o efeito numa coluna própria
  ("Aulas suspensas" / "Aulas normais"): sem ela, dois comunicados pareceriam
  a mesma coisa e um deles apaga o dia. Excluir com confirmação, e só promete
  a volta das aulas quando elas estavam suspensas.
- /plans: nome e valor mensal (R$). A tabela mostra quantos alunos estão
  matriculados, e é esse número que a confirmação de desativar repete — a
  SPEC-012 permite desativar plano com matrícula ativa, e o preço da permissão
  é dizer o tamanho do estrago antes. Sem excluir: plano desativado continua
  explicando as faturas emitidas com ele.
- /settings: o desconto de múltiplos planos, um campo só. Tela própria porque
  desconto não é atributo de plano nenhum.
- Os planos de um aluno saem da linha dele em /students ("Planos"), num diálogo
  de caixas de seleção. Sem botão de salvar: cada caixa é uma decisão isolada,
  e o back tem endpoint para cada uma — um "salvar" no fim mandaria N chamadas
  do mesmo jeito, com a diferença de que uma falha no meio deixaria a tela
  mentindo sobre o que gravou.
  **Encerrar é ação à parte, por linha, com confirmação (mudança 8).**
  Diferente de matricular, encerrar tem efeito sobre cobrança futura, e a
  caixa sozinha nunca perguntava "tem certeza?". Plano ativo mostra a caixa
  marcada e travada — quem desliga é o botão "Encerrar" ao lado, nunca o
  clique na caixa. Confirmação: "Tem certeza que quer encerrar o plano X de
  Y?" + o efeito real, "o aluno deixa de ser cobrado por este plano; as
  faturas já emitidas continuam" — mesmo `PATCH /plan-enrollments/{id}/
  deactivate` de sempre (SPEC-012), sem endpoint novo. Não desativa o aluno
  nem revoga acesso: são coisas separadas, e um aluno sem plano nenhum
  continua aluno ativo. Nenhum WhatsApp: encerrar é decisão administrativa,
  quem comunica é o admin. Confirmar volta para a lista de planos (não fecha
  o diálogo inteiro), com a caixa destravada — a matrícula não é apagada, só
  desativada, e é o que permite rematricular sem esbarrar na UNIQUE.

## Telas /me (mobile-first, read-only)
- /me/schedule (STUDENT): o horário padrão + as próximas exceções e
  comunicados que o afetam; estado "sem horário cadastrado". O comunicado que
  suspende substitui a linha do dia ("Sem aula — <motivo>"); o que não suspende
  aparece **ao lado** da hora, porque o dia continua tendo aula e o aluno volta
  aqui para conferir o que recebeu por WhatsApp.
- /me/invoices (STUDENT): lista com competência, valor, vencimento,
  status; OVERDUE em destaque.
- /me/grid (TEACHER): o dia agrupado por hora com os alunos; mesma
  navegação de data da SPEC-007, sem nenhuma ação sobre a aula. A linha do
  aluno mostra o telefone, com link `wa.me` que abre a conversa — o
  professor atende aquele aluno (decisão na SPEC-003).

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
- `GET /invoices/overdue-student-ids` (ADMIN) → os ids dos alunos com fatura
  ATRASADA. É o que a coluna "Cobrança" da `/students` cruza com a própria
  lista. **O grafo de módulos não muda:** `registration` continua sem
  consultar `billing`, e é o cliente que junta as duas respostas — pôr a
  situação de cobrança no DTO de aluno criaria a aresta `registration →
  billing` por causa de uma coluna de tela.
  Não é paginado, e é a exceção consciente à regra de "listagem sempre
  paginada": a resposta não é uma listagem, é o conjunto que a tela cruza com
  a página que já tem em mãos — paginá-la obrigaria a buscar todas as páginas
  antes de pintar a primeira linha. O tamanho é limitado pelo número de alunos
  do estúdio e cada item é um número; quando isso deixar de ser desprezível, o
  sucessor é a forma filtrada de `GET /users?studentIds=`, que já existe aqui.
  Ids distintos: dois meses atrasados são o mesmo aluno uma vez só. Aluno
  desativado entra na lista como qualquer outro — a fatura sobrevive à saída
  (não há DELETE de aluno, para o histórico de cobrança não sumir), e filtrá-lo
  esconderia o caso mais caro que existe: quem foi embora devendo.
- `GET /default-schedules/by-student` (ADMIN) → mapa `studentId → time_of_day`
  (HH:mm) de quem tem horário padrão. Mesmo raciocínio do
  `overdue-student-ids` acima: **o grafo não muda**, `registration` continua
  sem consultar `scheduling`, e é o cliente que cruza esta lista com a tabela
  de alunos para pintar a coluna "Horário". Aluno sem horário simplesmente não
  aparece no mapa. O caminho vem antes de `/{id}` no controller, mesma cautela
  do `overdue-student-ids`: "by-student" não pode ser confundido com um id.
- `GET /me/schedule` passa a devolver o dia do aluno **com** a descrição do
  comunicado e os cancelamentos dele (`SchedulingService.dayOfStudent`, que
  filtra `dayOf` em vez de reexpandir). Sem isso a tela do aluno não teria
  como mostrar "os comunicados que o afetam" — e "não tem aula hoje" ficaria
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
  exceções: comunicado não é evidência de horário. Com uma resposta só, ou a
  janela chegou inteira ou a tela mostra o erro e não conclui nada — não
  existe mais meia semana para conciliar.
- Filtro de situação nas listagens vive na URL: ausência de parâmetro é
  "ativos" (o default), `?active=false` é inativos e `?active=all` é todos.
- Navegação: `/admin/default-schedules` ("Horários") e
  `/admin/subscriptions` ("Assinaturas") entram no menu do ADMIN;
  `/admin/holidays` virou `/admin/announcements` ("Comunicados");
  `/me/invoices` entra na barra do aluno (o professor, com uma tela só,
  não ganha navegação).
- O recuo da página que deixou de existir (nascido na SPEC-008) virou o
  hook `usePageClamp` e vale para todas as listagens: desativar o único
  registro da última página não pode deixar a tela dizendo "nenhum aluno
  ativo" com a página 1 cheia.
- 409 na criação de horário, comunicado e assinatura é estado legítimo
  ("alguém chegou primeiro"), não erro genérico: a tela mostra a mensagem
  do back e refaz a listagem, que já estava desatualizada. No 409 de login
  já em uso o botão continua armado — escolher outro login é a saída.

- A linha de aluno só oferece ação de acesso depois que
  `GET /users?studentIds=` responde: a tabela chega antes (são duas
  chamadas), e "ainda não sei" não pode virar "não tem login". O back não
  impede um segundo usuário para o mesmo aluno — só o login é UNIQUE —,
  então um clique nesse intervalo criaria conta duplicada.
- A coluna "Cobrança" fica vazia enquanto a consulta não responde, em vez de
  mostrar "Em dia". Mesmo cuidado da consulta de acesso: são chamadas
  separadas, a tabela chega antes, e "ainda não sei" virar "está tudo certo" é
  pior que não dizer nada — é justamente a informação que o ADMIN veio buscar.
  Por isso "Em dia" existe como rótulo: célula vazia e "sem atraso" não podem
  ser a mesma coisa na tela. Falha na consulta também se explica, com "tentar
  de novo": calada, a coluna vazia faria o ADMIN concluir que ninguém está
  atrasado — a leitura mais cara possível do erro.
- A coluna "Horário" segue o mesmo padrão da coluna "Cobrança": célula vazia
  (não "—") enquanto `['scheduling','schedules-by-student']` não responde, e
  "tentar de novo" quando a consulta falha. Só depois de resolvida é que
  ausência no mapa vira "—" — traço e "ainda não sei" também não podem ser a
  mesma coisa aqui.
- A baixa invalida `['billing','overdue-ids']` além de `['billing','invoices']`.
  A baixa acontece em `/invoices` e o atraso deixa de existir para a
  `/students`, que não estava montada — sem invalidar, ela voltaria do cache
  afirmando um atraso já pago. O teste que cobre isso injeta `staleTime` e
  `gcTime` infinitos de propósito: com os padrões, toda volta refaz a chamada de
  qualquer jeito e o verde não provaria nada.
- **"Dar baixa" virou "Renovar" na UI de `/invoices` (mudança 8).** O botão da
  linha, o título do diálogo e o toast mudaram de texto; endpoint
  (`POST /invoices/{id}/payment`) e comportamento continuam os mesmos. O
  diálogo fala em renovar o ciclo do aluno, não mais em vencimento
  recalculado — a baixa nunca moveu o vencimento (SPEC-004), e o texto antigo
  estava incorreto nesse ponto.
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
- O link de WhatsApp da grade tira o "+" do E.164 (`wa.me` só aceita dígitos) e
  descarta o que não for dígito, em vez de reescrever: telefone inválido vira
  link quebrado — visível — e não uma conversa aberta com outra pessoa.
- O número aparece como o cadastro guarda, em E.164, sem máscara pt-BR. É a
  mesma grafia das outras telas (`/students`, `/teachers`) e é a que funciona
  quando o professor copia o número para outro aplicativo — "(11) 91111-0000"
  colado no WhatsApp não acha ninguém. Quem resolve a leitura é o link, que é
  a ação de verdade; a máscara só resolveria a soletração.
- O rótulo do link inclui o número visível ("Conversar com Fulano no WhatsApp:
  +55…"). Nome acessível que não contém o texto da tela deixa o link
  inalcançável para quem navega por voz (WCAG 2.5.3); o número sozinho, para
  quem usa leitor de tela, não diria de quem é.
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
