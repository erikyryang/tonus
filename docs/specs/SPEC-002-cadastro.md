# SPEC-002 — Cadastro

## Objetivo
CRUD de alunos e professores + vínculo fixo aluno→professor.

## Regras
- `professor`: nome, telefone, ativo. `aluno`: nome, telefone,
  professor_id (FK obrigatória), ativo.
- Saída do estúdio = desativação (ativo=false). DELETE físico não existe
  (preserva histórico de fatura).
- LGPD: coletar somente esses campos (minimização). Nada de CPF/endereço.
- Admin pode criar o usuário de acesso (role ALUNO/PROFESSOR) do aluno/professor.
  O endpoint é `POST /users`, no módulo auth — e não no cadastro — porque o auth
  é dono da tabela `users`. São duas chamadas do admin: cria o cadastro, depois
  o usuário, ligando os dois por studentId/teacherId.
- O vínculo é validado contra o cadastro (via `RegistrationFacade`) e role e id
  têm que concordar: STUDENT exige studentId, TEACHER exige teacherId, ADMIN não
  aceita vínculo. Qualquer combinação fora disso → 400.
- O vínculo tem que apontar para alguém **ativo**: criar login para quem já saiu
  do estúdio → 400. A recusa é sobre a situação atual — quem foi reativado pode
  ter login normalmente.
- Desativar aluno/professor revoga o acesso do usuário vinculado: o cadastro
  publica `StudentDeactivated`/`TeacherDeactivated` e o auth marca
  `users.active=false` e invalida os refresh tokens vivos. Bloquear só o login
  novo não bastaria — um refresh token vivo renovaria o acesso por 7 dias.
- Reativar o aluno **não** devolve o login: isso exige
  `PATCH /users/{id}/activate` do ADMIN. Revogar é automático, conceder é manual.
- Endpoints (ADMIN): CRUD /students e /teachers, listagem paginada.
  Desativação é `PATCH /{recurso}/{id}/deactivate` e a volta ao estúdio é
  `PATCH /{recurso}/{id}/activate` (mesmo id, histórico preservado);
  não existe DELETE.
- Professor inativo não recebe aluno novo nem remanejamento: `teacherId` de
  professor desativado → 400. Alunos que já eram dele continuam editáveis, e
  reativar aluno cujo professor saiu → 400 (remanejar antes).
- Listagens aceitam `?active=true|false`; sem o parâmetro, listam todos.
  Ordenação padrão por nome, sempre com `id` como desempate — sem ORDER BY
  total a paginação do Postgres pode repetir ou omitir linhas entre páginas.
  Teto de página: 100.
- Telefone em E.164 (`+5511999990000`); nome até 255 caracteres. Os limites
  espelham as colunas para que entrada inválida vire 400, não 500.
- `RegistrationFacade` expõe: telefone/nome por alunoId, lista de alunos ativos,
  alunos por professor.

## Critérios de aceite
- Criar aluno sem professor → 400 (validado na API e por NOT NULL + FK no banco).
- Desativar aluno não afeta faturas existentes.
- Listagens paginadas desde o dia 1.

## Fora de escopo
Importação em massa, foto, campos extras, troca de professor em lote.
Auditoria de mudanças de cadastro: por decisão de arquitetura, só
`invoice.status` e `default_schedule` geram registro em `audit_log`.

## Pendências conhecidas
- O critério "desativar aluno não afeta faturas existentes" só é testável de
  ponta a ponta quando billing existir. Hoje o teste cobre o invariante do qual
  ele depende: desativar é UPDATE, a linha e o vínculo sobrevivem, não há DELETE.
- Anônimo em rota autenticada recebe 403, não 401 — comportamento do entry point
  padrão do Spring Security. Se 401 for o desejado, a mudança é no SecurityConfig
  (auth/SPEC-001) e vale para toda a API.
