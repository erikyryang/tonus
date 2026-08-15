# Decisões de arquitetura

Consolidado do que já foi decidido e por quê. Decisão sem motivo registrado vira
dogma e é revertida por engano seis meses depois.

## Monólito modular, não microserviços

Um deploy só, com fronteiras verificadas em tempo de teste. O estúdio é pequeno;
o custo operacional de microserviços não se paga. As fronteiras existem para o
código não virar bola de lama, não para virar rede.

## Grafo de dependências

```
shared        ← não depende de ninguém
registration  → shared
auth          → shared, registration
scheduling    → shared, registration
billing       → shared, registration
notification  → shared, registration, scheduling, billing
```

Declarado em `@ApplicationModule(allowedDependencies = ...)` de cada
`package-info.java`.

**Isso é verificado, não combinado.** Cada módulo declara
`@ApplicationModule(allowedDependencies = ...)` no seu `package-info.java`, e
`ArchitectureTest` roda `ApplicationModules.verify()`. Uma aresta não declarada
falha o build nomeando as classes que a criaram.

Antes disso a lista existia só em markdown e nada a conferia — qualquer aresta
proibida passava batido.

### Por que `auth → registration`

`auth` é dono da tabela `users`, que carrega `student_id`/`teacher_id`. Para
validar esse vínculo na criação e revogar acesso de quem sai, `auth` precisa
conhecer `registration`.

A aresta é acíclica: `registration` não conhece `auth`. A direção contrária
(`registration → auth`) é que fecharia ciclo, e por isso é proibida.

Descartadas:
- **FK no banco** (`users.student_id → student.id`): garantiria a integridade,
  mas acoplaria os módulos no schema de um jeito invisível ao
  `ApplicationModules.verify()` — coupling sem guarda.
- **Inverter a posse** (`student.user_id → users.id`): arquiteturalmente mais
  limpo, `auth` ficaria puro. Custo alto agora: refatoraria o schema de
  `auth` e `registration` e o caminho token→aluno que os endpoints `/me` usam. Continua sendo a
  melhor saída se `auth` um dia precisar existir sem `registration`.

### Por que `notification` depende de `scheduling` e `billing`

Consumir evento **é** dependência de compilação. O listener recebe
`ClassCanceled`, e esse record mora na raiz do `scheduling`; não existe jeito
de escutar sem referenciar o tipo. A lista `{shared, registration}` prevista
no desenho foi recusada pelo `ArchitectureTest` nomeando as seis referências —
descoberto na implementação, não no desenho.

Vale registrar o que a aresta **não** significa: o acoplamento em tempo de
execução continua assíncrono e num sentido só. `notification` nunca chama
`scheduling` nem `billing`, e nenhum dos dois conhece `notification` — por
isso não há ciclo. O que a declaração diz é que trocar a assinatura de um
evento quebra a compilação de quem o escuta, o que é verdade e é bom que
apareça no build.

Descartadas:
- **Escutar `Object` e filtrar por tipo em runtime**: some com a aresta no
  papel e traz de volta em forma pior — a quebra vira erro de execução num
  listener assíncrono, que é o lugar mais caro de descobrir qualquer coisa.
- **Duplicar os records em `shared`**: duas verdades para o mesmo contrato, e
  `shared` viraria depósito de tudo que dois módulos trocam.

### Por que o token carrega `studentId`/`teacherId`

Os endpoints `/me/...` vivem no módulo dono do dado — `/me/schedule` é do
`scheduling`, não do `auth`. Mas o vínculo usuário→aluno mora em `users`, que é
do `auth`, e `scheduling → auth` fecharia um ciclo (auth já depende de
registration).

Saída: o access token leva `studentId` e `teacherId` como claims, e o principal
autenticado é `shared.AuthenticatedUser`. O `auth` preenche; qualquer módulo lê,
porque todos podem depender de `shared`.

Descartadas:
- **`scheduling` consultar `users`**: a aresta proibida, direto.
- **Fachada do auth resolvendo usuário→aluno**: transformaria toda leitura de
  `/me` numa consulta síncrona a outro módulo, e ainda criaria a aresta.

