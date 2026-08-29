# Catálogo de eventos

Contrato assíncrono entre módulos. Consulta entre módulos é chamada síncrona via
fachada; **efeito colateral é evento**.

## Como funciona aqui

- Evento é `record` na **raiz** do módulo que publica — é contrato público, fica
  ao lado da fachada. Nunca carrega entidade JPA.
- Consumo com `@ApplicationModuleListener`.
- Entrega **at-least-once**: todo listener é idempotente. Não é recomendação,
  é premissa — o mesmo evento chega mais de uma vez.

### Assíncrono, e isso importa

`@ApplicationModuleListener` é assíncrono. O Modulith habilita o suporte a
`@Async` por conta própria — **não** é preciso `@EnableAsync`, e não adianta
omiti-lo para tornar o listener síncrono.

Consequência prática: quando a resposta HTTP da operação que publicou o evento
volta, o listener do outro módulo pode ainda não ter rodado. Em teste, assertar
logo depois da chamada é corrida — use o helper `Eventually` (em
`src/test/java/br/com/tonus/support`) para esperar pelo efeito.

### Durabilidade

`spring-modulith-starter-jpa` mantém o registry em `event_publication`. A linha
é criada na transação de quem publica e marcada como completa quando o listener
termina. Se o listener falhar ou o processo cair no meio, a linha fica com
`completion_date IS NULL` — o evento não sumiu.

Mas registrar não é reentregar. Quem lê essas linhas é o `EventRepublicationJob`
(em `shared`), a cada 5 minutos, e ele é o **único** caminho de reentrega: a
republicação de subida do Modulith está desligada de propósito, porque reentrega
tudo que está incompleto sem olhar idade. Sem esse job, "at-least-once" seria só
uma frase — a pendência ficaria no banco e ninguém a leria.

A reentrega tem janela: nada é retentado antes de `shared.events.min-age` (para
não competir com um listener ainda em curso) nem depois de `shared.events.max-age`
(24h). O teto existe porque a tabela não guarda contador de tentativas — sem
ele, o que falha em definitivo seria retentado para sempre. Passado o teto o job
desiste e emite WARN, e a linha fica com `completion_date` nulo: é a evidência
de que aquela entrega nunca aconteceu.

Entrega concluída é apagada depois de `shared.events.retention` (7 dias), pelo
`EventPublicationCleanupJob` — o registry não poda nada sozinho.

---

## Eventos publicados

### `StudentDeactivated(Long studentId)`

- **Publica:** `registration`, em `RegistrationService.deactivateStudent`.
- **Significa:** aluno saiu do estúdio.
- **Consome:** `auth` — `RegistrationDeactivationListener` marca
  `users.active = false` do usuário vinculado e invalida os refresh tokens
  vivos dele.
- **Idempotência:** desativar quem já está desativado e invalidar token já
  invalidado são no-ops.

### `TeacherDeactivated(Long teacherId)`

- **Publica:** `registration`, em `RegistrationService.deactivateTeacher`.
- **Significa:** professor saiu do estúdio.
- **Consome:** `auth`, mesmo tratamento.

### `ClassCanceled(Long studentId, LocalDate date)`

- **Publica:** `scheduling`, em `SchedulingService.cancelClass`.
- **Significa:** a aula daquele aluno naquele dia não acontece.
- **Consome:** `notification` (SPEC-005) — avisa o aluno por WhatsApp.
- **Só é publicado quando a aula existia** (horário padrão naquele dia ou
  remarcação chegando nele). Cancelar um dia sem aula é registrado no banco —
  o painel mostra o que o admin gravou e permite desfazer —, mas não há a quem
  avisar: "sua aula foi cancelada" para uma aula que nunca existiu seria ruído.
- **Idempotência:** o consumidor precisa tolerar o mesmo aviso duas vezes — a
  entrega é at-least-once, e mandar WhatsApp repetido é o efeito colateral mais
  visível que existe. Em `notification` a garantia é a UNIQUE de
  `notification_log`, chaveada por `(template, student_id, date)`.

### `ClassRescheduled(Long studentId, LocalDate originalDate, LocalDate newDate, LocalTime newTime)`

- **Publica:** `scheduling`, em `SchedulingService.rescheduleClass`.
- **Significa:** a aula mudou de dia/hora.
- **Consome:** `notification` (SPEC-005) — avisa o aluno por WhatsApp.
- **Só é publicado quando havia aula no dia de origem**, mesma regra de
  `ClassCanceled`: sem aula de onde partir, não há a quem avisar a mudança.
- **Carrega a data original** porque quem avisa o aluno precisa dizer de onde
  para onde; "sua aula foi remarcada" sem a origem não informa nada.
- **Idempotência:** a chave em `notification_log` inclui origem *e* destino. A
  mesma aula pode ser remarcada duas vezes a partir do mesmo dia — apagar a
  exceção e recriá-la republica o evento com `originalDate` igual e destino
  diferente —, e chavear só pela origem engoliria o segundo aviso.

### `AnnouncementCreated(Long announcementId, LocalDate date, String description, boolean suspendsClasses)`

- **Publica:** `scheduling`, em `SchedulingService.createAnnouncement`.
- **Significa:** o estúdio tem um aviso sobre aquele dia. Com
  `suspendsClasses`, o dia inteiro deixou de ter aula para todo mundo; sem ele,
  é só informação e a agenda segue.
- **Consome:** `notification` (SPEC-005) — avisa cada aluno por WhatsApp.
- **Carrega descrição e efeito** porque a mensagem depende dos dois: "não
  teremos aula" e "as aulas acontecem normalmente" são avisos opostos, e o
  motivo é o que faz o aviso valer alguma coisa. Sem eles o consumidor teria de
  perguntar à agenda para montar o texto, e `notification` não pergunta nada a
  `scheduling` — a dependência é só de compilação, num sentido só.
