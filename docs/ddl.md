# DDL — tabelas e constraints

Fonte de verdade do schema. O que vale de fato é o conteúdo de
`backend/src/main/resources/db/migration`; este documento existe para explicar
**por que** cada constraint existe, já que várias delas são regra de negócio e
não detalhe técnico.

Nunca usar `ddl-auto` para criar nada — `validate` é o único modo permitido.
Toda mudança entra como migration nova; migration aplicada não se edita.

## Convenções

- Nomes em inglês, sem exceção (tabela, coluna, constraint, arquivo).
- Arquivo: `V###__modulo_descricao.sql`.
- Toda entidade de negócio herda `id`, `created_at`, `updated_at` de `BaseEntity`.
- Timestamps preenchidos via JPA auditing, que usa o bean `Clock` — por isso
  data em teste é controlável e `LocalDateTime.now()` é proibido no código.

## Migrations aplicadas

| Versão | Módulo | O que faz |
|---|---|---|
| V001 | auth | `users` |
| V002 | auth | `refresh_token` |
| V003 | shared | `audit_log` |
| V004 | registration | `teacher` |
| V005 | registration | `student` |
| V006 | shared | `event_publication` (registry do Modulith) |
| V007 | auth | `users.active` + índices do vínculo |
| V008 | scheduling | `default_schedule` |
| V009 | scheduling | `schedule_exception` |
| V010 | scheduling | `holiday` |
| V011 | billing | `subscription` |
| V012 | billing | `invoice` |
| V013 | billing | `payment` |
| V014 | shared | `shedlock` (tabela de lock do ShedLock) |
| V015 | notification | `notification_log` |
| V016 | billing | realinha `subscription.due_day` para o dia do cadastro do aluno (dado, não schema) |

---

## `users` (auth)

`id`, `login`, `password`, `role`, `student_id`, `teacher_id`, `active`,
`created_at`, `updated_at`.

- `uk_users_login` — login é a identidade de acesso; duplicar quebraria a
  autenticação.
- `role CHECK IN ('ADMIN','TEACHER','STUDENT')` — a autorização inteira depende
  desses três valores; um valor fora da lista não teria significado nenhum.
- `password` guarda hash BCrypt, nunca a senha.
- `student_id` / `teacher_id` — vínculo com o cadastro. **Sem FK, de propósito:**
  a integridade é garantida em código, no `AuthService.createUser`, via
  `RegistrationFacade`. Pôr FK aqui acoplaria os dois módulos no schema de um
  jeito que o `ApplicationModules.verify()` não enxergaria, e o acoplamento
  ficaria sem guarda automática. Ver `docs/design-summary.md`.
- `active` — revogação de acesso. Quem sai do estúdio tem o login barrado por
  aqui; a linha permanece, porque apagá-la levaria junto o histórico.

## `refresh_token` (auth)

`id`, `user_id`, `token_hash`, `family`, `expires_at`, `used`, timestamps.

- FK para `users` — token órfão não teria a quem se referir.
- `uk_refresh_token_token_hash` — o hash é a chave de busca do token
  apresentado; duplicata tornaria a resolução ambígua.
- `family` agrupa a cadeia de rotações. Reuso de um token já usado revoga a
  família inteira: é como se detecta roubo de token.
- Guarda hash, nunca o token em claro.

## `audit_log` (shared)

`id`, `entity_name`, `entity_id`, `action`, `changed_at`.

- Populada pelo `AuditListener` apenas para entidades anotadas com `@Audited`.
- Por decisão de arquitetura, só `invoice.status` e `default_schedule` serão
  auditados. Cadastro de aluno/professor **não** gera registro.

## `teacher` (registration)

`id`, `name`, `phone`, `active`, timestamps.

- Só esses campos, por minimização LGPD. Nada de CPF, endereço ou foto.
- `active` — saída do estúdio. Não existe DELETE físico.

## `student` (registration)

`id`, `name`, `phone`, `teacher_id`, `active`, timestamps.

- **`teacher_id NOT NULL` + `fk_student_teacher`** — é o critério de aceite
  "criar aluno sem professor → 400" expresso no banco. A API também valida, mas
  a constraint segura mesmo se alguém escrever direto no banco ou se a validação
  do DTO for removida por engano. Há teste cobrindo os dois caminhos.
- `idx_student_teacher_id` — a fachada lista alunos por professor.
- `idx_student_active` — listagem filtrada por `?active=`.
- Não existe DELETE: aluno que sai é desativado, para preservar o histórico de
  fatura que apontará para ele.

