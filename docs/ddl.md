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
| V017 | scheduling | `announcement` (substitui `holiday`, que é copiado e removido) |
| V018 | notification | templates de comunicado no CHECK de `notification_log` |
| V019 | billing | `plan`, `plan_enrollment`, `billing_settings` |
| V020 | billing | assinaturas viram planos + matrículas (dado, não schema) |
| V021 | billing | `invoice.student_id` no lugar de `subscription_id`; troca a UNIQUE |
| V022 | billing | `DROP TABLE subscription` |
| V023 | notification | template `BROADCAST` no CHECK de `notification_log` (mudança 9) |
| V024 | shared | `event_publication.{status,last_resubmission_date,completion_attempts}` (Spring Modulith 2.x) |
| V025 | notification | `whatsapp_settings` (SPEC-013) |

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
- Gravada em transação própria (REQUIRES_NEW, via `AuditLogWriter`): uma
  mudança de negócio que depois faz rollback deixa mesmo assim o rastro da
  tentativa — que é exatamente o que um log de compliance precisa encontrar.
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

## `announcement` (scheduling)

`id`, `date`, `description`, `suspends_classes`, timestamps.

- Substituiu `holiday` na V017: o conceito precisava cobrir o aviso que **não**
  cancela aula, e o feriado era o caso particular com `suspends_classes = true`.
  Cada linha antiga foi copiada assim, e a tabela morreu depois da cópia.
- `uk_announcement_date` — comunicado é global, uma linha por data. Duplicata
  tornaria ambíguo o "as aulas de D estão suspensas?" que a expansão da agenda
  pergunta, e engoliria o segundo aviso: a chave de idempotência em
  `notification_log` é a data.
- `suspends_classes NOT NULL`, sem default — a diferença entre avisar e
  cancelar não pode ser preenchida por omissão.
- Não existe comunicado por professor ou por turma: se existisse, a pergunta
  deixaria de ter resposta única.

Nenhuma tabela de agenda guarda aula materializada: a agenda de um dia é
derivada a cada consulta (template − suspensão − exceções + remarcações).
Materializar obrigaria a gerar linhas para o futuro inteiro e a reconciliar
tudo a cada troca de horário.

## `event_publication` (shared)

Registry do Spring Modulith. Schema espelha o mapeamento de
`JpaEventPublication` — `ddl-auto: validate` confere esta tabela como qualquer
outra, então divergência derruba o contexto na subida.

Cada evento entregue a um listener vira uma linha, marcada como completa só
quando o listener termina. Linha com `completion_date IS NULL` é entrega que
não fechou — é isso que sustenta o at-least-once. Ver `docs/eventos.md`.

`status`, `last_resubmission_date` e `completion_attempts` (V024) vieram do
ciclo de vida que o Modulith 2.x acrescentou ao registry. `EventRepublicationJob`
e `EventPublicationCleanupJob` continuam lendo `completion_date` como antes —
as consultas internas do Modulith para "incompleto" e "apagar concluído"
também. `status` existe porque o reenvio interno do Modulith compara
`status != RESUBMITTED`, e `NULL` nunca bate com esse `!=`; por isso o backfill
da V024 preenche toda linha existente com `COMPLETED` ou `PUBLISHED`.

Linhas **abandonadas** — nunca concluídas, além de `shared.events.max-age`, que
é quando o `EventRepublicationJob` desiste de reentregar — são podadas na mesma
janela de `retention` que as concluídas, pelo mesmo `EventPublicationCleanupJob`.
Sem isso, um evento sem consumidor nunca completaria (e.g. os três de cobrança
antes de `notification` consumi-los) e a tabela cresceria sem teto, arrastando o
scan do job de republicação a cada 5 minutos. `max-age` decide por quanto tempo
tentar; `retention`, por quanto tempo a linha continua respondendo "isso saiu?".

## `plan` (billing)

`id`, `name`, `monthly_price`, `active`, timestamps.

- Substituiu `subscription` na SPEC-012: o aluno pode assinar N planos, e o
  valor de cada um vive no catálogo, não na linha do aluno.
- `ck_plan_monthly_price CHECK (monthly_price > 0)` — plano de graça não é
  plano; se o estúdio quiser isentar alguém, o caminho é não matriculá-lo.
