# SPEC-003 — Agenda (template + exceções + comunicados)

## Objetivo
Agenda recorrente seg–sex por template, com mudanças pontuais e comunicados
do estúdio (que podem ou não suspender as aulas do dia).
Valida a decisão de arquitetura central do projeto.

## Regras
- `default_schedule`: student_id (UNIQUE — 1 horário por aluno), time_of_day.
  SEM teacher_id: professor deriva do vínculo em `student`.
- `schedule_exception`: student_id, date, type (CANCELED/RESCHEDULED),
  new_date/new_time (obrigatórios se RESCHEDULED, nulos se CANCELED —
  CHECK constraint). UNIQUE(student_id, date).
- `announcement`: date UNIQUE, description, suspends_classes.
  `suspends_classes = true` suprime o dia inteiro (era o feriado);
  `false` só avisa, e a agenda do dia segue normal.
- Agenda de um dia D = template (se D é dia útil) − comunicado com
  `suspends_classes` em D − exceções em D + remarcações cuja new_date = D.
- `new_date` de uma remarcação não pode ser passado nem um dia com as aulas
  suspensas por comunicado já cadastrado → 400. Comunicado sem suspensão não
  bloqueia: o dia continua tendo aula. Nenhuma das duas é constraint: dependem de estado fora da
  linha (a data de hoje, a tabela `announcement`). Cancelamento não tem destino e
  por isso é aceito no passado — é registro de fato consumado.
- Sábado/domingo: agenda vazia por definição.
- Grade do professor = agenda agrupada por hora (grupo emerge de professor+hora).
  A linha do aluno carrega **telefone**, além de id e nome: o professor atende
  aquele aluno, e falar com ele é parte do atendimento.
- Endpoints (ADMIN salvo indicação):
  - `GET /schedule/day?date=` — agenda do dia já expandida. `date` obrigatório.
  - `GET /me/schedule[?date=]` (ALUNO) e `GET /me/grid[?date=]` (PROFESSOR).
    Sem `date`, respondem sobre hoje.
  - `/default-schedules` — POST, GET (id e listagem paginada), PUT, DELETE.
  - `/schedule-exceptions` — POST, GET (id e listagem paginada com filtros
    opcionais `studentId`, `from`, `to`), DELETE.
  - `/announcements` — POST, GET (listagem paginada com `from`/`to` opcionais),
    DELETE.
- Eventos publicados (contrato em docs/eventos.md): ClassCanceled,
  ClassRescheduled, AnnouncementCreated.

## Critérios de aceite
- Segundo horário para o mesmo aluno → 409 (via constraint, não só validação).
- Comunicado com suspensão suprime todos os alunos do dia; sem suspensão não
  encosta na agenda. Remarcação aparece na nova data.
- GET /me/schedule de um aluno nunca retorna dado de outro (id vem do token).
- Testes de integração exercitam as constraints com Postgres real.

## Decisões tomadas na implementação
- **Nomes**: os rótulos em português da conversa viraram `scheduling`,
  `default_schedule`, `schedule_exception` e `announcement` (que nasceu
  `holiday`, ver abaixo) no código e no schema.
  `schedule_exception` e não `exception` — o prefixo diz de que exceção se
  trata, e `Exception` é nome infeliz para entidade Java.
- **Id do token em `/me`**: o access token passou a carregar `studentId` e
  `teacherId` como claims, e o principal virou `shared.AuthenticatedUser`.
  Sem isso, scheduling precisaria consultar a tabela `users`, que é de auth —
  e `scheduling → auth` não é uma aresta permitida. Ver `docs/design-summary.md`.
- **Hora em JSON é `HH:mm`**: a agenda é marcada em minutos cheios; o padrão do
  Jackson (`07:00:00`) só acrescenta ruído.
- **Aluno inativo não aparece na agenda**, mesmo com horário padrão cadastrado:
  a expansão indexa apenas os alunos ativos da fachada do registration.
- **`Clock` no fuso do estúdio** (`America/Sao_Paulo`, antes UTC). Instante não
  depende de fuso, mas "hoje" depende: com clock em UTC, todo aluno que
  abrisse `/me/schedule` depois das 21h veria a agenda do dia seguinte.
- **DELETE de exceção existe** e devolve o dia ao template. Sem ele, desfazer
  um cancelamento marcado por engano só seria possível no banco.