## `default_schedule` (scheduling)

`id`, `student_id`, `time_of_day`, timestamps.

- **`uk_default_schedule_student`** — é o critério de aceite "segundo horário
  para o mesmo aluno → 409" expresso no banco. A API também recusa, mas há
  teste que insere direto no banco justamente para provar que a regra não
  depende da validação de aplicação.
- **Sem `teacher_id`, de propósito** — o professor da aula é o do vínculo atual
  em `student`. Um campo aqui seria uma segunda verdade, que diverge da
  primeira na troca de professor de um aluno.
- `student_id` **sem FK**, mesma decisão de `users.student_id`: FK entre módulos
  acopla no schema de um jeito que o `ApplicationModules.verify()` não enxerga.
  A existência do aluno é validada via `RegistrationFacade`.
- `time_of_day`, e não `time`, porque `TIME` é nome de tipo em SQL.

## `schedule_exception` (scheduling)

`id`, `student_id`, `date`, `type`, `new_date`, `new_time`, timestamps.

- `uk_schedule_exception_student_date` — uma exceção por aluno por dia. Duas
  tornariam o dia ambíguo: cancelou e remarcou? remarcou para onde?
- `ck_schedule_exception_type` — `CANCELED` ou `RESCHEDULED`, mesma razão do
  CHECK de `users.role`: valor fora da lista não teria significado.
- **`ck_schedule_exception_target`** — o par `(new_date, new_time)` existe se e
  somente se for `RESCHEDULED`. Remarcação sem destino seria uma aula que some
  sem reaparecer; cancelamento com destino não quer dizer nada.
- `idx_schedule_exception_date` / `idx_schedule_exception_new_date` — a
  expansão da agenda pergunta duas coisas por dia: quem tem exceção em D (para
  subtrair) e quem foi remarcado para D (para somar).

## `holiday` (scheduling)

`id`, `date`, `description`, timestamps.

- `uk_holiday_date` — feriado é global, uma linha por data. Duplicata tornaria
  ambíguo o "existe feriado em D?" que a expansão da agenda pergunta.
- Não existe feriado por professor ou por turma: se existisse, a pergunta
  deixaria de ter resposta única.

Nenhuma tabela de agenda guarda aula materializada: a agenda de um dia é
derivada a cada consulta (template − feriado − exceções + remarcações).
Materializar obrigaria a gerar linhas para o futuro inteiro e a reconciliar
tudo a cada troca de horário.

## `event_publication` (shared)

Registry do Spring Modulith. Schema espelha o mapeamento de
`JpaEventPublication` — `ddl-auto: validate` confere esta tabela como qualquer
outra, então divergência derruba o contexto na subida.

Cada evento entregue a um listener vira uma linha, marcada como completa só
quando o listener termina. Linha com `completion_date IS NULL` é entrega que
não fechou — é isso que sustenta o at-least-once. Ver `docs/eventos.md`.

## `subscription` (billing)

`id`, `student_id`, `monthly_amount`, `due_day`, `active`, timestamps.

- `student_id` **sem FK**, mesma decisão de `default_schedule.student_id`: a
  integridade é garantida em código, via `RegistrationFacade`.
- `uk_subscription_student` — um aluno tem no máximo uma assinatura. Duas
  tornariam ambíguo quem o job diário deveria faturar.
- `ck_subscription_due_day CHECK (due_day BETWEEN 1 AND 31)` — dia de mês
  válido; a resolução para o último dia do mês (31 em fevereiro) é lógica de
  domínio (`Subscription.dueDateFor`), não do banco.
- `due_day` é **cópia do dia do mês de `student.created_at`**, gravada na
  criação da assinatura (SPEC-004). Não vem do cliente e não muda — a baixa de
  fatura, que antes o realinhava para o dia do pagamento, não o toca mais.
  Continua sendo coluna daqui, e não uma leitura do `registration` a cada
  fatura, porque o job diário não pode depender de outro módulo para resolver a
  data do que gera; é cópia de um valor que não retroage.

## `invoice` (billing)

`id`, `subscription_id`, `reference_month`, `amount`, `due_date`, `status`,
timestamps.

- `subscription_id` com FK normal para `subscription`: as duas tabelas são do
  mesmo módulo, então não há o acoplamento entre módulos que se evita em
  `student_id`/`teacher_id`.
- `reference_month` guarda sempre o dia 1 do mês — é a representação de
  `YearMonth` no banco, para reaproveitar `DATE` em vez de duas colunas
  separadas de ano e mês.
