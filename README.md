# Tonus

Sistema de gestão para escolas/academias com três perfis de usuário —
**admin**, **professor** e **aluno**. Cobre cadastro de pessoas, agenda de
aulas/horários, cobrança (faturas) e notificações via WhatsApp.

O repositório tem duas partes: uma API em `backend/` e um SPA em `frontend/`.

## Arquitetura

Monólito modular (Spring Modulith) em vez de microserviços — um único
deploy, mas com fronteiras de módulo verificadas em tempo de teste
(`ApplicationModules.verify()`), para não virar um "big ball of mud".

Módulos (pacote `br.com.tonus.*`):

| Módulo | Responsabilidade |
|---|---|
| `shared` | Infra transversal: erros (RFC 9457 Problem Details), `Clock`, auditoria, config Jackson |
| `auth` | Autenticação/autorização dos 3 papéis (JWT em cookie, refresh token) |
| `registration` | Cadastro de alunos e professores |
| `scheduling` | Agenda e horários |
| `billing` | Cobrança e faturas |
| `notification` | Notificações via WhatsApp |

Dependências permitidas: `scheduling → registration`,
`billing → registration`, `auth → registration`,
`notification → {registration, scheduling, billing}`, e todos → `shared`.
Nada além disso, sem ciclos. `notification` aponta para `scheduling` e
`billing` porque consome os eventos deles — escutar um evento é referenciar
o record que o publicador expõe na raiz, e isso é dependência de compilação
como qualquer outra; em execução o acoplamento continua assíncrono e num
sentido só. A lista é declarada em `@ApplicationModule(allowedDependencies)`
no `package-info.java` de cada módulo: aresta nova exige editar o arquivo, e
é isso que força a decisão a ser consciente.

Comunicação entre módulos é síncrona (fachada) para consultas, ou assíncrona
(eventos, at-least-once) para efeitos colaterais. Todo listener é idempotente.

**Autorização mora só no `SecurityConfig`**, em regras por rota — não há
`@PreAuthorize` em controller, e um teste de arquitetura reprova a volta do
padrão. Concentrada, a configuração é o inventário legível de quem pode
chamar o quê; espalhada em anotação, o controller esquecido não falha: ele
libera.

**Front e API são same-origin por decisão de arquitetura**, e por isso não há
CORS no backend: em desenvolvimento quem faz a ponte é o proxy `/api` do
Vite, em produção é o Caddy servindo o estático e fazendo reverse proxy. A
sessão vive em cookie `httpOnly` — o JavaScript nunca lê nem anexa token.

Decisões de arquitetura, contrato de eventos e DDL completo estão em
`docs/`; o comportamento esperado de cada feature está em
`docs/specs/SPEC-00X-*.md`.

## Stack

**Backend** — Java 21, Spring Boot 3 (Web MVC, Data JPA, Security), Spring
Modulith, PostgreSQL, Flyway, Testcontainers, ShedLock, JWT (jjwt), Bucket4j,
Maven.

**Frontend** — Vite, React 18, TypeScript (strict), React Router, TanStack
Query (estado de servidor), react-hook-form + zod (formulários), Tailwind,
Vitest + React Testing Library + MSW (testes).

## Como rodar localmente

Pré-requisitos: Java 21, Node 20+, Docker rodando, e o wrapper do Maven
(`./mvnw`, já incluso no repo — não precisa instalar Maven).

```bash
# API em localhost:8080
cd backend
cp .env.example .env        # ajuste os valores se necessário
docker compose up -d db     # sobe o Postgres
./mvnw spring-boot:run
```

```bash
# SPA em localhost:5173, com proxy /api para a API acima
cd frontend
npm install
npm run dev
```

Não há passo extra para desenvolver: **o `Secure` do cookie de sessão sai do
transporte da requisição**, não de configuração. Local é HTTP puro e o cookie
vem sem o atributo; atrás do Caddy é HTTPS e ele vem com. Nenhuma máquina
precisa lembrar de ligar nada, e não existe flag para vazar de um ambiente para
o outro.

