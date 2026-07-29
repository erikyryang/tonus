# SPEC-007 — Agenda do dia (ADMIN)

## Objetivo
A tela central do sistema: visão de um dia, agrupada por hora, com as
ações de cancelar e remarcar. É a única tela com decisão de UX real —
as escolhas de layout feitas durante a implementação entram em
"Decisões tomadas na implementação".

## Regras
- Rota /schedule (shell ADMIN). Fonte: GET /schedule/day?date=,
  query key ['scheduling','day',date].
- Navegação de data: anterior / hoje / próximo + date picker.
  Default: hoje (fuso do estúdio já é responsabilidade do back).
- Layout: colunas/blocos por hora (apenas horas com aula), cada bloco
  com professor e seus alunos — o grupo emerge de professor+hora, como
  no domínio. Densidade de painel de recepção: informação primeiro.
- Estados de dia inteiros, mutuamente exclusivos:
  - feriado → banner com a descrição, nenhum grupo;
  - fim de semana → aviso "sem aulas aos fins de semana";
  - dia útil vazio → vazio explícito ("nenhuma aula neste dia");
  - loading e erro padrão do apiClient.
- Aluno remarcado PARA este dia aparece no grupo do novo horário com
  badge "remarcada de dd/MM". Se o DTO do dia ainda não distinguir
  origem (template vs remarcação), acrescentar o campo no back —
  toque documentado, mesmo padrão do /auth/me na SPEC-006.
- Ações por aluno (menu na linha):
  - Cancelar → dialog de confirmação que AVISA o efeito real:
    "O aluno será avisado por WhatsApp." Confirmou →
    POST /schedule-exceptions {type: CANCELED, date} → invalidar
    ['scheduling','day',date] → toast de sucesso.
  - Remarcar → dialog com nova data + nova hora (zod: data não
    passada). Erros do back (data no passado, feriado já cadastrado)
    exibidos como mensagem do Problem Details, não genérica.
    Sucesso → invalidar o dia de ORIGEM e o de DESTINO.
  - Desfazer exceção (aluno cancelado/remarcado no dia) →
    DELETE /schedule-exceptions/{id} com confirmação; devolve o dia
    ao template. Exige que o DTO do dia carregue o id da exceção.
- Sem optimistic update: mutação → invalidate → refetch. Na escala do
  estúdio o refetch é instantâneo e elimina toda a classe de bugs de
  rollback visual. Decisão registrada, revisitável se doer.
- Aluno cancelado no dia: exibido em seção separada "Cancelamentos"
  (riscado), não some — o admin precisa ver o que cancelou para poder
  desfazer.

## Critérios de aceite (Vitest + RTL + MSW)
- Dia com 2 horas × 2 professores renderiza os grupos corretos.
- Feriado renderiza o banner e nenhum grupo; sábado renderiza o aviso.
- Cancelar: confirmação → request correto no MSW → lista atualizada
  (MSW responde o dia sem o aluno após a mutação).
- Remarcar para data com feriado: MSW responde 400 Problem Details e
  a mensagem do back aparece no dialog.
- Remarcação bem-sucedida invalida origem e destino (asserção nas
  chamadas refeitas).
- Aluno remarcado para o dia exibe a badge com a data de origem.

## Decisões tomadas na implementação
- **A data mora na URL (`/admin/schedule?date=`)**, não em `useState`. Num
  painel de recepção "volta pro dia de ontem" é gesto de navegador: recarregar,
  voltar no histórico e mandar o link de um dia continuam funcionando. Data
  ausente ou malformada cai em hoje, sem tela de erro.
- **Toque no backend em `GET /schedule/day`** (única parte fora de `frontend/`),
  pelo mesmo motivo do `/auth/me` na SPEC-006: o dia passa a carregar o estado
  inteiro, para a tela não ter de remontar a expansão da agenda no navegador.
    - `holiday` — descrição do feriado, nula fora dele. Sem ela a tela deduziria
      "vazio, deve ser feriado", inventando um motivo que não tem como saber.
    - `teacherName` em cada entrada, para agrupar por professor com nome e não
      com id. Veio de `StudentSummary.teacherName` no `registration`, resolvido
      em UMA consulta por lista — um id por aluno viraria N+1 na tela mais
      quente do sistema.
    - `originDate` e `exceptionId` nas entradas de origem `RESCHEDULED`: a
      primeira é a badge "remarcada de dd/MM", a segunda é o que permite
      desfazer. Nulos quando a aula vem do template.
    - `cancellations` — lista à parte, com `exceptionId`, nome do aluno, nome do
      professor e a hora do horário padrão (nula se o aluno não tiver um).
- **Cancelamento fica ao lado da agenda, não dentro dela.** A aula cancelada não
  acontece; misturá-la às demais faria a recepção chamar aluno que não vem. Fora
  da agenda e riscada, o ADMIN vê o que cancelou e pode desfazer.
- **Remarcado não entra em "Cancelamentos".** O desfazer dele mora na entrada do
  dia de DESTINO, onde a aula de fato acontece e onde a badge já explica de onde
  ela veio. Aparecer nos dois lugares daria dois botões para a mesma exceção.
- **Feriado e fim de semana não listam cancelamento**: não havia aula para
  cancelar, e a exceção registrada nesses dias não muda nada na tela.
- **O aviso de fim de semana só vale para sábado/domingo *sem nada*.**
  Remarcação pontual para o fim de semana é permitida pelo domínio (SPEC-003);
  esconder a aula atrás do aviso faria a tela mentir justamente no caso
  excepcional. Havendo entradas, o dia renderiza normalmente.