- **A chave de idempotência é o `announcementId`, não a data.** A data é única
  por dia, mas apagar e recriar um comunicado no mesmo dia é um fato novo — um
  aviso corrigido — e a data como `reference_key` faria o segundo aviso ser
  lido como reentrega do primeiro e engolido pela UNIQUE de `notification_log`.
  O id diferencia as duas publicações.
- **O template escolhido também faz parte da idempotência:**
  `ANNOUNCEMENT_SUSPENSION` e `ANNOUNCEMENT_NOTICE` são chaves diferentes em
  `notification_log`, de modo que a correção de um comunicado que trocou o
  efeito chega mesmo com o mesmo id.
- **Não carrega a lista de alunos afetados:** quem são eles é pergunta que o
  consumidor faz no momento do consumo, porque a resposta muda entre a
  publicação e o consumo.
- **Quem responde essa pergunta é o `registration`, não a agenda.** O
  consumidor natural seria `scheduling`, que sabe quem tem aula no dia — mas
  `notification → scheduling` não é aresta permitida, e abri-la para um
  evento só não se paga. `notification` usa
  `RegistrationFacade.findActiveStudents()` e avisa todo aluno ativo. O preço
  é avisar também quem está ativo sem horário padrão; a alternativa era mexer
  no grafo de dependências. Ver SPEC-005.

### `BroadcastRequested(String broadcastId, String message)`

- **Publica:** `scheduling`, em `SchedulingService.requestBroadcast` —
  `POST /announcements/broadcast` (ADMIN), mudança 9.
- **Significa:** o ADMIN quer mandar uma mensagem livre para todo aluno ativo,
  fora do ciclo de comunicado de dia/efeito. Não existe fato de domínio atrás
  dele — nada muda na agenda —, nem tabela própria: o disparo vive só como
  evento e como as linhas que ele gera em `notification_log`.
- **Consome:** `notification` (SPEC-005) — mesmo fan-out de `AnnouncementCreated`
  (todo aluno ativo, via `RegistrationFacade.findActiveStudents()`), template
  `BROADCAST`, mesmas regras de falha consecutiva e reprocessamento.
- **A chave de idempotência é `broadcastId` (UUID), não a data.** É a diferença
  central deste evento para `AnnouncementCreated`: o ADMIN pode legitimamente
  disparar duas vezes no mesmo dia, e as duas mensagens precisam chegar — a
  data como `reference_key` faria o segundo disparo ser lido como reentrega do
  primeiro e a UNIQUE de `notification_log` engoliria o segundo aviso.
- **Carrega a mensagem inteira** porque não há outro lugar de onde ela viria:
  ao contrário do comunicado, não existe descrição cadastrada em `scheduling`
  para o consumidor reconstituir — o texto é o próprio conteúdo do disparo.

### `InvoiceDueSoon(Long invoiceId, Long studentId, LocalDate dueDate, BigDecimal amount)`

- **Publica:** `billing`, em `BillingService.generateDueInvoices` (job diário),
  quando nasce a fatura da competência de um aluno com plano ativo.
- **Significa:** a fatura do mês venceu hoje.
- **Consome:** `notification` (SPEC-005) — avisa o aluno por WhatsApp.
- **Idempotência:** o pré-check de existência + `uk_invoice_student_period`
  garantem que o job só publica este evento uma vez por fatura, mesmo rodando
  2× no mesmo dia. O payload não mudou quando a fatura deixou de ser da
  assinatura e passou a ser do aluno (SPEC-012): ele já carregava `studentId`,
  e é por isso que `notification` não foi tocada. Ainda assim o consumidor precisa tolerar entrega repetida —
  a garantia é at-least-once.

### `InvoiceOverdue(Long invoiceId, Long studentId, LocalDate dueDate)`

- **Publica:** `billing`, em `BillingService.markOverdueInvoices` (job diário),
  na transição ABERTA -> ATRASADA.
- **Significa:** a fatura passou do prazo de tolerância
  (`billing.days-overdue`) sem pagamento.
- **Consome:** `notification` (SPEC-005) — avisa o aluno por WhatsApp.
- **Idempotência:** publicado só na transição — a query do job só varre
  faturas ainda ABERTAS, então rodar de novo no mesmo dia não republica.

### `PaymentRegistered(Long invoiceId, Long studentId, LocalDate paymentDate, BigDecimal amount)`

- **Publica:** `billing`, em `BillingService.registerPayment`, quando o ADMIN
  dá baixa numa fatura.
- **Significa:** a fatura foi paga.
- **Consome:** `notification` (SPEC-005) — manda o recibo ao aluno.
- **Idempotência:** o consumidor precisa tolerar o mesmo aviso duas vezes —
  entrega at-least-once.

---

## O que deliberadamente não é evento

**Reativação.** Não existe `StudentActivated` nem `TeacherActivated`. Reativar
alguém no cadastro **não** devolve o login: isso exige
`PATCH /users/{id}/activate` explícito do ADMIN.

A assimetria é intencional. Revogar acesso por engano é reversível com um
clique; conceder acesso por engano é um incidente. Efeito colateral automático
serve para tirar permissão, não para dar.

Como corolário, `POST /users` recusa criar login para aluno/professor inativo:
a desativação dessa pessoa já aconteceu, então o evento que revogaria o acesso
não vai se repetir — o usuário nasceria ativo e ninguém o derrubaria. Evento
cobre o que muda dali para frente, não o que já passou.
