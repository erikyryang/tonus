- All Java code, pom.xml, migrations, tests and docker-compose:
  inside backend/. Never at the root.
- Relative paths in this document referring to code
  assume backend/ as the base (e.g. src/main/resources/... =
  backend/src/main/resources/...).
- Backend-specific conventions that come up later can go in a
  backend/CLAUDE.md of its own; this one is the general context.

## Sources of truth (read before implementing)
- docs/design-summary.md — consolidated architecture decisions
- docs/ddl.md — tables and constraints
- docs/eventos.md — event catalog (contract between modules)
- docs/specs/SPEC-00X-*.md — spec of the feature under development
Rule: spec and code change in the same PR.

## Stack
Java 25 (LTS), Spring Boot 4 (Web MVC, Data JPA, Security), Spring Modulith,
PostgreSQL, Flyway, Testcontainers, ShedLock, Maven.

## Architecture
- Modular monolith. Packages:
  br.com.tonus.{shared, auth, registration, scheduling, billing, notification}
- Root of each module: ONLY facade + published events (the contract).
  Everything else in internal/ — subfolders web/ (controllers + REST DTOs)
  and domain/ (entities + rules); services and repositories loose.
- Allowed dependencies: scheduling→registration, billing→registration,
  auth→registration, notification→{registration, scheduling, billing},
  all→shared. NOTHING beyond that.
  No cycles — registration cannot know auth, that would close a cycle.
  notification points to scheduling and billing because it consumes their
  events, and listening to an event is referencing the record that the
  publisher exposes at the root — a compile dependency like any other. The
  runtime coupling stays asynchronous and one-way. See docs/design-summary.md.
  Declared in @ApplicationModule(allowedDependencies) in the package-info.java
  of each module and verified by ApplicationModules.verify() in a test. Without
  the declaration the list is not checked by anything: a new edge requires
  editing the package-info, and that is what forces the decision to be
  deliberate.
- Communication between modules: query = synchronous call via facade;
  side effect = event via @ApplicationModuleListener.
- Event delivery is at-least-once: every listener is idempotent. The
  persisted registry (spring-modulith-starter-jpa, event_publication table) is
  what makes that true — along with who reads it: without the shared jobs
  (EventRepublicationJob and EventPublicationCleanupJob) the pending item stays
  recorded and nobody redelivers it.
- @ApplicationModuleListener is ASYNCHRONOUS — Modulith enables @Async
  support on its own, without @EnableAsync. In tests, wait for the effect
  (Eventually helper); asserting right after the HTTP response is a race.

## Naming (project standard)
- Every code identifier is in English: packages, classes, methods,
  variables, DTOs, events, files and directories.
- This also applies to the database: table, column, constraint and
  migration file names are in English. No Portuguese in `db/migration`.
- Module names in Portuguese used in this document and in the specs (cadastro,
  agenda, cobrança, notificação) are only domain labels for conversation;
  in code and schema always use the English equivalent:
  cadastro→registration, agenda→scheduling, cobrança→billing,
  notificação→notification, aluno→student, professor→teacher,
  usuário→user, auditoria→audit, fatura→invoice,
  horário padrão→default_schedule.
- Text aimed at the end user (error messages, WhatsApp, labels)
  may be in Portuguese — the rule is about identifiers, not about
  content shown to humans.
- Documentation in docs/*.md remains in Portuguese.

## Conventions
- A DTO is never a JPA entity. REST DTO (internal/web) ≠ facade record.
  Mapping via static method on the record (no MapStruct for now).
- Errors: RFC 9457 Problem Details via global handler in shared.
- STUDENT/TEACHER endpoints follow the /me/... pattern — id extracted from the
  token, NEVER from the path or query (prevents IDOR).
- Listings are always paginated.
- Migrations only via Flyway in db/migration,
  name V###__module_description.sql. Never ddl-auto.
- LocalDate.now()/LocalDateTime.now() is forbidden: inject the Clock bean.
- @ConfigurationProperties per module, with its own prefix
  (e.g. billing.days-overdue).
- Secrets only via environment variables (.env outside git,
  .env.example versioned).
- Never log the full phone number nor the content of a WhatsApp message.
- Changes to invoice.status and default_schedule generate an audit record.

## Tests
- Integration with Testcontainers (real Postgres). H2 is FORBIDDEN —
  database constraints are business rules and must be tested.
- @ApplicationModuleTest to test the module in isolation and its events.
- Test dates controlled via fixed Clock.

## Development flow (TDD with manual gate)
For each spec, the work has two mandatory, separate phases:

**RED phase (default — always starts here):**
- Write the integration tests for the acceptance criteria
  (Testcontainers + @ApplicationModuleTest when applicable).
- Run and confirm they fail for the right reason (nonexistent endpoint/class,
  not an accidental compile error nor a bug in the test).
  If they fail due to test infrastructure issues (Spring context does not
  start, duplicate bean, container timeout), fix that before
  proceeding — that does not count as the expected "red".
- Implement the minimum production code needed for the tests
  to actually pass — not just compile. "Minimum" = just enough
  to go green, without polishing, without handling cases the tests do not
  cover, without refactoring beyond what is necessary.
- Run again and confirm the tests pass.
- Present the result (tests + minimal implementation) and wait.

**IMPLEMENTA phase (only upon explicit authorization):**
- Only starts when the user says the word **IMPLEMENTA**.
- Goal: finish what was left missing beyond the minimum that passes
  the tests — uncovered edge cases, hardening, refactoring,
  any part of the spec that the RED minimum did not close.
- Without that word, even if the request seems to ask for the complete
  finalization of the feature, stay only on the minimum that passes the tests.

If it is not clear whether the request is "minimum to pass the tests" or
"finalize everything", ask before going beyond the minimum.

## Commands (run from backend/)
- Build + tests: cd backend && ./mvnw clean verify   (requires Docker running)
- Run locally: cd backend && docker compose up -d db && ./mvnw spring-boot:run
- New migration: create file in backend/src/main/resources/db/migration/
- Dev seed (default password for everyone, see README): cd backend &&
  docker compose exec -T db psql -U tonus -d tonus < scripts/seed-dev.sql
  It is a development tool, not a migration — do not version seed in
  db/migration, a known password applied in production is an open account.

## DON'T
- Do not add Kafka, Redis, cache, microservice or any
  infra dependency without discussing first.
- Do not import *.internal.* classes from another module.
- Do not expose a JPA entity in a controller, facade or event.
- Do not create an endpoint that accepts a studentId coming from the client
  for the logged-in student's own data.
- Do not call WhatsAppSender/EvolutionApiSender outside the notification
  module — communicate via events.
- Do not use H2, database mocks, or @MockBean of a repository in an
  integration test.
- Do not create code, pom.xml or configuration outside backend/
  (except docs/ and root files like .gitignore).
- Do not create the frontend/ folder nor any frontend code
  in this phase — backend only.
