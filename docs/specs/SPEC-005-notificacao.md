# SPEC-005 — Notificação (WhatsApp via Evolution API)

## Objetivo
Consumir os seis eventos do catálogo e avisar o aluno por WhatsApp sem
duplicar mensagem. Único módulo que fala com o mundo externo.

## Regras
- `notification` depende de `shared` e `registration` — de onde saem nome,
  telefone e a lista de alunos ativos — e também de `scheduling` e `billing`,
  cujos eventos consome. Escutar um evento é referenciar o record que o
  publicador expõe na raiz, e isso é dependência de compilação como qualquer
  outra: declarar só `{shared, registration}` faz o `ArchitectureTest`
  recusar, nomeando as seis referências. As quatro arestas são acíclicas
  (nenhum desses módulos conhece notification) e o acoplamento em execução
  continua assíncrono e num sentido só.
- Nenhuma tabela de outro módulo é lida daqui: o que vem de fora vem do
  payload do evento ou da `RegistrationFacade`.
- Porta `WhatsAppSender` e adapter `EvolutionApiSender` ficam em `internal/`,
  **não na raiz**: a raiz é contrato para outros módulos, e nenhum módulo
  chama notification — ele só escuta. Trocar de provedor é um adapter novo,
  sem tocar nos listeners.
- Um `@ApplicationModuleListener` por evento: `ClassCanceled`,
  `ClassRescheduled`, `AnnouncementCreated`, `InvoiceDueSoon`, `InvoiceOverdue`,
  `PaymentRegistered` (contrato em docs/eventos.md).
- `notification_log`: student_id, template, reference_key, sent_at.
  UNIQUE(template, student_id, reference_key) — a idempotência do
  at-least-once vira constraint, mesmo raciocínio de
  `uk_invoice_subscription_period`. `reference_key` identifica o fato dentro
  do payload: a data em `ClassCanceled` e `AnnouncementCreated`, o `invoiceId`
  nos três de billing, e origem+destino em `ClassRescheduled`.
- `ClassRescheduled` leva o destino na chave, e não só a data original,
  porque a mesma aula pode ser remarcada duas vezes a partir do mesmo dia:
  apagar a exceção e recriá-la republica o evento com `originalDate` igual e
  destino diferente. Com a data original sozinha, o aluno ficaria sem saber
  da segunda mudança.
- Ordem dentro da transação: **grava primeiro, envia depois**. A constraint é
  o guarda contra entrega concorrente, e o envio só acontece se o INSERT
  passou. Falha no envio → exceção → rollback → a linha some e a publicação
  continua incompleta no registry, que reprocessa. Só existe linha de envio
  bem-sucedido: coluna de status e contador de tentativas não existem porque
  tentativa falha nunca sobrevive ao rollback que a própria falha causa.
- `AnnouncementCreated` → uma mensagem para cada aluno **ativo**, via
  `RegistrationFacade.findActiveStudents()`. Notification não pergunta à
  agenda quem tem aula no dia: `notification → scheduling` é aresta que o
  projeto não abre. O custo assumido é avisar também aluno ativo sem horário
  padrão.
