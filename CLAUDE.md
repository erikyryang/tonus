- Todo código Java, pom.xml, migrations, testes e docker-compose:
  dentro de backend/. Nunca na raiz.
- Caminhos relativos neste documento que se referem a código
  assumem backend/ como base (ex.: src/main/resources/... =
  backend/src/main/resources/...).
- Convenções específicas do backend que surgirem depois podem ir
  num backend/CLAUDE.md próprio; este aqui é o contexto geral.

## Fontes de verdade (leia antes de implementar)
- docs/design-summary.md — decisões de arquitetura consolidadas
- docs/ddl.md — tabelas e constraints
- docs/eventos.md — catálogo de eventos (contrato entre módulos)
- docs/specs/SPEC-00X-*.md — spec da feature em desenvolvimento
Regra: spec e código mudam no mesmo PR.

## Stack
Java 21, Spring Boot 3 (Web MVC, Data JPA, Security), Spring Modulith,
PostgreSQL, Flyway, Testcontainers, ShedLock, Maven.

## Arquitetura
- Monólito modular. Pacotes:
  br.com.tonus.{shared, auth, registration, scheduling, billing, notification}
- Raiz de cada módulo: SÓ fachada + eventos publicados (o contrato).
  Todo o resto em internal/ — subpastas web/ (controllers + DTOs REST)
  e domain/ (entidades + regras); services e repositories soltos.
- Dependências permitidas: scheduling→registration, billing→registration,
  auth→registration, notification→{registration, scheduling, billing},
  todos→shared. NADA além disso.
  Sem ciclos — registration não pode conhecer auth, isso fecharia ciclo.
  notification aponta para scheduling e billing porque consome os eventos
  deles, e escutar um evento é referenciar o record que o publicador expõe na
  raiz — dependência de compilação como qualquer outra. O acoplamento em
  execução continua assíncrono e num sentido só. Ver docs/design-summary.md.
  Declarado em @ApplicationModule(allowedDependencies) no package-info.java de
  cada módulo e verificado por ApplicationModules.verify() em teste. Sem a
  declaração a lista não é conferida por nada: aresta nova exige editar o
  package-info, e é isso que força a decisão a ser consciente.
- Comunicação entre módulos: query = chamada síncrona via fachada;
  efeito colateral = evento via @ApplicationModuleListener.
- Entrega de eventos é at-least-once: todo listener é idempotente. O registry
  persistido (spring-modulith-starter-jpa, tabela event_publication) é o que
  torna isso verdadeiro — junto com quem o lê: sem os jobs de shared
  (EventRepublicationJob e EventPublicationCleanupJob) a pendência fica
  registrada e ninguém a reentrega.
- @ApplicationModuleListener é ASSÍNCRONO — o Modulith liga o suporte a @Async
  sozinho, sem @EnableAsync. Em teste, esperar pelo efeito (helper Eventually);
  assertar logo após a resposta HTTP é corrida.

## Nomenclatura (padrão do projeto)
- Todo identificador de código é em inglês: pacotes, classes, métodos,
  variáveis, DTOs, eventos, arquivos e diretórios.
- Isso vale também para o banco: nomes de tabela, coluna, constraint e
  arquivo de migration são em inglês. Nada de português em `db/migration`.
- Nomes de módulo em português usados neste documento e nas specs (cadastro,
  agenda, cobrança, notificação) são só rótulos de domínio para a conversa;
  no código e no schema usar sempre o equivalente em inglês:
  cadastro→registration, agenda→scheduling, cobrança→billing,
  notificação→notification, aluno→student, professor→teacher,
  usuário→user, auditoria→audit, fatura→invoice,
  horário padrão→default_schedule.
- Texto voltado ao usuário final (mensagens de erro, WhatsApp, labels)
  pode ser em português — a regra é sobre identificadores, não sobre
  conteúdo exibido a humanos.
