import { act, screen, waitFor, within } from '@testing-library/react';
import { HttpResponse, delay, http } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { WhatsAppSession, WhatsAppSettingsInput } from '@/features/notification/api/notificationApi';
import { ADMIN, STUDENT, TEACHER, meOk } from '@/test/authHandlers';
import { problem } from '@/test/problem';
import { createTestQueryClient, renderApp } from '@/test/renderApp';
import { anyMyGridEmpty, anyMyScheduleEmpty } from '@/test/schedulingHandlers';
import { server } from '@/test/server';
import {
  IN_EFFECT_VERSION,
  QR_BASE64,
  createSessionHandler,
  createSessionOk,
  deleteSessionOk,
  instance,
  instancesList,
  instancesProblem,
  statusHandler,
  statusOk,
  statusProblem,
  updateSettingsOk,
  whatsappStatus,
} from '@/test/whatsappHandlers';

const MASKED_PHONE = '+55 11 9****-**77';

const connected = whatsappStatus({ state: 'CONNECTED', maskedPhone: MASKED_PHONE });

/** O seletor só faz sentido com a lista do gateway; é a mesma em quase todo teste. */
function gatewayWith(status = whatsappStatus()) {
  return [meOk(ADMIN), statusOk(status), instancesList(instance('tonus'), instance('Teste01'))];
}

describe('guard da tela de WhatsApp', () => {
  it('manda o STUDENT para a própria agenda', async () => {
    server.use(meOk(STUDENT), anyMyScheduleEmpty());

    renderApp('/admin/whatsapp');

    expect(await screen.findByRole('heading', { name: 'Minha agenda' })).toBeInTheDocument();
  });

  it('manda o TEACHER para a própria grade', async () => {
    server.use(meOk(TEACHER), anyMyGridEmpty());

    renderApp('/admin/whatsapp');

    expect(await screen.findByRole('heading', { name: 'Minha grade' })).toBeInTheDocument();
  });

  it('deixa o ADMIN entrar, com entrada na navegação', async () => {
    server.use(...gatewayWith());

    renderApp('/admin/whatsapp');

    expect(await screen.findByRole('heading', { name: 'WhatsApp' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'WhatsApp' })).toHaveAttribute('href', '/admin/whatsapp');
  });
});