Isso importa porque em HTTP o Safari descarta cookie `Secure` — Chrome e
Firefox abrem exceção para `localhost`, ele não. O sintoma é enganoso: o
`POST /auth/login` devolve 200, mas o `GET /auth/me` seguinte dá 401 e a tela
mostra "Login ou senha inválidos" para quem digitou a senha certa.

O que sustenta o lado de produção é `server.forward-headers-strategy`: sem ele,
o TLS terminado no Caddy ficaria invisível para a aplicação e o cookie sairia
sem `Secure`. `AuthCookieSecureIT` cobre os dois lados e o caso do proxy.

## Usuários de desenvolvimento

`POST /users` exige um ADMIN já autenticado, então o primeiro login não tem como
sair da própria API. Com o Postgres no ar e as migrations já aplicadas (ou seja,
depois de subir a aplicação ao menos uma vez), o seed abaixo resolve isso: dá a
todo mundo que está no banco um login com a **mesma senha padrão**.

```bash
cd backend
docker compose exec -T db psql -U tonus -d tonus < scripts/seed-dev.sql
```

A senha de todos é **`tonus123`**. Num banco zerado — o clone recém-feito — o
script monta o ambiente inteiro, porque só o ADMIN não daria para exercitar as
telas dos outros dois papéis:

| Login | Papel | Pessoa |
|---|---|---|
| `admin@tonus.com` | ADMIN | — |
| `marina@tonus.com` | TEACHER | Marina Duarte |
| `rafael@tonus.com` | TEACHER | Rafael Moreira |
| `beatriz@tonus.com` | STUDENT | Beatriz Lima (Marina) |
| `carla@tonus.com` | STUDENT | Carla Nunes (Marina) |
| `diego@tonus.com` | STUDENT | Diego Alves (Rafael) |
| `helena@tonus.com` | STUDENT | Helena Castro (Rafael) |

Assim que houver alguém em `teacher`/`student`, o script **para de inventar
gente** — inventar seria sujar o cadastro de quem já está usando o ambiente — e
passa a só derivar login de quem está lá, pela regra `primeironome@tonus.com`.
Cadastrou um aluno pela tela? Rode o seed de novo e ele ganha acesso. Nomes que
disputam o mesmo login (duas Marias) são desempatados pelo id.

O script é idempotente: reexecutar reaplica a senha padrão em todo mundo e cria
login só para quem ainda não tem. Duas regras que ele respeita, e que são a
razão de ele não ser um `UPDATE` qualquer:

- **Quem foi desativado no cadastro fica de fora.** O acesso dessas pessoas cai
  por evento quando elas saem do estúdio, e ressuscitá-lo aqui contradiria a
  regra.
- **Quem está ativo no cadastro mas com o login revogado é reativado.** Em
  produção a reconcessão é manual (`PATCH /users/{id}/activate`); aqui a
  promessa do script é justamente que todo mundo que está no estúdio entra.

O Flyway roda quando a **aplicação** sobe, não quando o Postgres sobe — rodar o
seed logo depois de um `docker compose up -d db` em volume novo falha, e a
mensagem diz o que fazer. `SeedDevScriptIT` executa o arquivo por `psql` contra
um Postgres real a cada build, então uma migration futura que mude `users`
quebra no CI, e não no primeiro login de quem clonou o repo.

O arquivo mora em `backend/scripts/` e **não** em `db/migration` de propósito:
migration roda em todo ambiente, e uma senha conhecida aplicada em produção é
conta aberta. Isto aqui é ferramenta de desenvolvimento — o dia em que houver
ambiente de verdade, senha por usuário volta a ser senha de verdade.

O seed cria **pessoas e acessos, não movimento**: horário padrão, assinatura e
fatura continuam vindo pela API ou pela tela, então a agenda e as faturas nascem
vazias. Encher essas telas é seed de dados de demonstração, outra ferramenta.

## Como rodar os testes

