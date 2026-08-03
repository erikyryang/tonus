import { screen, waitFor, within } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Announcement, AnnouncementInput } from '@/features/scheduling/api/schedulingApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import { pageOf } from '@/test/pageOf';
import { studentsHandler } from '@/test/registrationHandlers';
import { renderApp } from '@/test/renderApp';
import {
  announcement,
  announcementsHandler,
  announcementsList,
  broadcastOk,
  createAnnouncementOk,
  deleteAnnouncementOk,
  problem,
} from '@/test/schedulingHandlers';
import { server } from '@/test/server';

/** GET /students?active=true&page=0 — só a contagem (totalElements) importa aqui. */
function activeStudentCount(total: number) {
  return studentsHandler(() => pageOf([], { totalElements: total }));
}

const INDEPENDENCE = announcement({
  id: 7,
  date: '2026-09-07',
  description: 'Independência',
  suspendsClasses: true,
});

const NOTICE = announcement({
  id: 8,
  date: '2026-09-10',
  description: 'Obra na rua',
  suspendsClasses: false,
});

describe('lista de comunicados', () => {
  it('mostra data, descrição e o efeito sobre as aulas', async () => {
    server.use(meOk(ADMIN), announcementsList(INDEPENDENCE, NOTICE));

    renderApp('/admin/announcements');

    const suspended = await screen.findByRole('row', { name: /Independência/ });
    expect(within(suspended).getByText('07/09/2026')).toBeInTheDocument();
    expect(within(suspended).getByText('Aulas suspensas')).toBeInTheDocument();

    const notice = await screen.findByRole('row', { name: /Obra na rua/ });
    expect(within(notice).getByText('Aulas normais')).toBeInTheDocument();
  });
});

describe('cadastro de comunicado', () => {
  /**
   * Critério de aceite: criar comunicado dispara WhatsApp para todos os alunos
   * ativos. O aviso é sobre o efeito real — não há como desavisar depois.
   */
  it('avisa sobre a notificação em massa e cria', async () => {
    const sent: AnnouncementInput[] = [];
    const announcements: Announcement[] = [];

    server.use(
      meOk(ADMIN),
      announcementsHandler(() => pageOf(announcements)),
      createAnnouncementOk((body) => {
        sent.push(body);
        announcements.push(announcement({ id: 80, ...body }));
      }),
    );

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Novo comunicado' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByText(/alunos ativos serão notificados por WhatsApp/i)).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Data'), '2026-09-07');
    await user.type(within(dialog).getByLabelText('Descrição'), 'Independência');
    await user.click(within(dialog).getByLabelText(/suspende as aulas/i));
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(sent).toEqual([{ date: '2026-09-07', description: 'Independência', suspendsClasses: true }]),
    );
    expect(await screen.findByRole('row', { name: /Independência/ })).toBeInTheDocument();
  });

  /**
   * Sem marcar a caixa, o comunicado só avisa: é o default porque suprimir o
   * dia é o efeito caro, e efeito caro não acontece por omissão.
   */
  it('cria comunicado que não suspende quando a caixa fica desmarcada', async () => {
    const sent: AnnouncementInput[] = [];
    server.use(meOk(ADMIN), announcementsList(), createAnnouncementOk((body) => sent.push(body)));

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Novo comunicado' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Data'), '2026-09-10');
    await user.type(within(dialog).getByLabelText('Descrição'), 'Obra na rua');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(sent).toEqual([{ date: '2026-09-10', description: 'Obra na rua', suspendsClasses: false }]),
    );
  });

  /** O texto do aviso muda com a caixa: são duas mensagens diferentes. */
  it('conta que a mensagem depende de suspender ou não', async () => {
    server.use(meOk(ADMIN), announcementsList());

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Novo comunicado' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByText(/as aulas acontecem normalmente/i)).toBeInTheDocument();

    await user.click(within(dialog).getByLabelText(/suspende as aulas/i));

    expect(within(dialog).getByText(/não haverá aula/i)).toBeInTheDocument();
  });
});

/**
 * Disparo manual (mudança 9): mensagem livre para todo aluno ativo, fora do
 * ciclo de comunicado de dia/efeito. "Enviar para N alunos ativos?" vem de
 * GET /students?active=true — o back não tem endpoint de contagem próprio.
 */
