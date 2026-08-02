import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Announcement, AnnouncementInput } from '@/features/scheduling/api/schedulingApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import { pageOf } from '@/test/pageOf';
import { renderApp } from '@/test/renderApp';
import {
  announcement,
  announcementsHandler,
  announcementsList,
  createAnnouncementOk,
  deleteAnnouncementOk,
  problem,
} from '@/test/schedulingHandlers';
import { server } from '@/test/server';

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