Efeito colateral bom: o id do aluno não tem por onde chegar do cliente. Não é
que o endpoint confira se o id pedido é o do token — é que não existe parâmetro
de id. IDOR deixa de ser algo que alguém precisa lembrar de prevenir.

## Comunicação entre módulos

- **Consulta** → chamada síncrona via fachada (`RegistrationFacade`).
- **Efeito colateral** → evento (`@ApplicationModuleListener`), at-least-once,
  listener idempotente, registry persistido em `event_publication`.

Detalhes e catálogo em `docs/eventos.md`.

## Revogar é automático, conceder é manual

Desativar aluno/professor no cadastro derruba o acesso por evento. Reativar
**não** devolve o login — exige `PATCH /users/{id}/activate` do ADMIN.

Revogar por engano custa um clique para desfazer; conceder por engano é
incidente de segurança. Automação assimétrica é proposital.

## Idioma

Todo identificador em inglês: pacote, classe, método, tabela, coluna,
constraint, arquivo de migration, path REST (`/students`, não `/alunos`).

Os nomes em português nas specs (cadastro, agenda, cobrança) são rótulos de
domínio para conversa, não nomes de código. Texto para humano — mensagem de
erro, WhatsApp, label — continua em português.

## Segurança e privacidade

- JWT em cookie httpOnly `SameSite=Strict`. Token **nunca** no corpo da resposta.
- O atributo `Secure` do cookie é derivado de `request.isSecure()`, não de
  configuração por ambiente: HTTPS o exige, HTTP local não. A alternativa —
  uma flag ligada em produção e desligada em dev — foi descartada porque
  depende de cada máquina lembrar de aplicá-la, e o esquecimento aparece como
  "login quebrado no Safari", que é o navegador que descarta cookie `Secure`
  recebido por HTTP. Isso exige `server.forward-headers-strategy`: o Caddy
  termina o TLS e repassa HTTP, então sem os forwarded headers a aplicação
  veria HTTP e produção perderia o `Secure` — o único modo de falha da
  decisão, coberto por teste em `AuthCookieSecureIT`.
  Confiar nos forwarded headers não abre superfície aqui: a aplicação não
  constrói nenhuma URL a partir do request (todo `Location` é caminho
  relativo montado à mão), então o que um `X-Forwarded-*` forjado alcança é
  só o `Secure` do cookie — e forjá-lo o deixaria mais restrito, não menos.
- **401 e 403 são escritos direto na resposta, nunca por `sendError`.** O
  handler padrão do Spring Security chama `response.sendError(403)`, o
  container redespacha para `/error`, a cadeia de segurança também filtra
  dispatches ERROR, e nesse ponto o contexto stateless já não existe — o 401 do
  entry point substituía o 403 já decidido. A aplicação anunciava 403 no README
  e afirmava 403 em todo teste, e respondia 401 a todo cliente real. Não é
  cosmético: pela regra do front, 401 dispara refresh, retry e logout, então
  cair numa rota de outro papel deslogava o usuário em vez de dizer que faltava
  permissão. O defeito atravessou porque a suíte inteira é MockMvc, que não faz
  o redespacho — `AuthorizationStatusIT` roda em container real justamente por
  isso, e é o único teste do projeto que não confia no MockMvc.
- Refresh token rotacionado a cada uso; reuso de token já usado revoga a família
  inteira. É assim que se detecta token roubado.
- Endpoints de aluno/professor no padrão `/me/...`, com id vindo do token e
  nunca do path ou query — previne IDOR.
- Usuário desativado recebe a **mesma** resposta de senha errada. Distinguir os
  dois casos contaria a quem tenta que aquele login existe.
- LGPD por minimização: só nome, telefone e situação. Nada de CPF, endereço ou
  foto. Nunca logar telefone completo nem conteúdo de mensagem.
