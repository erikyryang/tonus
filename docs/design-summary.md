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
notification  → shared, registration
```

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
  limpo, `auth` ficaria puro. Custo alto agora: refatoraria o schema da SPEC-001
  e o caminho token→aluno que os endpoints `/me` vão usar. Continua sendo a
  melhor saída se `auth` um dia precisar existir sem `registration`.

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
- Refresh token rotacionado a cada uso; reuso de token já usado revoga a família
  inteira. É assim que se detecta token roubado.
- Endpoints de aluno/professor no padrão `/me/...`, com id vindo do token e
  nunca do path ou query — previne IDOR.
- Usuário desativado recebe a **mesma** resposta de senha errada. Distinguir os
  dois casos contaria a quem tenta que aquele login existe.
- LGPD por minimização: só nome, telefone e situação. Nada de CPF, endereço ou
  foto. Nunca logar telefone completo nem conteúdo de mensagem.

## Banco

Postgres, Flyway, `ddl-auto: validate` — nunca criação automática de schema.
Constraint é regra de negócio: `student.teacher_id NOT NULL` existe porque
"aluno sem professor" é proibido pelo domínio, e o teste bate na constraint
diretamente, não só na API.

Por isso teste de integração usa Postgres real via Testcontainers. **H2 é
proibido**: um banco que não tem as mesmas constraints não testa as regras.
