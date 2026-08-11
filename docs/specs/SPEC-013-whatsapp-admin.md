# SPEC-013 — Administração do gateway de WhatsApp (backend)

## Objetivo
Dar ao ADMIN, pela API do produto, o controle da sessão de WhatsApp e da
versão do gateway. Hoje as duas coisas se fazem **fora** do Tônus: parear é
abrir o Manager da Evolution em `localhost:8081` e ler o QR lá; trocar a
versão é editar `.env` e recriar container. Quem administra o estúdio não tem
como saber se as mensagens estão saindo — e não tem, no produto, nada que
diga o contrário.

O sintoma que motivou a spec é real e está em pé agora: o gateway tem uma
instância `Teste01` pareada e conectada, enquanto `EVOLUTION_INSTANCE` vale
`tonus`. O app enviaria para uma instância que não existe, e nada na aplicação
denuncia isso — o erro só apareceria quando um aluno não recebesse o aviso.

## Regras

### Onde mora
- Módulo `notification`. **Ele passa a expor endpoints**, revertendo o "não
  expõe endpoint nenhum" que valia até aqui. O motivo de reverter é o mesmo
  que justificava a regra: quem fala com a Evolution é notification e mais
  ninguém (`CLAUDE.md`, DON'T). Pôr o controlador em `shared` ou em `auth`
  obrigaria outro módulo a conhecer o gateway, que é exatamente o que a regra
  protege. Administração do gateway é do dono do gateway.
- `allowedDependencies` **não muda**: `{shared, registration, scheduling,
  billing}`. Nenhum módulo novo é referenciado, e continua não existindo
  caminho de volta — ninguém chama notification.
- Controladores e DTOs REST em `internal/web/`; entidade e regras em
  `internal/domain/`; serviço e adapter soltos em `internal/`. Mesma divisão
  dos outros módulos.

### O adapter de sessão é outro
- Porta `WhatsAppSessionGateway` e adapter `EvolutionSessionGateway`, em
  `internal/`, **separados** de `WhatsAppSender`/`EvolutionApiSender`. Enviar
  mensagem e administrar sessão são responsabilidades diferentes: o envio tem
  um fake legítimo (`LoggingWhatsAppSender`, que é o default de
  desenvolvimento), e administrar sessão não tem equivalente — "parear no
  vazio" não significa nada.
- **A administração não pode depender de `notification.evolution.enabled`.**
  Ligar o envio é o que se faz *depois* de parear; se a tela de pareamento só
  funcionasse com o envio ligado, não haveria ordem possível de operações. O
  gateway de sessão exige apenas `base-url` e `api-key` preenchidos.
- Sem `base-url` ou `api-key`, os endpoints respondem **503** com Problem
  Details dizendo que o gateway não está configurado — não 500, e não uma
  tela em branco. É a única condição em que a API admite não saber responder.
- Timeout de leitura próprio para operações de sessão, maior que o de envio:
  criar instância e gerar QR levou segundos na Evolution real, contra o
  `read-timeout: 10s` dimensionado para `sendText`. Reaproveitar o do envio
  transformaria pareamento normal em timeout.

### Endpoints (todos ADMIN)
| Método | Rota | O quê |
|---|---|---|
| `GET` | `/whatsapp/status` | instância em uso, estado da conexão, número mascarado, versão em vigor × desejada |
| `GET` | `/whatsapp/instances` | instâncias que existem no gateway, para o seletor |
| `PUT` | `/whatsapp/settings` | grava instância escolhida e versão desejada |
| `POST` | `/whatsapp/session` | cria a instância se faltar e devolve o QR |
| `DELETE` | `/whatsapp/session` | desconecta, preservando a instância |

- Autorização por rota em `SecurityConfig` (`auth`), com `hasRole("ADMIN")` —
  é onde a autorização do projeto mora, junto de `/plans/**` e
  `/billing-settings`. Não é anotação no controlador.
- Não é `/me/...`: não há recurso "do próprio usuário" aqui. A sessão é uma
  só, do estúdio.
- `POST /whatsapp/session` é **idempotente para o caso conectado**: numa
  instância já `open`, devolve o estado atual e **nenhum QR**, sem derrubar a
  sessão. Parear de novo por engano não pode custar a sessão que está
  funcionando — quem quer trocar de número desconecta antes, explicitamente.

### O que nunca sai daqui
- **Número completo, nunca** — nem na resposta, nem no log. Máscara
  `+55 11 9****-**77`. É a mesma regra que já vale para o envio, e a tela
  precisa apenas do suficiente para o ADMIN reconhecer o aparelho.
- **A `api-key` não é devolvida por nenhum endpoint.** É segredo de ambiente;
  a tela mostra se o gateway está configurado, não com o quê.
- **O QR não é persistido nem logado.** Ele é credencial de pareamento com
  validade de segundos: gravar seria criar uma cópia durável de algo que só
  existe para ser consumido na hora.

### `whatsapp_settings` (V025)
Linha única, mesmo padrão de `billing_settings`:

`id`, `instance_name`, `desired_phone_version`, `created_at`, `updated_at`.

- **`ck_whatsapp_settings_singleton CHECK (id = 1)`** — linha única por
  constraint, não por convenção. Duas linhas fariam "qual instância vale?"
  ter como resposta "a que o LIMIT 1 pegou".
- Semeada na própria migration com o valor que hoje vem da configuração, para
  não existir estado sem linha.
- **`@Audited`**. Trocar a instância ou a versão muda por onde e como o
  estúdio fala com todos os alunos; é a mesma classe de mudança que
  `invoice.status` e `default_schedule`, que já geram registro.

### Precedência da instância
- **A tabela ganha de `EVOLUTION_INSTANCE`.** A configuração vira o valor
  inicial (semente da migration), não a autoridade permanente — senão a tela
  ofereceria uma escolha que o envio ignora.
- Isso obriga uma mudança no `EvolutionApiSender`: hoje ele recebe `instance`
  no construtor, fixado na subida. Passa a resolver a instância **por envio**,
  a partir das settings. É a parte da spec com risco de regressão, porque
  toca o caminho que já está em produção — e é por isso que o critério de
  aceite abaixo amarra "trocou a instância → o próximo envio usa a nova".

### Versão do WhatsApp: o limite é real e fica declarado
- `CONFIG_SESSION_PHONE_VERSION` é **variável de ambiente global do
  container**. Verificado na Evolution v2.3.7 em execução: `settings` por
  instância expõe apenas `rejectCall`, `msgCall`, `groupsIgnore`,
  `alwaysOnline`, `readMessages`, `readStatus`, `syncFullHistory` e
  `wavoipToken`, e um campo `version` enviado no `/instance/create` é
  silenciosamente ignorado. **Não existe caminho pela API para trocar a
  versão.**
- Portanto a tela edita uma **intenção**, não o gateway: `desired_phone_version`
  na tabela, e a resposta marca `pending: true` enquanto ela diferir da que
  está em vigor. Aplicar continua sendo recriar o container.
- **"Em vigor" é o melhor esforço possível, e a spec não finge o contrário.**
  O valor exibido é o `notification.evolution.phone-version` que a *aplicação*
  lê — o mesmo `WHATSAPP_VERSION` do `.env` que o container recebeu, desde que
  os dois tenham subido do mesmo arquivo. Se alguém editar o `.env` e recriar
  só a aplicação, o número exibido será o do arquivo e não o que roda dentro
  do gateway. Não há como fechar essa fresta: nenhum endpoint da Evolution
  devolve a versão de WhatsApp Web em uso.
- Formato validado (`2.\d+.\d+`), porque um valor inválido só se manifestaria
  no próximo pareamento, muito depois de salvo.

### Erros
- RFC 9457 pelo handler global, como o resto. Evolution fora do ar → **502**,
  não 500: a falha é de um serviço a montante, e a distinção é o que separa
  "o gateway caiu" de "o Tônus tem um bug".
- Falha de gateway **não altera o banco**. Salvar settings e falar com a
  Evolution são operações distintas e não se arrastam.

## Critérios de aceite
- STUDENT e TEACHER recebem 403 em toda rota `/whatsapp/**`; ADMIN passa.
- **Instância configurada que não existe no gateway devolve estado "não
  existe", com 200** — é o caso `tonus`/`Teste01` que está em pé hoje, e ele
  não pode virar 500 nem tela vazia.
- `POST /whatsapp/session` com a instância inexistente cria e devolve QR
  base64 não vazio.
- `POST /whatsapp/session` com a instância já `open` devolve conectado, sem
  QR, **e a sessão continua conectada depois da chamada**.
- `DELETE /whatsapp/session` desconecta e a instância continua existindo —
  logout não é apagar.
- `PUT /whatsapp/settings` trocando a instância faz o **próximo envio de
  notificação** usar a nova. Sem esse teste, a escolha da tela é decorativa.
- Versão desejada diferente da em vigor → `pending: true`; iguais →
  `pending: false`.
- Versão em formato inválido → 400 com Problem Details, e nada gravado.
- Gateway fora do ar → 502 com Problem Details, sem stacktrace, e
  `whatsapp_settings` intacta.
- Nenhuma resposta e nenhuma linha de log contêm o número completo nem a
  `api-key`.
- Trocar instância ou versão deixa registro de auditoria.
- Testes: `@ApplicationModuleTest` para o módulo e os endpoints; contrato do
  `EvolutionSessionGateway` contra a Evolution real, no estilo do
  `EvolutionApiContractIT` — mesma imagem e mesmas variáveis do compose, para
  rota renomeada quebrar no teste e não no deploy. Postgres real via
  Testcontainers, sem H2.

## Decisões tomadas na implementação

- **O import do `.env` fazia a suíte falar com a Evolution de verdade.**
  `spring.config.import` (adicionado antes desta spec) faz o app ler o `.env`,
  e os testes rodam com `backend/` como diretório de trabalho — então
  `EVOLUTION_BASE_URL` e a chave da máquina do desenvolvedor chegavam ao perfil
  de teste, o adapter real era construído e os endpoints de sessão batiam no
  container que estivesse no ar. Só apareceu porque o
  `WhatsAppNotConfiguredIT` respondeu 200 onde afirma 503. `base-url` e
  `api-key` passaram a ser fixados vazios em `application-test.yml`, junto com
  o `enabled` que já estava.
- **Nenhuma chamada ao gateway acontece dentro de transação.** A primeira
  versão tinha `@Transactional` no serviço e compunha o status — que consulta a
  Evolution — dentro dela; no caminho de update, dentro de uma transação de
  *escrita*. Com 60s de read timeout para operações de sessão, é uma conexão de
  banco presa por algo que não tem nada a ver com o banco.
  `WhatsAppSettingsStore` isola o que é transacional e devolve um snapshot, não
  a entidade.
- **O sender não pode depender do serviço de administração.** Amarrar o bean
  `evolutionApiSender` a `WhatsAppAdminService` quebrou o
  `NotificationConfigTest`, que sobe só o `@Configuration`, sem banco. A
  instância passou a ser resolvida por `ObjectProvider` dentro do lambda: a
  tabela é a autoridade, a configuração é o fallback de um contexto sem
  settings, e o `@Configuration` volta a ser testável sozinho.
- **`jsonPath(...).doesNotExist()` passa para um null presente também.** Foi
  por isso que o `@JsonInclude(NON_NULL)` inicial atravessou o RED: a asserção
  não distinguia campo ausente de campo nulo. O front tropeçou nisso —
  `undefined !== null` abriria o painel de QR no caso já-conectado, sobre uma
  imagem inexistente. As respostas passaram a emitir null explícito, cumprindo
  o contrato publicado, e as asserções viraram `value(nullValue())`, que
  falha para chave ausente.
- **Os payloads da Evolution viraram records em vez de `Map`.** Ela devolve
  cerca de vinte campos por instância e três importam. Nomeá-los faz uma
  renomeação a montante falhar na borda, em vez de virar um null que se lê como
  "desconectada" — o modo de errar mais plausível deste adapter.
- **`PUT /whatsapp/settings` continua recebendo os dois campos juntos.**
  Separar em dois endpoints deixaria a troca de instância (destrutiva quando há
  sessão) distinta do ajuste de versão (rotineiro), e o front apontou o
  incômodo. Mantido assim mesmo: é a mesma forma de `billing_settings`,
  e o custo — a tela sempre reenviar os dois — é menor que uma segunda rota
  para o mesmo recurso. A perda de atualização concorrente que isso admite é a
  de qualquer PUT de objeto inteiro no projeto.
- **A entidade estende `BaseEntity`, ao contrário de `BillingSettings`.**
  Aquela evita de propósito, porque o id não é gerado. Aqui `@Audited` obriga:
  o listener lê o id por `BaseEntity` e grava `unknown` para qualquer outra
  coisa, o que deixaria a trilha inútil. O id gerado nunca é exercitado — a
  linha nasce na migration e daí em diante só é carregada e atualizada.
- **O número mascarado aparece mesmo com a instância desconectada.** A
  Evolution devolve `ownerJid` depois que a sessão cai, e a spec não previa o
  caso. Mantido visível: saber qual número estava pareado é justamente o que
  quem vai reparear precisa para confirmar que é o aparelho certo. Continua
  mascarado, então não é exposição nova.
- **Validação de tamanho no request, espelhando as colunas.** Sem ela, um nome
  de instância com 101 caracteres vira violação de constraint e chega ao ADMIN
  como 500. A regra é a mesma dos dois lados; o que muda é se ela é explicada.

## Fora de escopo
Reiniciar e apagar instância pela tela; webhooks da Evolution; mais de uma
instância ativa ao mesmo tempo; a aplicação recriar o container do gateway;
adapter de outro provedor; histórico de conexões e desconexões.
