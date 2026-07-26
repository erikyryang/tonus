# SPEC-001 — Fundação (shared + auth)

## Objetivo
Base técnica transversal e autenticação/autorização dos 3 papéis.

## Regras
- `shared`: handler global de erros (RFC 9457 Problem Details), config Jackson,
  `Clock` como bean, `BaseEntity` (id, created_at, updated_at),
  tabela `audit_log` + listener JPA para popular.
- `auth`: tabela `users` (login UNIQUE, password BCrypt, role ADMIN/TEACHER/STUDENT,
  student_id/teacher_id nullable) e `refresh_token` (token_hash, family,
  expires_at, used).
- JWT em cookie httpOnly + SameSite=Strict. Access token 10–15 min.
- Refresh token 7 dias, rotacionado a cada uso. Reuso de refresh já usado
  → revogar a família inteira + 401.
- Rate limit no POST /auth/login (Bucket4j).
- Endpoints: POST /auth/login, POST /auth/refresh, POST /auth/logout.

## Critérios de aceite
- Login válido seta cookies; token NUNCA aparece no body da resposta.
- Refresh reusado retorna 401 e invalida todos os tokens da família.
- Cada role acessa só o que pode (teste dos 3 perfis).
- Erro de validação retorna Problem Details, não stacktrace.

## Fora de escopo
Auto-registro, recuperação de senha, gestão de usuários (entra na SPEC-002).