- **`uk_invoice_subscription_period`** — uma fatura por assinatura por
  competência. É o critério de aceite "job rodando 2× no mesmo dia não
  duplica fatura" expresso no banco: a idempotência do job vira constraint,
  não só verificação em código.
- `ck_invoice_status CHECK (status IN ('OPEN', 'PAID', 'OVERDUE'))` — mesma
  razão do CHECK de `users.role`.
- `idx_invoice_status` / `idx_invoice_due_date` — o job diário varre faturas
  por status (abertas vencendo → atrasadas) e por vencimento (quem vence
  hoje).
- Auditada (`@Audited`): transição de status de fatura precisa ficar
  rastreável — mesma decisão de `default_schedule`.

## `payment` (billing)

`id`, `invoice_id`, `payment_date`, `registered_by`, timestamps.

- `invoice_id` com FK normal para `invoice` (mesmo módulo).
- `registered_by` guarda o id do usuário ADMIN que deu baixa, **sem FK**:
  billing não pode depender de auth (fecharia ciclo com `auth → registration`).
- **`uk_payment_invoice`** — uma fatura só pode ser paga uma vez. É o
  critério de aceite "baixa em fatura já paga → 409" expresso no banco.

## `shedlock` (shared)

`name`, `lock_until`, `locked_at`, `locked_by`.

- Schema padrão exigido pelo `JdbcTemplateLockProvider` do ShedLock — não
  segue a convenção de `BaseEntity` (sem `id`/`created_at`/`updated_at`)
  porque não é uma entidade de negócio, é infra do agendador.
- Fica em `shared`, não em `billing`: é infra genérica reutilizável por
  qualquer módulo que precise de job com lock distribuído, mesmo raciocínio
  de `event_publication` (V006) — infra do Modulith, não dado de domínio de
  um módulo de negócio. `billing` é o único consumidor hoje (job diário de
  cobrança), mas a tabela não é dele.

## `notification_log` (notification)

`id`, `student_id`, `template`, `reference_key`, `sent_at`, timestamps.

- `student_id` **sem FK**, mesma decisão de `default_schedule.student_id` e
  `subscription.student_id`: a integridade é garantida em código, via
  `RegistrationFacade`.
- **`uk_notification_log_template_student_reference`** — UNIQUE(`template`,
  `student_id`, `reference_key`). É o critério de aceite "evento reprocessado
  não reenvia mensagem" expresso no banco, e não só verificação em código: a
  entrega é at-least-once, e a constraint é o que segura duas entregas
  concorrentes do mesmo evento — a verificação sozinha tem janela entre o
  SELECT e o envio. Mesmo raciocínio de `uk_invoice_subscription_period`.
- `reference_key` é `VARCHAR` e não uma coluna por tipo de evento: cada
  template deriva sua chave do próprio payload (a data em `CLASS_CANCELED` e
  `HOLIDAY_CREATED`, o id da fatura nos três de cobrança, origem+destino em
  `CLASS_RESCHEDULED`) e não há formato comum entre eles.
- `ck_notification_log_template CHECK (template IN (...))` — mesma razão do
  CHECK de `invoice.status`.
- **Não há coluna de status nem contador de tentativas.** A linha é gravada
  antes do envio, e falha no envio derruba a transação inteira: uma tentativa
  malsucedida nunca chega a existir no banco. Quem rastreia falha é
  `event_publication.completion_date IS NULL` — a tabela do Modulith já é o
  registro de "não terminou", e duplicá-lo aqui daria dois lugares para
  discordar.
- `idx_notification_log_student` — histórico de avisos de um aluno.
- Sem `@Audited`: a tabela já é, ela própria, o registro do que aconteceu.
- **Podada por `notification.log-retention` (180 dias), e isso só é seguro
  porque a reentrega tem teto.** A linha é a chave de idempotência: apagá-la
  enquanto a publicação correspondente ainda puder ser reentregue faz o aluno
  receber a mensagem de novo — e no fan-out de feriado as linhas dos já
  avisados convivem com uma publicação incompleta, então o caso não é
  hipotético. O teto é `shared.events.max-age` (24h) somado a
  `republish-outstanding-events-on-restart=false`; ligado, o Modulith
  reentrega na subida tudo que está incompleto sem olhar idade, e não haveria
  teto nenhum. Um piso em `NotificationProperties` recusa retenção menor que
  7 dias, porque as duas configurações moram em módulos diferentes e nada
  mais as relaciona.