Os testes do backend são de integração, com Postgres real via Testcontainers —
**é obrigatório o Docker estar rodando**. H2 é proibido: as constraints do
banco são regras de negócio e precisam ser testadas de verdade.

```bash
cd backend && ./mvnw clean verify
```

Os do frontend são de comportamento (Vitest + RTL), com a API mockada por MSW
na fronteira HTTP — nunca o TanStack Query ou o cliente HTTP por dentro.

```bash
cd frontend && npm run test:run && npm run typecheck
```

## Estrutura do repositório

```
backend/    # código Java, pom.xml, migrations, testes, docker-compose
frontend/   # SPA (Vite + React + TypeScript)
docs/       # specs de feature e decisões de arquitetura (fonte de verdade)
CLAUDE.md   # convenções do projeto (arquitetura, nomenclatura, fluxo de dev)
```

Cada parte tem o seu próprio `CLAUDE.md` quando as convenções divergem —
`frontend/CLAUDE.md` traz as regras de sessão, rede e testes do SPA.

## Fluxo de desenvolvimento

O projeto segue TDD com um gate manual: cada spec começa na fase **RED**
(testes de aceite + o mínimo de código para passarem) e só avança para a
fase **IMPLEMENTA** — que fecha casos de borda e finaliza o que sobrou —
mediante autorização explícita. Spec e código mudam no mesmo PR. Detalhes
completos em `CLAUDE.md`.

## Status atual

Backend e frontend completos até a SPEC-007.

- **SPEC-001 (fundação: `shared` + `auth`)** — login/refresh/logout com JWT em
  cookie httpOnly `SameSite=Strict`, rotação de refresh token com detecção de
  reuso, rate limit no login, Problem Details para erros, e auditoria
  automática de entidades via listener JPA.
- **SPEC-002 (`registration`)** — CRUD de alunos e professores com vínculo
  obrigatório aluno→professor, desativação em vez de DELETE (preserva
  histórico de fatura), listagens paginadas e filtráveis, `RegistrationFacade`
  para os módulos seguintes, e criação do usuário de acesso pelo ADMIN via
  `POST /users`. Quem é desativado no cadastro tem o acesso revogado
  automaticamente, por evento.
- **SPEC-003 (`scheduling`)** — agenda recorrente seg–sex por horário padrão,
  com exceções pontuais (cancelamento e remarcação) e comunicados do estúdio,
  que podem ou não suspender as aulas do dia. A agenda de um
  dia é derivada a cada consulta, nunca materializada. Endpoints
  `/schedule/day` (ADMIN), `/me/schedule` (aluno) e `/me/grid` (professor).
- **SPEC-004 (`billing`)** — ciclo assinatura → fatura → pagamento, com job
  diário (`@Scheduled` + ShedLock) que gera faturas e marca atrasos. A
  idempotência do job é uma constraint do banco, não uma checagem em código.
  O vencimento da mensalidade é o dia do mês em que o aluno se cadastrou, fixo:
  dar baixa não o move (regra substituída, ver SPEC-004).
- **SPEC-005 (`notification`)** — consome os seis eventos do catálogo e avisa o
  aluno por WhatsApp (Evolution API). Único módulo que fala com o mundo
  externo. `notification_log` com UNIQUE por (template, aluno, referência):
  a idempotência do at-least-once também vira constraint.
- **SPEC-006 (fundação do frontend)** — SPA com fluxo de sessão completo
  (login, refresh transparente, logout, recuperação ao recarregar) e shell de
  navegação por papel. Trouxe o `GET /auth/me` e tornou 401 — e não 403 — a
  resposta a requisição não autenticada em toda a API.
- **SPEC-007 (agenda do dia)** — a tela central do ADMIN: visão de um dia
  agrupada por hora e professor, com cancelar, remarcar e desfazer exceção.

Auto-registro e recuperação de senha seguem fora de escopo. Visão semanal,
drag-and-drop e criação de horário padrão pela tela ficaram para as próximas
specs.
