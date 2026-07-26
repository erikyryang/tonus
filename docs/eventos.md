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
`completion_date IS NULL` — o evento não sumiu, e dá para reprocessar. Sem esse
registry, "at-least-once" seria só uma frase.

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
- **Consome:** ninguém ainda. `notification` (SPEC-004) avisará o aluno.
- **Idempotência:** o consumidor precisa tolerar o mesmo aviso duas vezes — a
  entrega é at-least-once, e mandar WhatsApp repetido é o efeito colateral mais
  visível que existe.

### `ClassRescheduled(Long studentId, LocalDate originalDate, LocalDate newDate, LocalTime newTime)`

- **Publica:** `scheduling`, em `SchedulingService.rescheduleClass`.
- **Significa:** a aula mudou de dia/hora.
- **Carrega a data original** porque quem avisa o aluno precisa dizer de onde
  para onde; "sua aula foi remarcada" sem a origem não informa nada.

### `HolidayCreated(LocalDate date)`

- **Publica:** `scheduling`, em `SchedulingService.createHoliday`.
- **Significa:** o dia inteiro deixou de ter aula, para todo mundo.
- **Não carrega a lista de alunos afetados:** quem são eles é pergunta que o
  consumidor faz à agenda, e a resposta muda entre a publicação e o consumo.

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