- **Não se exige horário padrão para criar exceção**, mas **apagar o horário
  apaga junto todas as exceções do aluno** — cancelamento e remarcação, sem
  seletividade. Sem a limpeza havia um bug real, não só dado morto:
  `schedule_exception` é indexada por `(student_id, date)`, sem vínculo com o
  id do horário, então uma exceção antiga sobrevivia à exclusão do horário e
  voltava a suprimir em silêncio a aula de um horário **novo** criado depois
  para o mesmo aluno no mesmo dia. Limpar tudo, e não caso a caso, é a leitura
  mais simples de "apagado" e fecha o bug sem deixar outra combinação por trás.
- **Bloqueio de remarcação é assumidamente parcial**: vale para a suspensão já
  cadastrada, mas nada impede cadastrá-la depois. Nesse caso a aula some da
  agenda, porque a suspensão suprime o dia inteiro. A checagem existe para o
  ADMIN descobrir na hora, não para garantir consistência.

- **Feriado virou comunicado** (`holiday` → `announcement`, migration V017). O
  conceito era estreito demais: o estúdio precisa avisar todo mundo de coisas
  que não cancelam aula — mudança de endereço, festa de fim de ano, obra na rua
  — e a única ferramenta que existia para falar com o estúdio inteiro suprimia
  o dia como efeito colateral. `suspends_classes` separa avisar de cancelar.
  Cada feriado existente virou comunicado que suspende, que é exatamente o que
  ele significava; a tabela antiga morreu depois da cópia, não antes.

  Consequências assumidas:
  - **A data continua UNIQUE.** Herdada de `uk_holiday_date` e pelas mesmas
    duas razões: "as aulas de D estão suspensas?" precisa de resposta única, e
    a data é a chave de idempotência do aviso em `notification_log`. Dois
    comunicados no mesmo dia fariam o segundo aviso ser engolido pela UNIQUE de
    lá, e o aluno não receberia. O preço é não poder anunciar duas coisas no
    mesmo dia; quando isso doer, o que muda é a chave do log, não esta tabela.
  - **`suspendsClasses` é obrigatório no request, sem default.** Um default
    silencioso faria um aviso comum esvaziar o dia sem ninguém ter pedido.
  - **O dia carrega o comunicado mesmo sem suspensão** (`DayScheduleView`), e é
    por isso que o campo deixou de ser a `String holiday`: a tela precisa
    distinguir "não tem aula porque foi suspenso" de "tem aula, e tem um aviso".
- **Apagar remarcação não publica evento.** O aluno já avisado pode ficar com
  informação desatualizada. Decidido junto com a SPEC-005, quando existir
  consumidor — inventar o contrato agora, sem quem o consuma, seria adivinhação.
- **Telefone do aluno na grade do professor** (`GET /me/grid`). É finalidade, não
  conveniência: o professor desmarca, avisa de atraso, combina reposição — sem o
  telefone, ele pede o número ao ADMIN por fora, e o dado circula igual sem
  nenhum controle. A minimização continua valendo no que a decisão não mudou:
  o telefone só existe nesse caminho (`/schedule/day` e `/me/schedule`
  continuam sem ele, e há teste que falha se algum passar a carregá-lo), e só
  chega o dos alunos daquele professor, porque o id vem do token. Continua
  proibido logar telefone.
- **A grade tem DTO próprio** (`TeacherClassEntry`), em vez de um campo a mais em
  `ScheduleEntry`. O `ScheduleEntry` alimenta também a agenda do ADMIN e a do
  aluno: um campo lá levaria telefone para dois caminhos sem finalidade para
  ele. Os telefones vêm em lote (`RegistrationFacade.findStudentPhones`), e só
  dos alunos que sobraram depois do filtro por professor — uma consulta, e
  nenhum telefone de aluno alheio sai do `registration`.
- **Relógio fixo nos testes de agenda** (`SchedulingClockConfig`): com as regras
  de data passada, teste que usa o relógio do sistema passa hoje e falha sozinho
  quando a data virar passado. Quebra longe da mudança de código é a pior de
  investigar.

## Fora de escopo
Comunicado por professor ou por turma, edição de comunicado (apaga e recria —
o aviso já saiu de qualquer forma), capacidade máxima por horário, turma como
entidade, recorrência customizada,
agenda em fim de semana (exceto remarcação pontual, que é permitida).