describe('os cinco estados da tela de WhatsApp', () => {
  /** 1. Gateway não configurado: 503 do back, e nenhuma ação faz sentido. */
  it('explica que falta configuração de ambiente e não oferece ação', async () => {
    server.use(meOk(ADMIN), statusProblem(503, 'Service Unavailable', 'Gateway de WhatsApp não configurado.'));

    renderApp('/admin/whatsapp');

    expect(await screen.findByText(/não está configurado/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /parear/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /desconectar/i })).not.toBeInTheDocument();
  });

  /** 2. É o estado do ambiente hoje, e ele NÃO é erro. */
  it('trata instância inexistente como estado normal, com ação de criar', async () => {
    server.use(...gatewayWith(whatsappStatus({ state: 'INSTANCE_MISSING' })));

    renderApp('/admin/whatsapp');

    expect(await screen.findByRole('button', { name: 'Criar e parear' })).toBeInTheDocument();
    expect(screen.getByText(/ainda não existe no gateway/i)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  /** 3. Instância existe, sessão não. */
  it('mostra desconectada com ação de parear', async () => {
    server.use(...gatewayWith(whatsappStatus({ state: 'DISCONNECTED' })));

    renderApp('/admin/whatsapp');

    expect(await screen.findByText(/sem sessão/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Parear' })).toBeInTheDocument();
  });

  /** 4. Pareando. */
  it('mostra que está pareando quando o gateway está conectando', async () => {
    server.use(...gatewayWith(whatsappStatus({ state: 'CONNECTING' })));

    renderApp('/admin/whatsapp');

    expect(await screen.findByRole('heading', { name: 'Pareando' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  /** 5. Conectada: só o que a API mascarou aparece. */
  it('mostra o número mascarado e as ações de quem está conectado', async () => {
    server.use(...gatewayWith(connected));

    renderApp('/admin/whatsapp');

    expect(await screen.findByText(MASKED_PHONE)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Desconectar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Parear outro número' })).toBeInTheDocument();
    // Nada de número completo: a tela não tem de onde tirar um, e não pode inventar.
    expect(document.body.textContent).not.toMatch(/\+55 \d{2} \d{4,5}-?\d{4}/);
  });

  /** Enquanto o gateway não responde, a tela diz o que está fazendo. */
  it('diz que está consultando o gateway antes da resposta', async () => {
    server.use(
      meOk(ADMIN),
      http.get('/api/whatsapp/status', async () => {
        await delay('infinite');
        return HttpResponse.json(whatsappStatus());
      }),
    );

    renderApp('/admin/whatsapp');

    expect(await screen.findByText('Consultando o gateway…')).toBeInTheDocument();
    expect(screen.queryByLabelText(/versão desejada/i)).not.toBeInTheDocument();
  });

  /** 502 é gateway fora do ar, não tela branca. */
  it('renderiza gateway indisponível com opção de tentar de novo', async () => {
    server.use(meOk(ADMIN), statusProblem(502, 'Bad Gateway', 'Gateway de WhatsApp indisponível.'));

    renderApp('/admin/whatsapp');

    expect(await screen.findByRole('alert')).toHaveTextContent('Gateway de WhatsApp indisponível.');
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument();
  });
});

describe('pareamento por QR', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('mostra o QR que a API devolveu', async () => {
    server.use(...gatewayWith(), createSessionOk());

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Parear' }));

    const qr = await screen.findByRole('img', { name: /qr/i });
    expect(qr).toHaveAttribute('src', expect.stringContaining(QR_BASE64));
  });

  /**
   * Numa instância já conectada o POST devolve o estado e nenhum QR (SPEC-013):
   * abrir o painel aí seria mostrar um quadrado vazio e deixar o polling
   * girando por nada.
   */
  it('não abre painel de QR quando a resposta vem sem código', async () => {
    server.use(...gatewayWith(whatsappStatus({ state: 'CONNECTING' })), createSessionOk(null));

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Gerar QR' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Gerar QR' })).toBeEnabled());
    expect(screen.queryByRole('img', { name: /qr/i })).not.toBeInTheDocument();
  });

  /**
   * O mesmo caso com a chave ausente em vez de nula. O back manda `null`
   * explícito hoje, mas a normalização (`?? null`) é o que separa "sem QR" de
   * "painel de QR ligado para uma imagem que não existe" — e um teste que
   * segura essa distinção custa menos que descobri-la em produção.
   */
  it('tolera resposta sem a chave do QR', async () => {
    const connecting = whatsappStatus({ state: 'CONNECTING' });
    server.use(
      ...gatewayWith(connecting),
      createSessionHandler(() => ({ ...connecting }) as WhatsAppSession),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Gerar QR' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Gerar QR' })).toBeEnabled());
    expect(screen.queryByRole('img', { name: /qr/i })).not.toBeInTheDocument();
  });

  it('mostra o erro do gateway quando não consegue gerar o QR', async () => {
    server.use(
      ...gatewayWith(),
      http.post('/api/whatsapp/session', () => problem(502, 'Bad Gateway', 'Gateway de WhatsApp indisponível.')),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Parear' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Gateway de WhatsApp indisponível.');
    expect(screen.queryByRole('img', { name: /qr/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Parear' })).toBeEnabled();
  });

  /**
   * O pareamento termina no celular, não no navegador: sem o polling a tela
   * ficaria mostrando um QR já consumido. E ele para ao conectar — nunca fica
   * girando em aba esquecida.
   */
  it('refaz o status a cada 5s com o QR na tela e para ao conectar', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const statusCalls: number[] = [];
    let paired = false;
    server.use(
      meOk(ADMIN),
      instancesList(instance('tonus')),
      statusHandler(() => (paired ? connected : whatsappStatus({ state: 'DISCONNECTED' })), statusCalls),
      createSessionOk(),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Parear' }));
    await screen.findByRole('img', { name: /qr/i });

    const beforePolling = statusCalls.length;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_100);
    });
    expect(statusCalls.length).toBeGreaterThan(beforePolling);

    paired = true;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_100);
    });

    expect(await screen.findByText(MASKED_PHONE)).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /qr/i })).not.toBeInTheDocument();

    const afterConnecting = statusCalls.length;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(statusCalls.length).toBe(afterConnecting);
  });

  it('para de repetir o status ao fechar o QR', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const statusCalls: number[] = [];
    server.use(
      meOk(ADMIN),
      instancesList(instance('tonus')),
      statusHandler(() => whatsappStatus({ state: 'DISCONNECTED' }), statusCalls),
      createSessionOk(),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Parear' }));
    await screen.findByRole('img', { name: /qr/i });
    await user.click(screen.getByRole('button', { name: 'Fechar o QR' }));

    const afterClosing = statusCalls.length;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(statusCalls.length).toBe(afterClosing);
  });

  /**
   * QR vencido é indistinguível de um válido no olho. Deixá-lo na tela é
   * garantir que alguém leia um código morto e culpe o celular.
   */
  it('troca o QR expirado pela oferta de gerar outro, e para o polling', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const statusCalls: number[] = [];
    server.use(
      meOk(ADMIN),
      instancesList(instance('tonus')),
      statusHandler(() => whatsappStatus({ state: 'DISCONNECTED' }), statusCalls),
      createSessionOk(),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Parear' }));
    await screen.findByRole('img', { name: /qr/i });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_100);
    });

    expect(await screen.findByRole('heading', { name: 'O QR expirou' })).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /qr/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gerar outro QR' })).toBeInTheDocument();

    const afterExpiring = statusCalls.length;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(statusCalls.length).toBe(afterExpiring);
  });

  it('gera outro QR depois de o primeiro expirar', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    server.use(
      ...gatewayWith(),
      createSessionOk(),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Parear' }));
    await screen.findByRole('img', { name: /qr/i });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_100);
    });
    await screen.findByRole('heading', { name: 'O QR expirou' });

    await user.click(screen.getByRole('button', { name: 'Gerar outro QR' }));

    expect(await screen.findByRole('img', { name: /qr/i })).toBeInTheDocument();
  });
});