- Não existe DELETE: plano desativado continua explicando as faturas emitidas
  com ele.

## `plan_enrollment` (billing)

`id`, `student_id`, `plan_id`, `active`, timestamps.

- **`uk_plan_enrollment_student_plan`** — o mesmo plano não se assina duas
  vezes: a segunda linha tornaria ambíguo quanto cobrar. Cobrar em dobro se
  resolve com um plano de outro preço.
- `student_id` **sem FK**, mesma decisão de `users.student_id` e
  `default_schedule.student_id`; `plan_id` **com** FK, porque plano e matrícula
  são do mesmo módulo.
- `active` em vez de apagar a linha: "esse aluno já teve esse plano" é pergunta
  de cobrança, e é o que permite rematricular sem esbarrar na UNIQUE.
- `idx_plan_enrollment_student` — o job monta a fatura por aluno, e a tela do
  aluno pergunta os planos dele.
- **"Encerrar" (mudança 8, SPEC-009) é `active = false` por esta mesma linha**,
  via `PATCH /plan-enrollments/{id}/deactivate` — nenhuma coluna nova, nenhum
  endpoint novo. Encerra o vínculo aluno-plano, não o aluno: `student.active`
  não muda, e o job diário simplesmente para de somar este plano na fatura do
  aluno. Faturas já emitidas com este plano não são apagadas nem recalculadas
  — `invoice.amount` já era cópia congelada do que foi cobrado.

## `billing_settings` (billing)

`id`, `multi_plan_discount_percent`, timestamps.

- **`ck_billing_settings_singleton CHECK (id = 1)`** — linha única por
  constraint, não por convenção. Com duas linhas, "qual desconto vale?" teria
  como resposta "a que o LIMIT 1 pegou".
- `ck_billing_settings_discount CHECK (BETWEEN 0 AND 100)` — percentual fora
  disso não tem significado, e o negativo viraria acréscimo silencioso.
- Nasce com 0 na migration: desconto é escolha do estúdio, não default.

## `invoice` (billing)

`id`, `student_id`, `reference_month`, `amount`, `due_date`, `status`,
timestamps.

- `student_id` **sem FK** (V021, SPEC-012): a fatura passou a ser do aluno, e
  aluno é de outro módulo — mesma decisão de `plan_enrollment.student_id`. Era
  `subscription_id` com FK, quando fatura e assinatura eram ambas de billing.
- `amount` é **cópia** do que foi cobrado, não uma soma recalculável: mudar o
  preço de um plano depois não pode reescrever histórico.
- `reference_month` guarda sempre o dia 1 do mês — é a representação de
  `YearMonth` no banco, para reaproveitar `DATE` em vez de duas colunas
  separadas de ano e mês.
- **`uk_invoice_student_period`** — uma fatura por **aluno** por competência.
  É o critério de aceite "job rodando 2× no mesmo dia não duplica fatura"
  expresso no banco: a idempotência do job vira constraint, não só verificação
  em código. A chave mudou de `(subscription, competência)` na V021 — com N
  planos por aluno, a chave antiga entregaria uma fatura por plano, que é
  exatamente o que a SPEC-012 veio evitar.
- `ck_invoice_status CHECK (status IN ('OPEN', 'PAID', 'OVERDUE'))` — mesma
  razão do CHECK de `users.role`.
- `idx_invoice_status` / `idx_invoice_due_date` — o job diário varre faturas
  por status (abertas vencendo → atrasadas) e por vencimento (quem vence
  hoje). `idx_invoice_student` — a listagem do aluno e o cruzamento de
  inadimplência.
- Auditada (`@Audited`): transição de status de fatura precisa ficar
  rastreável — mesma decisão de `default_schedule`.

## `payment` (billing)

`id`, `invoice_id`, `payment_date`, `registered_by`, timestamps.

- `invoice_id` com FK normal para `invoice` (mesmo módulo).
- `registered_by` guarda o id do usuário ADMIN que deu baixa, **sem FK**:
  billing não pode depender de auth (fecharia ciclo com `auth → registration`).
- **`uk_payment_invoice`** — uma fatura só pode ser paga uma vez. É o
  critério de aceite "baixa em fatura já paga → 409" expresso no banco.