- **Aula que veio de remarcação oferece só "Desfazer".** Não é simplificação de
  tela: é o que a API comporta hoje. A exceção que traz a aula é indexada pela
  data de ORIGEM, e a expansão do dia adiciona a remarcação a partir dela
  (`findByNewDate`), sem consultar as exceções do dia de destino. Consequências
  do que seria oferecer os outros itens:
    - *Cancelar* gravaria uma segunda exceção (date = destino), dispararia o
      WhatsApp e **não** tiraria a aula da tela — ela apareceria ao mesmo tempo
      no bloco de horário e em "Cancelamentos".
    - *Remarcar de novo* criaria uma exceção com date = destino e a aula passaria
      a aparecer nos **dois** dias.
  O caminho correto é em dois passos explícitos: desfazer no destino e, no dia de
  origem, cancelar ou remarcar. Resolver em um passo é mudança de API (cancelar/
  mover uma remarcação existente, via PUT na exceção) e fica registrado aqui em
  vez de escondido atrás de um item de menu que mente.
- **Sem biblioteca de UI nova.** Modal (`components/ui/dialog`) e menu de linha
  (`components/ui/actionMenu`) foram escritos à mão: três itens de menu e uma
  confirmação não justificam Radix, e o CLAUDE.md do front proíbe adotar
  dependência de UI sem discussão. Também não se usou `<dialog>` nativo — o
  suporte no jsdom é irregular e o comportamento passaria a depender do ambiente
  de teste. O que a biblioteca daria de graça está explícito e testado: foco
  entra no diálogo (no primeiro campo, nunca na ação destrutiva), fica preso nele
  enquanto aberto e volta para o botão que o abriu ao fechar; Escape e clique no
  fundo fecham, exceto com mutação em andamento; o menu anda por setas/Home/End e
  devolve o foco ao gatilho.
- **Live region montada sempre**, com a mensagem entrando dentro dela. Criar o
  elemento junto com o texto não é anunciado: leitor de tela observa mudanças em
  regiões que já existem.
- **`keepPreviousData` na troca de dia**, com indicador "Atualizando…". Piscar
  para branco a cada clique na seta é o que mais atrapalha num painel de
  recepção; o indicador evita que a tela minta mostrando o dia velho sob a data
  nova.
- **`GET /schedule/day` responde por um único `SchedulingService.dayOf(date)`.**
  Compor três chamadas públicas repetiria a consulta de feriado, a de alunos
  ativos e a de horários padrão a cada abertura da tela mais usada do sistema. A
  expansão continua tendo uma implementação só, compartilhada com `/me/schedule`
  e `/me/grid`.
- **Validação de data passada duplicada no formulário** (zod), a única
  duplicação que o CLAUDE.md permite. Evita a ida à API por erro de digitação
  óbvio; a autoridade continua sendo o back, e é a mensagem dele (Problem
  Details) que aparece quando o motivo é outro — feriado, por exemplo.
- **Invalidação testada por comportamento**: o teste do dia de destino usa um
  QueryClient com `staleTime` infinito, para que uma chamada repetida só possa
  vir de invalidação e não da obsolescência padrão. `renderApp` passou a aceitar
  o cliente por parâmetro. Nada de espiar o TanStack Query por dentro.

## Validação visual (29/07/2026, Chrome, 1470px)
Rodado contra o backend real, atravessando o proxy do Vite, com dois professores
e oito alunos semeados pela API.
- Blocos por hora (07/09/10/18), professor como subtítulo do grupo, apenas horas
  com aula. Badge "remarcada de 28/07" e seção "Cancelamentos" com o nome
  riscado, hora e professor.
- Feriado: banner e nada mais. Sábado: "Sem aulas aos fins de semana."
- Remarcar para o feriado devolve a mensagem do backend no diálogo: *"Não é
  possível remarcar para um feriado: 2026-07-31"*. A data aparece em ISO porque
  é o texto do back — a spec manda exibi-lo fiel, e reescrevê-lo aqui exigiria
  interpretar a mensagem.
- Ciclo completo remarcar → desfazer conferido nos dois dias: a aula sai da
  origem, entra no destino com a badge, e volta ao template ao desfazer.
- Console sem erro nem warning da aplicação.

### Achados corrigidos aqui
- **O mesmo aluno pode aparecer duas vezes no mesmo dia, na MESMA hora e com o
  mesmo professor** — uma pela aula do template e outra pela remarcação que caiu
  ali. É o domínio funcionando (ele perdeu a aula de um dia e ganhou outra
  neste), mas na tela são dois nomes idênticos lado a lado. Duas consequências,
  ambas corrigidas:
    - a chave da lista era o id do aluno, e passou a ser `id + exceptionId`;
    - o rótulo do menu era "Ações de Fulano" nos dois, indistinguível no leitor
      de tela. Agora carrega hora e origem: *"Ações de Diego Alves às 09:00,
      remarcada de 29/07"*.
  Vale considerar, numa próxima spec, se o backend deveria recusar remarcação
  para um horário em que o aluno já tem aula. Não foi feito aqui: é regra de
  negócio nova, não correção de tela.
- **Colunas de professor com largura fixa em 3 por linha** deixavam o menu "⋯"
  na borda direita, longe do nome, e desperdiçavam metade da tela com dois
  professores. Passaram a `auto-fill` com mínimo de 19rem — abaixo disso o nome
  trunca quando a linha tem badge.

## Fora de escopo
Visão semanal, drag-and-drop, criação de horário padrão (SPEC-009),
tempo real/polling, impressão.