describe('disparo manual', () => {
  it('busca quantos alunos ativos existem e envia a mensagem', async () => {
    const sent: string[] = [];
    server.use(
      meOk(ADMIN),
      announcementsList(),
      activeStudentCount(42),
      broadcastOk((message) => sent.push(message)),
    );

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Enviar mensagem' }));
    const dialog = await screen.findByRole('dialog');

    expect(await within(dialog).findByText('Enviar para 42 alunos ativos?')).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Mensagem'), 'Aula de sábado cancelada.');
    await user.click(within(dialog).getByRole('button', { name: 'Enviar' }));

    await waitFor(() => expect(sent).toEqual(['Aula de sábado cancelada.']));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent(/mensagem enviada.*avisados por whatsapp/i),
    );
  });

  it('conta os caracteres digitados', async () => {
    server.use(meOk(ADMIN), announcementsList(), activeStudentCount(1));

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Enviar mensagem' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByText('0/500')).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Mensagem'), 'Aviso');

    expect(within(dialog).getByText('5/500')).toBeInTheDocument();
  });

  /** Critério de aceite: o zod barra antes de qualquer request. */
  it('recusa mensagem vazia sem chamar a API', async () => {
    server.use(meOk(ADMIN), announcementsList(), activeStudentCount(1));

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Enviar mensagem' }));
    const dialog = await screen.findByRole('dialog');

    // Sem handler de POST: se a chamada saísse, o MSW derrubaria o teste.
    await user.click(within(dialog).getByRole('button', { name: 'Enviar' }));

    expect(await within(dialog).findByText(/escreva a mensagem/i)).toBeInTheDocument();
  });

  it('recusa mensagem acima de 500 caracteres sem chamar a API', async () => {
    server.use(meOk(ADMIN), announcementsList(), activeStudentCount(1));

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Enviar mensagem' }));
    const dialog = await screen.findByRole('dialog');

    const textarea = within(dialog).getByLabelText('Mensagem') as HTMLTextAreaElement;
    // maxLength no textarea já barra a digitação; a validação real do zod é o
    // que impede o envio caso o limite chegue por outro caminho (colar, por
    // exemplo) — daí definir o value direto, ignorando o maxLength do DOM.
    await user.click(textarea);
    textarea.value = 'x'.repeat(501);
    await user.type(textarea, ' ');

    await user.click(within(dialog).getByRole('button', { name: 'Enviar' }));

    expect(await within(dialog).findByText(/no máximo 500 caracteres/i)).toBeInTheDocument();
  });

  /** "Ainda não sei" não pode virar "0 alunos" nem liberar o envio. */
  it('não libera o envio enquanto a contagem não responde', async () => {
    server.use(
      meOk(ADMIN),
      announcementsList(),
      http.get('/api/students', async () => {
        await new Promise(() => {});
      }),
    );

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Enviar mensagem' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByText(/contando os alunos ativos/i)).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Mensagem'), 'Aviso');
    expect(within(dialog).getByRole('button', { name: 'Enviar' })).toBeDisabled();
  });

  /** Sem o número, o envio fica bloqueado — e a falha se explica, com "tentar de novo". */
  it('explica quando não consegue contar os alunos ativos e refaz a consulta', async () => {
    let calls = 0;
    server.use(
      meOk(ADMIN),
      announcementsList(),
      http.get('/api/students', () => {
        calls += 1;
        return calls === 1 ? HttpResponse.error() : HttpResponse.json(pageOf([], { totalElements: 3 }));
      }),
    );

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Enviar mensagem' }));
    const dialog = await screen.findByRole('dialog');

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(/quantos alunos ativos/i);
    expect(within(dialog).getByRole('button', { name: 'Enviar' })).toBeDisabled();

    await user.click(within(dialog).getByRole('button', { name: 'Tentar de novo' }));

    expect(await within(dialog).findByText('Enviar para 3 alunos ativos?')).toBeInTheDocument();
  });
});

describe('comunicado já cadastrado', () => {
  /** O back é a autoridade sobre a duplicata; a tela repete a razão dele. */
  it('mostra a mensagem do 409 sem fechar o formulário', async () => {
    server.use(
      meOk(ADMIN),
      announcementsList(),
      http.post('/api/announcements', () => problem(409, 'Conflict', 'Já existe comunicado em 2026-09-07')),
    );

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Novo comunicado' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Data'), '2026-09-07');
    await user.type(within(dialog).getByLabelText('Descrição'), 'Independência');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    expect(await within(dialog).findByText('Já existe comunicado em 2026-09-07')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Salvar' })).toBeEnabled();
  });
});

describe('exclusão de comunicado', () => {
  it('pede confirmação antes de excluir', async () => {
    const deleted: number[] = [];
    server.use(meOk(ADMIN), announcementsList(INDEPENDENCE), deleteAnnouncementOk((id) => deleted.push(id)));

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Ações de Independência' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Excluir' }));

    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Excluir' }));

    await waitFor(() => expect(deleted).toEqual([7]));
  });

  /**
   * Excluir um comunicado que suspendia devolve as aulas do dia; o que não
   * suspendia nunca as tirou, e prometer o contrário confundiria o ADMIN.
   */
  it('só promete a volta das aulas quando elas estavam suspensas', async () => {
    server.use(meOk(ADMIN), announcementsList(NOTICE), deleteAnnouncementOk());

    const { user } = renderApp('/admin/announcements');

    await user.click(await screen.findByRole('button', { name: 'Ações de Obra na rua' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Excluir' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).queryByText(/voltam para a agenda/i)).not.toBeInTheDocument();
    expect(within(dialog).getByText(/quem já foi avisado/i)).toBeInTheDocument();
  });
});
