# Tonus

Sistema de gestão para escolas/academias com três perfis de usuário —
**admin**, **professor** e **aluno**. Cobre cadastro de pessoas, agenda de
aulas/horários, cobrança (faturas) e notificações via WhatsApp.

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

Dependências permitidas: `scheduling/billing/notification → registration`,
todos os módulos → `shared`. Nada além disso, sem ciclos. Comunicação
entre módulos é síncrona (fachada) para consultas, ou assíncrona
(eventos, at-least-once) para efeitos colaterais.

Decisões de arquitetura, contrato de eventos e DDL completo estão em
`docs/`; o comportamento esperado de cada feature está em
`docs/specs/SPEC-00X-*.md`.

## Stack

Java 21, Spring Boot 3 (Web MVC, Data JPA, Security), Spring Modulith,
PostgreSQL, Flyway, Testcontainers, JWT (jjwt), Bucket4j, Maven.

## Como rodar localmente

Pré-requisitos: Java 21, Docker rodando, e o wrapper do Maven (`./mvnw`,
já incluso no repo — não precisa instalar Maven).

```bash
cd backend
cp .env.example .env        # ajuste os valores se necessário
docker compose up -d db     # sobe o Postgres
./mvnw spring-boot:run      # sobe a aplicação em localhost:8080
```

## Como rodar os testes

Os testes são de integração, com Postgres real via Testcontainers —
**é obrigatório o Docker estar rodando**.

```bash
cd backend
./mvnw clean verify
```

## Estrutura do repositório

```
backend/    # todo o código Java, pom.xml, migrations, testes, docker-compose
docs/       # specs de feature e decisões de arquitetura (fonte de verdade)
CLAUDE.md   # convenções do projeto (arquitetura, nomenclatura, fluxo de dev)
```

## Fluxo de desenvolvimento

O projeto segue TDD com um gate manual: cada spec começa na fase **RED**
(testes de aceite + o mínimo de código para passarem) e só avança para a
fase **IMPLEMENTA** — que fecha casos de borda e finaliza o que sobrou —
mediante autorização explícita. Detalhes completos em `CLAUDE.md`.

## Status atual

- **SPEC-001 (fundação: `shared` + `auth`)** — implementada e testada:
  login/refresh/logout com JWT em cookie httpOnly `SameSite=Strict`,
  rotação de refresh token com detecção de reuso, rate limit no login,
  Problem Details para erros, e auditoria automática de entidades via
  listener JPA.
- Auto-registro, recuperação de senha e gestão de usuários ficam para a
  próxima spec (SPEC-002).
