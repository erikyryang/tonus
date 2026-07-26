# SPEC-003 — Agenda (template + exceções + feriados)

## Objetivo
Agenda recorrente seg–sex por template, com mudanças pontuais e feriados.
Valida a decisão de arquitetura central do projeto.

## Regras
- `default_schedule`: student_id (UNIQUE — 1 horário por aluno), time_of_day.
  SEM teacher_id: professor deriva do vínculo em `student`.
- `schedule_exception`: student_id, date, type (CANCELED/RESCHEDULED),
  new_date/new_time (obrigatórios se RESCHEDULED, nulos se CANCELED —
  CHECK constraint). UNIQUE(student_id, date).
- `holiday`: date UNIQUE, description.
- Agenda de um dia D = template (se D é dia útil) − feriado em D
  − exceções em D + remarcações cuja new_date = D.
- `new_date` de uma remarcação não pode ser passado nem um feriado já
  cadastrado → 400. Nenhuma das duas é constraint: dependem de estado fora da
  linha (a data de hoje, a tabela `holiday`). Cancelamento não tem destino e
  por isso é aceito no passado — é registro de fato consumado.
- Sábado/domingo: agenda vazia por definição.
- Grade do professor = agenda agrupada por hora (grupo emerge de professor+hora).
- Endpoints (ADMIN salvo indicação):
  - `GET /schedule/day?date=` — agenda do dia já expandida. `date` obrigatório.
  - `GET /me/schedule[?date=]` (ALUNO) e `GET /me/grid[?date=]` (PROFESSOR).
    Sem `date`, respondem sobre hoje.
  - `/default-schedules` — POST, GET (id e listagem paginada), PUT, DELETE.
  - `/schedule-exceptions` — POST, GET (id e listagem paginada com filtros
    opcionais `studentId`, `from`, `to`), DELETE.
  - `/holidays` — POST, GET (listagem paginada com `from`/`to` opcionais),
    DELETE.
- Eventos publicados (contrato em docs/eventos.md): ClassCanceled,
  ClassRescheduled, HolidayCreated.

## Critérios de aceite
- Segundo horário para o mesmo aluno → 409 (via constraint, não só validação).
- Feriado suprime todos os alunos do dia; remarcação aparece na nova data.
- GET /me/schedule de um aluno nunca retorna dado de outro (id vem do token).
- Testes de integração exercitam as constraints com Postgres real.

## Decisões tomadas na implementação
- **Nomes**: os rótulos em português da conversa viraram `scheduling`,
  `default_schedule`, `schedule_exception`, `holiday` no código e no schema.
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
- **Bloqueio de remarcação para feriado é assumidamente parcial**: vale para
  feriado já cadastrado, mas nada impede cadastrar o feriado depois. Nesse caso
  a aula some da agenda, porque o feriado suprime o dia inteiro. A checagem
  existe para o ADMIN descobrir na hora, não para garantir consistência.
- **Apagar remarcação não publica evento.** O aluno já avisado pode ficar com
  informação desatualizada. Decidido junto com a SPEC-004, quando existir
  consumidor — inventar o contrato agora, sem quem o consuma, seria adivinhação.
- **Relógio fixo nos testes de agenda** (`SchedulingClockConfig`): com as regras
  de data passada, teste que usa o relógio do sistema passa hoje e falha sozinho
  quando a data virar passado. Quebra longe da mudança de código é a pior de
  investigar.

## Fora de escopo
Capacidade máxima por horário, turma como entidade, recorrência customizada,
agenda em fim de semana (exceto remarcação pontual, que é permitida).