- **Dois templates, escolhidos pelo `suspendsClasses` do evento**:
  `ANNOUNCEMENT_SUSPENSION` ("não teremos aula: <motivo>") e
  `ANNOUNCEMENT_NOTICE` ("aviso sobre o dia: <motivo>. As aulas acontecem
  normalmente"). São avisos opostos, e mandar o primeiro por um comunicado que
  não suspende esvaziaria a sala num dia de aula. O template não é só texto:
  entra na chave de idempotência junto com a data, então os dois avisos são
  independentes um do outro no log.
- **A descrição vai no texto da mensagem**, e é por isso que o evento a carrega:
  "não teremos aula" sem o motivo faz o aluno perguntar. O antigo
  `HOLIDAY_CREATED` dizia só "é feriado", que era o motivo por definição —
  comunicado não tem esse luxo. Migration V018 renomeia as linhas antigas para
  `ANNOUNCEMENT_SUSPENSION` antes de trocar o CHECK, e a chave de idempotência
  (a data) sobrevive à renomeação: quem já foi avisado não é avisado de novo.
- **Corrigir um comunicado avisa de novo; repetir o mesmo, não.** Apagar e
  recriar na mesma data com o outro efeito republica o evento com template
  diferente, e o aluno recebe a correção — que é o que se quer, porque ele está
  acreditando no aviso anterior. Recriar com o *mesmo* efeito cai na UNIQUE e
  não reenvia nada, que também é o que se quer: é a mesma mensagem que ele já
  leu. Os dois casos têm teste (`AnnouncementFanOutIT`).
- **Reprocessar é trabalho de alguém.** Deixar a publicação incompleta não
  reentrega nada por si. Quem reentrega é o `EventRepublicationJob` (em
  `shared`, com ShedLock), que varre o registry a cada 5 minutos — e é o
  **único** caminho: a republicação de subida do Modulith está desligada, pelo
  motivo registrado nas decisões abaixo. O job fica em `shared` porque o
  registry é de lá; `auth` também consome evento, e um job dentro de
  notification reprocessaria entrega alheia.
- **Reentrega tem teto** (`shared.events.max-age`, 24h). `event_publication`
  não guarda contador de tentativas, então sem teto o que falha em definitivo
  — telefone desligado, aluno que sumiu — é retentado a cada 5 minutos para
  sempre. Passado o teto o job desiste e emite WARN; a linha continua com
  `completion_date` nulo, que é a única evidência de que aquele aviso nunca
  saiu, e apagá-la seria esconder o problema.
- **A duplicação não é impossível, é estreita.** Entre o envio retornar e o
  commit da transação existe uma janela: queda do banco nesse intervalo deixa
  a mensagem entregue e a linha desfeita, e o reprocessamento avisa o aluno de
  novo. Fechá-la exigiria chave de idempotência do lado da Evolution, que a
  API não oferece. Gravar antes de enviar reduz a janela ao mínimo possível
  sem isso; o resto é o preço do at-least-once com efeito colateral externo.
- **Falhas seguidas abortam o fan-out** (`notification.consecutive-failure-limit`,
  default 5). N falhas em sequência não são azar com N alunos, é o gateway
  fora do ar, e insistir custa um read timeout por aluno — 500 alunos com a
  Evolution pendurada seguram uma thread por mais de uma hora. Abortar chega no
  mesmo lugar, porque o que sobrou volta pelo job de republicação.
- Cada aluno do fan-out é gravado e enviado em **transação própria**
  (`REQUIRES_NEW`). Sem isso, falhar no aluno 150 desfaria as 149 linhas já
  gravadas e o reprocessamento mandaria 149 mensagens repetidas — o fan-out
  num commit só transforma falha parcial em duplicação em massa. O listener
  acumula as falhas e lança ao final para o registry reprocessar; quem já
  recebeu é barrado pela UNIQUE.
- **Aluno inativo não recebe nada** — nem aviso de agenda, que não faz sentido
  para quem não tem aula, nem cobrança, porque perseguir dívida de ex-aluno é
  decisão de quem administra e não efeito colateral de um job noturno. Mesma
  assimetria do resto do projeto: o automático serve para parar, não para
  continuar. A checagem é `studentIsActive`, que cobre junto o aluno que nunca
  existiu — `findStudentContact` sozinho **não serve**, porque usa `findById` e
  devolve inativo normalmente.
- Nos dois casos o listener encerra sem enviar e **sem** lançar. Reprocessar
  não traria o aluno de volta; deixar a publicação incompleta para sempre só
  entupiria o registry.
- Texto das mensagens em português, num enum `MessageTemplate` com o formato
  ao lado do nome. O nome do template é identificador e fica em inglês; o
  corpo, que o aluno lê, não.
- Datas das mensagens formatadas no fuso do estúdio a partir do bean `Clock`
  — nunca `LocalDate.now()`.
- Logs: um registro estruturado por envio, com template, studentId e
  resultado. **Nunca** o telefone completo nem o corpo da mensagem.
- Config em `notification.evolution.*` (@ConfigurationProperties):
  `base-url`, `api-key`, `instance`, `connect-timeout`, `read-timeout`,
  `enabled`. Segredo só por variável de ambiente.
- `enabled: false` troca o adapter por um `LoggingWhatsAppSender` que só
  registra. É o default fora de produção — sem ele, subir a aplicação local
  exigiria uma sessão de WhatsApp de verdade.
- Cliente HTTP é `RestClient` com timeout explícito de conexão e de leitura.
  Timeout ausente é o jeito mais comum de um listener assíncrono travar para
  sempre.
- Evolution API roda em container em rede Docker interna, sem porta exposta
  ao host. A sessão de WhatsApp é credencial.
- Apagar exceção continua não publicando evento (SPEC-003): agora que o
  consumidor existe, a decisão fica confirmada — o aluno já avisado pode
  ficar com informação desatualizada, e um evento de "desfiz" duplicaria
  mensagem no caso comum de correção imediata logo após o engano.

## Critérios de aceite
- Evento reprocessado com envio já gravado → nenhuma mensagem nova.
- Evolution fora do ar → nada em `notification_log` e
  `event_publication.completion_date` continua nulo; quando ela volta, o
  reprocessamento **entrega o aviso ao aluno**. O critério é o aviso chegar,
  não a linha ficar pendente — pendência é o mecanismo, e afirmar só isso
  deixa passar a versão em que ninguém reprocessa.
- Comunicado com 3 alunos ativos → 3 linhas em `notification_log`; falha no
  segundo aluno preserva a linha do primeiro.
- Aluno sumido da fachada não recebe mensagem e não deixa evento preso.
- Adapter testado com `MockRestServiceServer`; listeners com
  @ApplicationModuleTest e o helper `Eventually`.

## Decisões tomadas na implementação
- **A lista de dependências prevista estava errada e o build provou.**
  `{shared, registration}` foi recusada pelo `ArchitectureTest` nomeando seis
  referências: escutar um evento é referenciar o record do publicador. A lista
  real é `{shared, registration, scheduling, billing}`. Ver
  `docs/design-summary.md`, onde as alternativas descartadas estão registradas.
- **Configuração incompleta derruba a subida.** `enabled=true` sem `base-url`
  ou sem `api-key` recusa criar o adapter. A alternativa era subir normal e
  falhar em cada envio — dentro de um listener assíncrono, que é o pior lugar
  para descobrir erro de deploy. Com o envio desligado, um aviso no log na
  subida: ninguém nota que nada está saindo até um aluno reclamar.
- **A violação da UNIQUE na corrida não é tratada.** Duas entregas do mesmo
  evento passando juntas pela consulta fazem o INSERT perdedor estourar, e a
  exceção sobe. É de propósito: uma vez violada a constraint a transação já
  está condenada, e engolir o erro trocaria o rollback por um
  `UnexpectedRollbackException` no commit. O reprocessamento resolve.
- **A Evolution tem banco próprio (`evolution`), não schema dentro de
  `tonus`.** São dados de produto de terceiro, com migrations que não passam
  pelo Flyway daqui. Criado por script em `docker-entrypoint-initdb.d`, que só
  roda na primeira subida do volume.
- **O serviço da Evolution fica atrás de um profile do compose.** Sem isso,
  `docker compose up -d db` subiria junto um gateway de WhatsApp em qualquer
  máquina de desenvolvimento. E a variável da chave usa `${VAR:-}` e não
  `${VAR:?}`: o compose interpola o arquivo inteiro antes de olhar profiles,
  então a forma que exige a variável quebraria justamente aquele comando.
- **O telefone vai para a Evolution em dígitos, sem o "+".** O cadastro guarda
  E.164 (`+5511999990000`) e a API documenta o campo `number` em dígitos puros
  (`5511999990000`), que é a forma do JID do WhatsApp. Há relato de que ela
  tolera o "+", mas apostar em tolerância não documentada na única saída
  externa do sistema não se justifica. A conversão é do adapter: E.164 é a
  representação certa para guardar, e cada provedor converte na borda.
- **A republicação de subida do Modulith foi desligada.** Ela faz
  `resubmitIncompletePublications(__ -> true)` — tudo que está incompleto, sem
  olhar idade —, o que furava o teto de `max-age` e, sobretudo, impedia podar
  `notification_log`: no fan-out de comunicado as linhas dos já avisados convivem
  com uma publicação incompleta, e apagá-las com a publicação ainda
  republicável reenviaria a mensagem. Desligada, o teto vale sempre e a poda
  fica segura; a recuperação após restart passa a levar até um tique do cron.
- **Poda do histórico.** `NotificationLogCleanupJob` apaga aviso com mais de
  `notification.log-retention` (180 dias). Um piso de 7 dias recusa
  configuração perigosa: a retenção precisa ser folgadamente maior que a
  janela de reentrega, e as duas moram em módulos diferentes.
- **Poda do registry.** `EventPublicationCleanupJob` apaga entrega concluída
  depois de `shared.events.retention` (7 dias). Só o que fechou — o que está
  pendente é justamente o que o job de republicação precisa achar. Sem poda a
  tabela cresce para sempre no caminho quente de toda publicação.
- **O ShedLock saiu de billing para shared.** A decisão anterior era "a tabela
  de lock é infra genérica e mora em shared, mas quem liga o suporte é quem
  usa", com billing como único consumidor. Com o job de republicação houve um
  segundo consumidor, em módulo que não pode conhecer billing.
- **O primeiro teste de falha de envio media a coisa errada.** Ele afirmava
  que a publicação ficava incompleta e parava aí — e ficava verde com o aviso
  nunca saindo, porque nada reprocessava. Foi assim que a lacuna atravessou o
  RED e a IMPLEMENTA. O teste agora vai até o aluno receber.
- **`MessageTemplate` guarda os formatos de data, hora e dinheiro.** Ficaria
  natural em `shared`, mas o único consumidor é a mensagem que o aluno lê —
  formato de exibição não é infra transversal enquanto ninguém mais o usar.

## Fora de escopo
Adapter Meta Cloud API, opt-out, múltiplos canais, templates ricos, janela de
silêncio, endpoint REST de consulta dos envios.
