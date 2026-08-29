# CLAUDE.md — frontend (Tônus)

## Stack
Vite + React 18 + TypeScript (strict). React Router. TanStack Query
(estado de servidor). react-hook-form + zod (formulários). shadcn/ui +
Tailwind (UI). Vitest + React Testing Library + MSW (testes).

## Estrutura
src/
├── app/          → router, providers (QueryClient), shell de navegação
├── features/     → um diretório por módulo de domínio, espelhando o back:
│   ├── auth/         (login, sessão, guards)
│   ├── registration/ (alunos, professores)
│   ├── scheduling/   (agenda, exceções, feriados)
│   └── billing/      (assinaturas, faturas)
│       cada feature: api/ (chamadas + tipos), components/, pages/
├── components/   → ui/ (shadcn) e compartilhados de verdade
└── lib/          → apiClient, utils, formatadores (data/dinheiro pt-BR)

## Auth e rede — regras duras
- Sessão vive em cookies httpOnly. JS NUNCA lê, grava ou anexa token.
  Não existe Authorization header, localStorage ou sessionStorage de auth.
- Única exceção ao "JS nunca lê cookie": o CSRF. O backend exige o cookie
  XSRF-TOKEN (não-httpOnly, deliberadamente) ecoado como header X-XSRF-TOKEN
  em todo método não-GET (POST/PUT/PATCH/DELETE), inclusive no /auth/refresh.
  É o padrão double-submit: o valor não é segredo; o que o protege é o
  same-origin. O eco acontece SÓ no apiClient (lib/), nunca em componente.
- Toda chamada usa o apiClient de lib/ (fetch com credentials: 'include',
  baseURL relativa /api). Proibido fetch/axios solto em componente.
- Front e API são SAME-ORIGIN por decisão de arquitetura:
    - dev: proxy do Vite encaminha /api → http://localhost:8080
    - prod: Caddy serve o estático e faz reverse proxy de /api
      Não configurar CORS no backend; se parecer necessário, a topologia
      está errada — discutir antes.
- 401 em qualquer chamada → uma única tentativa de POST /auth/refresh
  → retry da original; falhou de novo → limpar caches do Query e
  redirecionar para /login. Nunca loop de refresh.
- Quem o usuário é vem de GET /auth/me (role, nome, studentId/teacherId).
  Nunca decodificar JWT no front.

## Convenções
- Identificadores em inglês (regra da raiz); texto visível ao usuário
  em pt-BR; datas dd/MM/yyyy e dinheiro R$ via formatadores de lib/.
- Tipos de api/ espelham os DTOs do backend manualmente — mudança de
  contrato muda os dois lados no mesmo PR.
- Componentes: função + hooks; sem class components; sem Redux/Zustand —
  estado de servidor é do TanStack Query, estado local é useState.
- Erros de API (Problem Details) têm tratamento padrão no apiClient;
  formulário mostra a mensagem do campo quando o back devolver.
- Query keys por feature: ['scheduling','day',date] — invalidação
  explícita após mutação.

## Testes
- Vitest + RTL para comportamento: formulário valida, guard bloqueia,
  erro renderiza, mutação invalida query. MSW mocka a API na fronteira
  HTTP — proibido mockar o TanStack Query ou o apiClient por dentro.
- Sem teste de aparência/snapshot de pixel: visual se valida no olho e
  a decisão vira registro na spec.
- O gate RED/IMPLEMENTA da raiz vale aqui com esta tradução:
  RED = testes de comportamento (Vitest/RTL/MSW) falhando pelo motivo
  certo + mínimo para passar. IMPLEMENTA = finalização (bordas,
  estados de loading/vazio, polimento).

## Comandos (a partir de frontend/)
- npm run dev — sobe com proxy para o back local
- npm run test — Vitest em watch; npm run test:run no CI
- npm run build && npm run preview — build de produção

## NÃO FAÇA
- Não guardar nada de auth em localStorage/sessionStorage.
- Não adicionar biblioteca de UI, estado ou CSS sem discutir antes.
- Não criar rota que receba studentId/teacherId para dados do próprio
  usuário — o back resolve pelo token (/me/...).
- Não duplicar no front regra de negócio do back além de validação de
  formulário — o back é a autoridade; o front reflete erros.
- Não usar dados mockados fora de teste (MSW é só teste).