- **"Renovar" (mudança 8, SPEC-008/009) é o rótulo de UI para esta mesma
  baixa** — schema, endpoint (`POST /invoices/{id}/payment`) e semântica
  (fatura paga = ciclo renovado) não mudaram, só o texto que o ADMIN vê.

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
  os dois de comunicado, o id da fatura nos três de cobrança, origem+destino em
  `CLASS_RESCHEDULED`, e o `broadcastId` — UUID, não data — em `BROADCAST`) e
  não há formato comum entre eles.
- `ck_notification_log_template CHECK (template IN (...))` — mesma razão do
  CHECK de `invoice.status`. `BROADCAST` entrou na V023 (mudança 9): disparo
  manual do ADMIN, sem tabela própria — o evento e as linhas daqui são o único
  registro de que ele aconteceu.
- **`status` (`DELIVERED` | `DISCARDED`) veio na V026.** A linha passa a
  registrar *como* o aviso foi tratado: entregue, ou deliberadamente
  descartado (aluno inativo ou sem contato). O marcador `DISCARDED` existe pela
  idempotência do at-least-once: sem ele, uma reentrega do mesmo evento (um
  fan-out que falhou em outro aluno mantém a publicação incompleta) mandaria um
  aviso velho para um aluno reativado no meio do caminho. **Não é um retrocesso
  da regra antiga:** falha de envio continua sem deixar linha (a linha e o envio
  dividem a transação, e a falha derruba as duas) — descarte é decisão, não
  falha.
- `idx_notification_log_student` — histórico de avisos de um aluno.
- Sem `@Audited`: a tabela já é, ela própria, o registro do que aconteceu.
- **Podada por `notification.log-retention` (180 dias), e isso só é seguro
  porque a reentrega tem teto.** A linha é a chave de idempotência: apagá-la
  enquanto a publicação correspondente ainda puder ser reentregue faz o aluno
  receber a mensagem de novo — e no fan-out de comunicado as linhas dos já
  avisados convivem com uma publicação incompleta, então o caso não é
  hipotético. O teto é `shared.events.max-age` (24h) somado a
  `republish-outstanding-events-on-restart=false`; ligado, o Modulith
  reentrega na subida tudo que está incompleto sem olhar idade, e não haveria
  teto nenhum. Um piso em `NotificationProperties` recusa retenção menor que
  7 dias, porque as duas configurações moram em módulos diferentes e nada
  mais as relaciona.

---

## `whatsapp_settings` (notification)

`id`, `instance_name`, `desired_phone_version`, timestamps.

- **`ck_whatsapp_settings_singleton CHECK (id = 1)`** — linha única por
  constraint, não por convenção, mesmo raciocínio de `billing_settings`. Aqui a
  resposta ambígua seria pior: "qual instância vale?" decide para onde vai toda
  mensagem para todo aluno, e "a que o LIMIT 1 pegou" não é resposta.
- `ck_whatsapp_settings_instance CHECK (length(trim(instance_name)) > 0)` —
  instância em branco faria o adapter montar `/message/sendText/` e receber um
  404 de rota, que se parece com bug do produto e não com configuração vazia.
- **`instance_name` tem precedência sobre `EVOLUTION_INSTANCE`.** A
  configuração é só a semente da migration; a tabela é a autoridade a partir
  daí. Sem isso a tela ofereceria uma escolha que o envio ignora — e o
  `EvolutionApiSender` resolve a instância a cada envio justamente por causa
  disso, em vez de fixá-la na subida.
- **`desired_phone_version` é intenção, não estado.** A versão que governa o
  pareamento é variável de ambiente do container da Evolution, e nenhum
  endpoint dela devolve a que está em uso. A coluna guarda o que o ADMIN quer;
  a API compara com o que a *aplicação* recebeu e marca `pending` quando
  divergem. Aplicar continua sendo recriar o container.
- **`@Audited`**. Trocar instância ou versão muda por onde e como o estúdio
  fala com todos os alunos — mesma classe de mudança que `invoice.status` e
  `default_schedule`. É também por isso que a entidade estende `BaseEntity`,
  ao contrário de `BillingSettings`: o listener de auditoria lê o id por ali e
  registraria `unknown` para qualquer outra coisa.