- CSP no front: o build de produção (`frontend/vite.config.ts`, plugin
  `tonus-csp-meta`) injeta `Content-Security-Policy` via `<meta>` só no HTML
  gerado por `vite build` — em dev quebraria o cliente de HMR, que depende de
  script inline. `<meta>` não tem efeito para `frame-ancestors`, `sandbox` e
  `report-uri` (a spec de CSP ignora essas diretivas fora de header HTTP).
  Quando o Caddyfile de produção for versionado, ele precisa setar por header
  o que o `<meta>` não cobre: `Content-Security-Policy: frame-ancestors 'none'`,
  `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: strict-origin-when-cross-origin`. Até lá, essa proteção
  específica (clickjacking) não existe — o resto da CSP (script/style/connect
  restritos a `'self'`) já está ativo desde o build atual.

## Tempo

O bean `Clock` é o único jeito permitido de saber a hora — `LocalDate.now()`
sem argumento é proibido, porque data não controlável em teste transforma
regra de negócio em coisa que passa de segunda a sexta e quebra no sábado.

O clock vive no fuso do estúdio (`America/Sao_Paulo`), não em UTC. Instante
independe de fuso, mas "hoje" não: `/me/schedule` sem data resolve o dia por
`LocalDate.now(clock)`, e em UTC todo acesso depois das 21h de Brasília
mostraria a agenda do dia seguinte. Erro que só aparece de noite é o mais caro
de descobrir.

## Cobrança por planos

O aluno assina N planos e recebe **uma** fatura por mês, com a soma dos planos
ativos e desconto a partir do segundo, guardado em `billing_settings`.
Substituiu a assinatura única por aluno, numa migração em quatro passos.

Duas decisões dali que valem para além do módulo:

- **A fatura é do aluno, não do vínculo de cobrança.** A chave única passou de
  `(assinatura, competência)` para `(aluno, competência)`. Com N planos, a
  chave antiga entregaria uma fatura por plano — e o aluno receberia três
  cobranças no mesmo dia.
- **Os eventos de billing não mudaram de payload.** `InvoiceDueSoon`,
  `InvoiceOverdue` e `PaymentRegistered` já carregavam `studentId` e nunca
  mencionaram assinatura, então `notification` atravessou a mudança sem uma
  linha alterada. É o argumento a favor de o evento carregar o identificador do
  domínio, e não o da tabela que por acaso o originou.

## Banco

Postgres, Flyway, `ddl-auto: validate` — nunca criação automática de schema.
Constraint é regra de negócio: `student.teacher_id NOT NULL` existe porque
"aluno sem professor" é proibido pelo domínio, e o teste bate na constraint
diretamente, não só na API.

Por isso teste de integração usa Postgres real via Testcontainers. **H2 é
proibido**: um banco que não tem as mesmas constraints não testa as regras.

## `shared` como shared module do Modulith

`TonusApplication` usa `@Modulith(sharedModules = "shared")`, não
`@SpringBootApplication` puro.

"Shared module" no Spring Modulith **não é inferido** de
`@ApplicationModule(allowedDependencies = {})` — é sempre declaração explícita
em `@Modulith(sharedModules = ...)`. Sem ela, `@ApplicationModuleTest` (usado
por `SchedulingEventsIT`, `BillingJobIT`) filtra o bootstrap para o módulo sob
teste + suas `allowedDependencies`, e **não inclui `shared.internal`** —
mesmo `shared` sendo dependência de todo mundo. O sintoma: `JpaAuditingConfig`
e `AuditListener` ficam de fora do contexto, `created_at`/`updated_at` nascem
nulos, e o insert quebra a constraint `NOT NULL`. É fácil de não notar porque
`@SpringBootTest` comum (a maioria dos testes) sobe o contexto inteiro e nunca
exercita esse filtro.

Efeito colateral da correção: uma vez que `shared` entra no bootstrap
filtrado, `spring-modulith-moments` (feature de eventos de troca de dia/semana
do próprio Modulith, transitiva de `spring-modulith-starter-core`, não usada
pelo projeto) passa a competir por um bean `Clock` único e falha a resolver
`@Primary` do jeito que o resto da aplicação resolve. Excluída via
`spring.autoconfigure.exclude` em `application.yml` — não é usada, então
excluir não muda comportamento nenhum, só remove ruído.
