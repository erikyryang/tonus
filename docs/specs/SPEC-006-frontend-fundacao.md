# SPEC-006 — Fundação do frontend

## Objetivo
Setup do SPA, fluxo de sessão completo (login, refresh, logout,
recuperação ao recarregar) e shell de navegação por papel. É a SPEC-001
do frontend: tudo que as telas de negócio vão assumir como dado.

## Regras
- Projeto em frontend/ com a stack e estrutura do frontend/CLAUDE.md.
  Proxy /api no Vite; nenhuma config de CORS no backend.
- **Toque no backend (única parte fora de frontend/):**
  `GET /auth/me` no módulo auth, autenticado, devolvendo
  { role, name, studentId?, teacherId? } a partir do token — sem consulta
  a registration; os claims já carregam os ids (design-summary). 401 se
  não autenticado. É o que permite ao front saber quem está logado sem
  ler cookie httpOnly.
    - `name` = `users.login`. O nome civil mora em registration, e consultá-lo
      a cada verificação de sessão acoplaria o caminho mais quente da API a
      outro módulo. Alternativas descartadas: coluna `name` em `users`
      (duplicaria o nome e abriria divergência) e claim `name` no token
      (congelaria o nome até novo login e obrigaria o refresh a rebuscar).
      Se um dia o nome civil precisar aparecer na tela, ele vem de um endpoint
      de registration, não daqui.
    - `role` e o vínculo saem do token, não do banco: é o token que autoriza
      as demais chamadas, e a tela tem de refletir exatamente o que a API vai
      aceitar. Só o login exige leitura, e da própria tabela do auth.
    - `studentId`/`teacherId` são omitidos do JSON quando nulos (ADMIN), em vez
      de virem `null`.
- **Não autenticado passa a ser 401 em TODA a API** (era 403, o padrão do
  Spring Security sem entry point próprio). 403 fica para quem está
  autenticado e não tem permissão. A distinção não é cosmética: o interceptor
  do front trata 401 como "sessão expirada, tente o refresh" — com 403 a
  sessão nunca se renovaria e o usuário cairia no login a cada expiração do
  access token. Mudança no `SecurityConfig` do auth; os testes de acesso
  anônimo de registration, scheduling e billing foram atualizados junto.
- **Sessão que não vale mais é 401 em `/auth/me`.** O access token é
  autocontido e sobrevive por até 10 min à revogação do acesso, então o
  endpoint confere que o usuário ainda existe e continua ativo. Limitação
  conhecida e aceita: os demais endpoints seguem aceitando esse token até ele
  expirar — fechar essa janela custaria uma consulta ao banco por requisição
  no filtro, e isso é decisão de arquitetura, não desta spec.
- Login (/login): formulário com zod; sucesso → GET /auth/me → redireciona
  por papel; credencial inválida → mensagem única, sem distinguir
  usuário inexistente de senha errada (mesma regra do back).
    - 429 (rate limit de 5 tentativas/min do back) tem mensagem própria: sem
      ela, quem errou a senha cinco vezes continuaria lendo "login ou senha
      inválidos" e tentaria de novo sem saber que está bloqueado.
    - Falhas que não são de credencial (503, rede) mostram o motivo, nunca a
      mensagem de credencial — erro de infraestrutura não pode se disfarçar
      de senha errada.
- Sessão no front = query ['auth','me'] do TanStack Query. Recarregar a
  página refaz /auth/me; 401 dispara o fluxo de refresh do CLAUDE.md.
    - Erro que **não** é 401 (500, API fora do ar) não derruba para o login:
      mostra tela de erro com "tentar de novo". Mandar para o login nesse caso
      encerraria uma sessão possivelmente válida e faria o usuário digitar a
      senha contra a mesma API que está fora do ar.
- Logout: POST /auth/logout → limpar TODO o cache do QueryClient →
  /login. Cache sujo de um usuário não pode vazar para o próximo login.
- Rotas protegidas por papel:
    - ADMIN → shell desktop com sidebar (Agenda, Alunos, Professores,
      Faturas, Comunicados — links podem apontar para placeholder).
    - STUDENT → shell mobile-first, destino /me/schedule (placeholder).
    - TEACHER → shell mobile-first, destino /me/grid (placeholder).
    - Papel sem acesso à rota → redirect para o destino do próprio papel,
      não página de erro.
- Interceptor de 401 no apiClient: uma tentativa de refresh, retry,
  falhou → limpar cache e /login. Requisições concorrentes com 401
  compartilham UMA promise de refresh (sem tempestade de refresh).

## Critérios de aceite (Vitest + RTL + MSW)
- Login inválido mostra a mensagem única; válido redireciona conforme
  o papel devolvido por /auth/me.
- Guard: STUDENT navegando para rota de ADMIN cai em /me/schedule.
- 401 numa query → refresh → retry transparente (MSW: 401, depois 200).
- Refresh falhando → cache limpo e redirect para /login.
- Reload com sessão válida restaura o usuário sem passar pelo /login.
- Backend: teste de integração de GET /auth/me nos três papéis
  (claims correspondem ao vínculo) e 401 anônimo.

## Validação visual (28/07/2026, Chrome)
Não há teste de aparência: o visual se valida no olho e a decisão fica aqui.
Rodado contra o backend real, atravessando o proxy do Vite.
- Login: card centralizado, foco visível no campo, autofill do navegador não
  quebra o layout.
- Shell ADMIN (1470px): sidebar com os cinco itens, item ativo destacado,
  login do usuário e "Sair" no header.
- Shell STUDENT (420px): header simples com nome e "Sair", sem sidebar, sem
  scroll horizontal.
- Guards com navegação real (page load, não só client-side): ADMIN em
  `/me/schedule` cai em `/admin/schedule`; STUDENT em `/admin/students` cai em
  `/me/schedule`; logout volta para `/login`; reload mantém a sessão.
- Console sem erro nem warning da aplicação.
- Rotação do refresh conferida por fora do navegador: `POST /auth/refresh`
  devolve token novo, e o token antigo reusado dá 401.

## Notas de execução
- O `Secure` dos cookies de sessão sai do transporte da requisição: em
  desenvolvimento (`http://localhost:5173` → proxy → API em HTTP) o atributo
  não aparece; atrás do Caddy, aparece. Não há passo de configuração local.

  Esta nota já disse o contrário — que o cookie era sempre `Secure` e que em
  Safari o login não persistia em dev, restando usar Chrome ou Firefox. O
  custo ficou claro depois: o sintoma não aponta para a causa. O
  `POST /auth/login` devolve 200, o `GET /auth/me` seguinte dá 401 e a tela
  mostra "Login ou senha inválidos" — o front traduz todo 401 do login assim
  (mensagem única, para não contar a quem tenta se o login existe). Quem
  esbarrasse nisso investigaria credencial, não cookie. "Use outro navegador"
  também não resolvia validar a UI no Safari, nem abrir o ambiente pelo IP da
  rede local para testar no celular — ali nenhum navegador abre exceção.
- Não há seed de usuário: para subir o ambiente pela primeira vez é preciso
  inserir um ADMIN direto no banco (o `POST /users` exige um ADMIN logado).

## Fora de escopo
Telas de negócio (SPEC-007+), PWA, dark mode, "lembrar de mim",
recuperação de senha, Playwright (avaliar quando houver fluxo completo
de negócio para cobrir).