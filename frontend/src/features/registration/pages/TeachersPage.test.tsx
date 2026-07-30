import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Teacher, TeacherInput } from '@/features/registration/api/registrationApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import { pageOf } from '@/test/pageOf';
import {
  createTeacherOk,
  deactivateTeacherOk,
  teacher,
  teachersHandler,
  teachersList,
  updateTeacherOk,
} from '@/test/registrationHandlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

const BRUNO = teacher({ id: 10, name: 'Bruno Braga', phone: '+5511988880010' });

describe('lista de professores', () => {
  it('mostra nome, telefone e situação', async () => {
    server.use(meOk(ADMIN), teachersList(BRUNO));

    renderApp('/admin/teachers');

    const row = await screen.findByRole('row', { name: /Bruno Braga/ });
    expect(within(row).getByText('+5511988880010')).toBeInTheDocument();
    expect(within(row).getByText('Ativo')).toBeInTheDocument();
  });
});

describe('cadastro de professor', () => {
  it('cria o professor e a lista volta com ele', async () => {
    const sent: TeacherInput[] = [];
    const teachers: Teacher[] = [];

    server.use(
      meOk(ADMIN),
      teachersHandler(() => pageOf(teachers)),
      createTeacherOk((body) => {
        sent.push(body);
        teachers.push(teacher({ id: 98, ...body }));
      }),
    );

    const { user } = renderApp('/admin/teachers');

    await user.click(await screen.findByRole('button', { name: 'Novo professor' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Nome'), 'Bruno Braga');
    await user.type(within(dialog).getByLabelText('Telefone'), '+5511988880010');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(sent).toEqual([{ name: 'Bruno Braga', phone: '+5511988880010' }]));
    expect(await screen.findByRole('row', { name: /Bruno Braga/ })).toBeInTheDocument();
  });

  it('recusa telefone fora do E.164 sem chamar a API', async () => {
    server.use(meOk(ADMIN), teachersList());

    const { user } = renderApp('/admin/teachers');

    await user.click(await screen.findByRole('button', { name: 'Novo professor' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Nome'), 'Bruno Braga');
    await user.type(within(dialog).getByLabelText('Telefone'), '(11) 98888-0010');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    expect(await within(dialog).findByText(/E\.164/)).toBeInTheDocument();
  });
});

describe('edição de professor', () => {
  it('abre com os dados atuais e envia o que mudou', async () => {
    const sent: Array<{ id: number; body: TeacherInput }> = [];
    server.use(meOk(ADMIN), teachersList(BRUNO), updateTeacherOk((id, body) => sent.push({ id, body })));

    const { user } = renderApp('/admin/teachers');

    await user.click(await screen.findByRole('button', { name: 'Ações de Bruno Braga' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Editar' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByLabelText('Nome')).toHaveValue('Bruno Braga');

    await user.clear(within(dialog).getByLabelText('Telefone'));
    await user.type(within(dialog).getByLabelText('Telefone'), '+5511977770010');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(sent).toEqual([{ id: 10, body: { name: 'Bruno Braga', phone: '+5511977770010' } }]),
    );
  });
});

describe('desativação de professor', () => {
  /**
   * O aviso é sobre o que a desativação deixa quebrado: os alunos continuam
   * apontando para ele, e alguém precisa remanejá-los.
   */
  it('avisa sobre o remanejamento dos alunos', async () => {
    const deactivated: number[] = [];
    server.use(meOk(ADMIN), teachersList(BRUNO), deactivateTeacherOk((id) => deactivated.push(id)));

    const { user } = renderApp('/admin/teachers');

    await user.click(await screen.findByRole('button', { name: 'Ações de Bruno Braga' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Desativar' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/remanejamento/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Desativar' }));

    await waitFor(() => expect(deactivated).toEqual([10]));
  });
});