- Documentação em docs/*.md continua em português.

## Convenções
- DTO nunca é entidade JPA. DTO REST (internal/web) ≠ record de fachada.
  Mapeamento via método estático no record (sem MapStruct por ora).
- Erros: RFC 9457 Problem Details via handler global em shared.
- Endpoints de ALUNO/PROFESSOR no padrão /me/... — id extraído do token,
  NUNCA do path ou query (previne IDOR).
- Listagens sempre paginadas.
- Migrations só via Flyway em db/migration,
  nome V###__modulo_descricao.sql. Nunca ddl-auto.
- Proibido LocalDate.now()/LocalDateTime.now(): injete o bean Clock.
- @ConfigurationProperties por módulo, com prefixo próprio
  (ex.: billing.days-overdue).
- Segredos só via variável de ambiente (.env fora do git,
  .env.example versionado).
- Nunca logar telefone completo nem conteúdo de mensagem WhatsApp.
- Mudanças em invoice.status e default_schedule geram registro em audit.

## Testes
- Integração com Testcontainers (Postgres real). H2 é PROIBIDO —
  as constraints do banco são regras de negócio e devem ser testadas.
- @ApplicationModuleTest para testar módulo isolado e seus eventos.
- Datas em teste controladas via Clock fixo.

## Fluxo de desenvolvimento (TDD com gate manual)
Para cada spec, o trabalho tem duas fases obrigatoriamente separadas:

**Fase RED (padrão — sempre começa aqui):**
- Escrever os testes de integração dos critérios de aceite
  (Testcontainers + @ApplicationModuleTest quando aplicável).
- Rodar e confirmar que falham pelo motivo certo (endpoint/classe
  inexistente, não erro de compilação por engano nem bug no teste).
  Se falhar por erro de infraestrutura de teste (contexto Spring não
  sobe, bean duplicado, timeout de container), corrigir antes de
  seguir — isso não conta como o "vermelho" esperado.
- Implementar o mínimo de produção necessário para os testes
  passarem de verdade — não só compilar. "Mínimo" = o suficiente
  para ficar verde, sem polir, sem tratar casos que os testes não
  cobrem, sem refatorar além do necessário.
- Rodar de novo e confirmar que os testes passam.
- Apresentar o resultado (testes + implementação mínima) e aguardar.

**Fase IMPLEMENTA (só mediante autorização explícita):**
- Só inicia quando o usuário disser a palavra **IMPLEMENTA**.
- Objetivo: finalizar o que ficou faltando além do mínimo que passa
  nos testes — casos de borda não cobertos, hardening, refatoração,
  qualquer parte da spec que o mínimo do RED não tenha fechado.
- Sem essa palavra, mesmo que o pedido pareça pedir a finalização
  completa da feature, ficar só no mínimo que passa nos testes.

Se não estiver claro se o pedido é "mínimo pra passar os testes" ou
"finalizar tudo", perguntar antes de ir além do mínimo.

## Comandos (executar a partir de backend/)
- Build + testes: cd backend && ./mvnw clean verify   (requer Docker rodando)
- Rodar local: cd backend && docker compose up -d db && ./mvnw spring-boot:run
- Nova migration: criar arquivo em backend/src/main/resources/db/migration/

## NÃO FAÇA
- Não adicionar Kafka, Redis, cache, microserviço ou qualquer
  dependência de infra sem discutir antes.
- Não importar classes *.internal.* de outro módulo.
- Não expor entidade JPA em controller, fachada ou evento.
- Não criar endpoint que aceite alunoId vindo do cliente para
  dados do próprio aluno logado.
- Não chamar WhatsAppSender/EvolutionApiSender fora do módulo
  notificacao — comunique-se por eventos.
- Não usar H2, mocks de banco, ou @MockBean de repository em
  teste de integração.
- Não criar código, pom.xml ou configuração fora de backend/
  (exceto docs/ e arquivos de raiz como .gitignore).
- Não criar a pasta frontend/ nem qualquer código de frontend
  nesta fase — backend only.