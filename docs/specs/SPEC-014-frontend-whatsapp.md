# SPEC-014 — Tela de WhatsApp do ADMIN (frontend)

## Objetivo
Uma tela em `/admin/whatsapp` onde quem administra o estúdio vê se o WhatsApp
está conectado, pareia um número lendo o QR ali mesmo, desconecta, e ajusta a
versão do gateway. Consome a API da SPEC-013 — as duas mudam no mesmo PR
quando o contrato mudar.

O que se substitui é concreto: hoje parear exige abrir o Manager da Evolution
em `localhost:8081`, autenticar com a `EVOLUTION_API_KEY` do `.env` e operar
uma interface de terceiro que expõe *todas* as instâncias e todas as
operações destrutivas. Quem administra o estúdio não deveria precisar da chave
do gateway para trocar o número do WhatsApp.

## Regras

### Onde mora
- Rota `/admin/whatsapp`, dentro do `AdminShell`, sob o
  `<RequireRole allow={['ADMIN']} />` que já guarda o bloco administrativo.
  Entrada nova na navegação lateral.
- Feature `src/features/notification/`, espelhando o módulo do backend como
  manda a convenção — `api/`, `components/`, `pages/`. Não cabe em `billing`
  nem em `scheduling`.
- Tipos em `api/` espelham os DTOs da SPEC-013 **à mão**, como o resto do
  front. Sem geração de cliente.
- Toda chamada pelo `apiClient` de `lib/`. Nada de `fetch` solto — e nada de
  falar com `localhost:8081`: o front conversa com a API do Tônus, que é quem
  conhece a Evolution.

### Os cinco estados da tela
A tela é uma máquina de estados, não um formulário com um botão. Renderizar
qualquer um deles como erro genérico é o jeito de a tela não servir para nada:

1. **Gateway não configurado** (503 da API) — explica que falta configuração
   de ambiente e não oferece ação nenhuma. Não é erro do usuário e não
   adianta ele clicar em nada.
2. **Instância não existe no gateway** — estado normal, não erro. Ação:
   criar e parear. É o estado em que o ambiente está hoje.
3. **Desconectada** — a instância existe, sem sessão. Ação: parear.
4. **Pareando** — QR na tela.
5. **Conectada** — número mascarado como veio da API. Ações: desconectar,
   ou parear outro número.

### QR e polling
- O QR chega em base64 e vai direto para `<img>`. **Não é persistido em
  lugar nenhum** — nem `localStorage` (proibido para auth e igualmente
  errado aqui), nem cache do Query além da vida da tela.
- Enquanto o QR está visível, o status é refeito a cada 5s: pareamento
  termina no celular, não no navegador, e sem polling a tela ficaria mostrando
  um QR já consumido. O polling **para** ao conectar, ao sair da tela e ao
  fechar o QR — nunca fica girando em aba esquecida.
- O QR expira (`QRCODE_LIMIT` no gateway). Expirado, a tela oferece gerar
  outro em vez de deixar um quadrado morto na tela.

### Ações destrutivas
- **Desconectar pede confirmação**, nomeando a consequência: os avisos param
  de sair até alguém parear de novo. É a única ação da tela que interrompe o
  serviço para os alunos.
- Trocar a instância no seletor também para de ser inócuo quando há sessão
  ativa — a confirmação diz para onde as mensagens passam a ir.

### Versão
- Formulário com react-hook-form + zod, validando `2.\d+.\d+` no cliente; o
  back é a autoridade e a tela reflete o erro dele.
- Mostra **em vigor** e **desejada** lado a lado, e o aviso de pendência
  enquanto diferirem — sem isso, o ADMIN salva, vê o valor novo na tela e
  conclui que aplicou, quando o gateway continua com o antigo até ser
  recriado.
- O texto de ajuda diz o que fazer quando o QR não conecta: é o sintoma pelo
  qual alguém chega nesse campo, e a tela é onde a pergunta aparece.

### Rede e cache
- Query keys `['notification','whatsapp','status']` e
  `['notification','whatsapp','instances']`; toda mutação invalida
  explicitamente o status.
- Erros de Problem Details caem no tratamento padrão do `apiClient`; 502 vira
  mensagem de gateway indisponível com opção de tentar de novo, não tela
  branca.
- Texto visível em pt-BR, identificadores em inglês.

## Critérios de aceite
- STUDENT e TEACHER que abrem `/admin/whatsapp` são redirecionados pelo guard;
  ADMIN entra.
- Cada um dos cinco estados renderiza seu próprio conteúdo, com MSW
  devolvendo a resposta correspondente — inclusive "instância não existe",
  que **não** pode aparecer como erro.
- Com QR na tela, o status é refeito; quando a resposta vira conectada, o QR
  some, o número mascarado aparece e **o polling para**.
- Desconectar sem confirmar não chama a API; confirmando, chama e invalida o
  status.
- Salvar versão diferente da em vigor mostra o aviso de pendência; igual, não
  mostra.
- Versão em formato inválido é barrada no formulário, sem chamada de rede.
- 502 da API renderiza mensagem de gateway indisponível, não tela branca.
- O número completo não aparece em lugar nenhum da tela — só o que a API
  mascarou.
- Testes com Vitest + RTL + MSW, mockando na fronteira HTTP. Proibido mockar
  o TanStack Query ou o `apiClient` por dentro, e proibido dado mockado fora
  de teste.

## Fora de escopo
Histórico de mensagens enviadas; reiniciar ou apagar instância; administrar
mais de uma instância; qualquer tela que exponha a `api-key` do gateway;
substituir o Manager da Evolution para operações que esta spec não cobre.