describe('desconectar', () => {
  it('não chama a API sem confirmação', async () => {
    let calls = 0;
    server.use(
      ...gatewayWith(connected),
      deleteSessionOk(() => {
        calls += 1;
      }),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Desconectar' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(/avisos/i);

    await user.click(within(dialog).getByRole('button', { name: 'Voltar' }));

    expect(calls).toBe(0);
  });

  /**
   * O cliente do teste tem staleTime infinito de propósito: com o padrão, toda
   * volta refaz a chamada de qualquer jeito e o teste passaria sem a
   * invalidação — verde que não prova nada.
   */
  it('desconecta ao confirmar e refaz o status', async () => {
    let calls = 0;
    let session = true;
    server.use(
      meOk(ADMIN),
      instancesList(instance('tonus')),
      statusHandler(() => (session ? connected : whatsappStatus({ state: 'DISCONNECTED' }))),
      deleteSessionOk(() => {
        calls += 1;
        session = false;
      }),
    );

    const { user } = renderApp('/admin/whatsapp', createTestQueryClient({ staleTime: Infinity, gcTime: Infinity }));

    await user.click(await screen.findByRole('button', { name: 'Desconectar' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Desconectar' }));

    await waitFor(() => expect(calls).toBe(1));
    expect(await screen.findByRole('button', { name: 'Parear' })).toBeInTheDocument();
    expect(screen.queryByText(MASKED_PHONE)).not.toBeInTheDocument();
  });

  /** Falhou: a confirmação continua aberta com o motivo, e a sessão segue de pé. */
  it('mantém a confirmação aberta quando o gateway recusa a desconexão', async () => {
    server.use(
      ...gatewayWith(connected),
      http.delete('/api/whatsapp/session', () => problem(502, 'Bad Gateway', 'Gateway de WhatsApp indisponível.')),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Desconectar' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Desconectar' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Gateway de WhatsApp indisponível.');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(MASKED_PHONE)).toBeInTheDocument();
  });

  /**
   * O gateway mantém um número por instância: parear outro é desconectar e
   * parear em seguida. Chamar o POST direto seria um botão que não faz nada —
   * numa instância conectada ele é idempotente e não devolve QR.
   */
  it('parear outro número confirma, desconecta e já pede o QR novo', async () => {
    let session = true;
    const posted: string[] = [];
    server.use(
      meOk(ADMIN),
      instancesList(instance('tonus')),
      statusHandler(() => (session ? connected : whatsappStatus({ state: 'DISCONNECTED' }))),
      deleteSessionOk(() => {
        session = false;
      }),
      createSessionHandler(
        () => ({ ...whatsappStatus({ state: 'CONNECTING' }), qrCode: QR_BASE64 }),
        () => posted.push('session'),
      ),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Parear outro número' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(/param de sair/i);
    expect(posted).toEqual([]);

    await user.click(within(dialog).getByRole('button', { name: 'Desconectar e parear' }));

    expect(await screen.findByRole('img', { name: /qr/i })).toBeInTheDocument();
    expect(posted).toEqual(['session']);
  });
});

describe('versão do WhatsApp', () => {
  it('mostra em vigor e desejada, e avisa da pendência ao salvar uma diferente', async () => {
    const sent: WhatsAppSettingsInput[] = [];
    let desired = IN_EFFECT_VERSION;
    server.use(
      meOk(ADMIN),
      instancesList(instance('tonus')),
      statusHandler(() =>
        whatsappStatus({
          phoneVersion: { inEffect: IN_EFFECT_VERSION, desired, pending: desired !== IN_EFFECT_VERSION },
        }),
      ),
      updateSettingsOk((body) => {
        sent.push(body);
        desired = body.desiredPhoneVersion;
      }),
    );

    const { user } = renderApp('/admin/whatsapp');

    expect(await screen.findByText(IN_EFFECT_VERSION)).toBeInTheDocument();
    expect(screen.queryByText(/só passa a valer/i)).not.toBeInTheDocument();

    const field = await screen.findByLabelText(/versão desejada/i);
    await user.clear(field);
    await user.type(field, '2.3000.1024000000');
    await user.click(screen.getByRole('button', { name: 'Salvar versão' }));

    await waitFor(() =>
      expect(sent).toEqual([{ instance: 'tonus', desiredPhoneVersion: '2.3000.1024000000' }]),
    );
    expect(await screen.findByText(/só passa a valer/i)).toBeInTheDocument();
  });

  it('não avisa de pendência quando a desejada é igual à em vigor', async () => {
    server.use(...gatewayWith(), updateSettingsOk());

    const { user } = renderApp('/admin/whatsapp');

    const field = await screen.findByLabelText(/versão desejada/i);
    await user.clear(field);
    await user.type(field, IN_EFFECT_VERSION);
    await user.click(screen.getByRole('button', { name: 'Salvar versão' }));

    expect(await screen.findByText('Configuração salva.')).toBeInTheDocument();
    expect(screen.queryByText(/só passa a valer/i)).not.toBeInTheDocument();
  });

  it('barra formato inválido no formulário, sem chamada de rede', async () => {
    let called = false;
    server.use(
      ...gatewayWith(),
      http.put('/api/whatsapp/settings', () => {
        called = true;
        return problem(400, 'Bad Request', 'não deveria ter sido chamado');
      }),
    );

    const { user } = renderApp('/admin/whatsapp');

    const field = await screen.findByLabelText(/versão desejada/i);
    await user.clear(field);
    await user.type(field, '3.0');
    await user.click(screen.getByRole('button', { name: 'Salvar versão' }));

    expect(await screen.findByText(/2\.x\.y/i)).toBeInTheDocument();
    expect(called).toBe(false);
  });

  /** O limite de tamanho é do back (40 caracteres); o campo o repete para o 400 não ser a primeira notícia. */
  it('barra versão longa demais no formulário, sem chamada de rede', async () => {
    let called = false;
    server.use(
      ...gatewayWith(),
      http.put('/api/whatsapp/settings', () => {
        called = true;
        return problem(400, 'Bad Request', 'não deveria ter sido chamado');
      }),
    );

    const { user } = renderApp('/admin/whatsapp');

    const field = await screen.findByLabelText(/versão desejada/i);
    await user.clear(field);
    await user.type(field, `2.3000.${'1'.repeat(40)}`);
    await user.click(screen.getByRole('button', { name: 'Salvar versão' }));

    expect(await screen.findByText(/no máximo 40 caracteres/i)).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('mostra o erro do back sem perder o que foi digitado', async () => {
    server.use(
      ...gatewayWith(),
      http.put('/api/whatsapp/settings', () => problem(400, 'Bad Request', 'Versão inválida.')),
    );

    const { user } = renderApp('/admin/whatsapp');

    const field = await screen.findByLabelText(/versão desejada/i);
    await user.clear(field);
    await user.type(field, '2.9999.1');
    await user.click(screen.getByRole('button', { name: 'Salvar versão' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Versão inválida.');
    expect(screen.getByLabelText(/versão desejada/i)).toHaveValue('2.9999.1');
  });

  /**
   * O status é refeito a cada 5s com o QR na tela, e a resposta muda enquanto
   * se digita (o gateway passa a CONNECTING). Se o formulário se resetasse a
   * cada objeto novo de status, o campo se apagaria sozinho no meio da frase —
   * por isso o reset olha os valores gravados, e não a resposta inteira.
   */
  it('não apaga o que está sendo digitado quando o polling refaz o status', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const statusCalls: number[] = [];
    let connecting = false;
    server.use(
      meOk(ADMIN),
      instancesList(instance('tonus')),
      statusHandler(
        () => whatsappStatus({ state: connecting ? 'CONNECTING' : 'DISCONNECTED' }),
        statusCalls,
      ),
      createSessionOk(),
    );

    const { user } = renderApp('/admin/whatsapp');

    await user.click(await screen.findByRole('button', { name: 'Parear' }));
    await screen.findByRole('img', { name: /qr/i });

    const field = screen.getByLabelText(/versão desejada/i);
    await user.clear(field);
    await user.type(field, '2.3000.1024000000');

    connecting = true;
    const before = statusCalls.length;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_100);
    });
    expect(statusCalls.length).toBeGreaterThan(before);

    expect(screen.getByLabelText(/versão desejada/i)).toHaveValue('2.3000.1024000000');

    vi.useRealTimers();
  });
});

describe('instância do gateway', () => {
  /**
   * Trocar a instância com sessão de pé não é inócuo: as mensagens passam a
   * sair por outro lugar. A confirmação diz para onde.
   */
  it('pede confirmação nomeando o destino antes de trocar a instância conectada', async () => {
    const sent: WhatsAppSettingsInput[] = [];
    server.use(...gatewayWith(connected), updateSettingsOk((body) => void sent.push(body)));

    const { user } = renderApp('/admin/whatsapp');

    await user.selectOptions(await screen.findByLabelText('Instância'), 'Teste01');
    await user.click(screen.getByRole('button', { name: 'Salvar versão' }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('Teste01');
    expect(sent).toEqual([]);

    await user.click(within(dialog).getByRole('button', { name: 'Trocar a instância' }));

    await waitFor(() =>
      expect(sent).toEqual([{ instance: 'Teste01', desiredPhoneVersion: IN_EFFECT_VERSION }]),
    );
  });

  it('não pede confirmação quando só a versão muda', async () => {
    const sent: WhatsAppSettingsInput[] = [];
    server.use(...gatewayWith(connected), updateSettingsOk((body) => void sent.push(body)));

    const { user } = renderApp('/admin/whatsapp');

    const field = await screen.findByLabelText(/versão desejada/i);
    await user.clear(field);
    await user.type(field, '2.3000.1024000000');
    await user.click(screen.getByRole('button', { name: 'Salvar versão' }));

    await waitFor(() =>
      expect(sent).toEqual([{ instance: 'tonus', desiredPhoneVersion: '2.3000.1024000000' }]),
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('avisa quando não consegue carregar as instâncias, sem perder a configurada', async () => {
    server.use(
      meOk(ADMIN),
      statusOk(),
      instancesProblem(502, 'Bad Gateway', 'Gateway de WhatsApp indisponível.'),
    );

    renderApp('/admin/whatsapp');

    expect(await screen.findByRole('alert')).toHaveTextContent(/não foi possível carregar as instâncias/i);
    expect(screen.getByRole('option', { name: 'tonus' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument();
  });

  it('diz que o gateway não tem instância nenhuma quando a lista volta vazia', async () => {
    server.use(meOk(ADMIN), statusOk(whatsappStatus({ state: 'INSTANCE_MISSING' })), instancesList());

    renderApp('/admin/whatsapp');

    expect(await screen.findByText(/ainda não tem nenhuma instância criada/i)).toBeInTheDocument();
  });
});
