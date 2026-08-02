import { screen, waitFor, within } from '@testing-library/react';
import type { RequestHandler } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Student, StudentInput } from '@/features/registration/api/registrationApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import {
  invoice,
  invoicesHandler,
  overdueStudentIds,
  overdueStudentIdsHandler,
  registerPaymentOk,
} from '@/test/billingHandlers';
import { pageOf } from '@/test/pageOf';
import {
  activateStudentOk,
  createStudentOk,
  deactivateStudentOk,
  student,
  studentsHandler,
  studentsList,
  teacher,
  teachersList,
  updateStudentOk,
} from '@/test/registrationHandlers';
import { problem } from '@/test/problem';
import { createTestQueryClient, renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import {
  accessOfStudentsHandler,
  accessUser,
  activateAccessOk,
  createAccessOk,
  noAccessYet,
} from '@/test/userHandlers';
import { HttpResponse, delay, http } from 'msw';

const BRUNO = teacher({ id: 10, name: 'Bruno Braga' });
const CLARA = teacher({ id: 11, name: 'Clara Cruz', active: false });

const ANA = student({ id: 1, name: 'Ana Alves', phone: '+5511999990001', teacherId: 10 });

/**
 * Toda tela de aluno consulta quatro coisas: a lista, os professores, quem tem
 * acesso e quem está com fatura atrasada.
 */
function renderStudents(...handlers: RequestHandler[]) {
  server.use(meOk(ADMIN), teachersList(BRUNO, CLARA), noAccessYet(), overdueStudentIds(), ...handlers);
  return renderApp('/admin/students');
}

function rowOf(name: string) {
  return screen.findByRole('row', { name: new RegExp(name) });
}

async function openMenu(user: ReturnType<typeof renderApp>['user'], name: string, action: string) {
  await user.click(await screen.findByRole('button', { name: `Ações de ${name}` }));
  await user.click(await screen.findByRole('menuitem', { name: action }));
  return screen.findByRole('dialog');
}

describe('lista de alunos', () => {
  it('mostra nome, telefone, professor e situação', async () => {
    renderStudents(studentsList(ANA));

    const row = await rowOf('Ana Alves');
    expect(within(row).getByText('+5511999990001')).toBeInTheDocument();
    expect(within(row).getByText('Bruno Braga')).toBeInTheDocument();
    expect(within(row).getByText('Ativo')).toBeInTheDocument();
  });

  it('mostra vazio explícito quando não há aluno', async () => {
    renderStudents(studentsList());

    expect(await screen.findByText(/nenhum aluno ativo/i)).toBeInTheDocument();
  });
});

/**
 * A situação de cobrança vem de outra chamada, do módulo dono da fatura
 * (GET /invoices/overdue-student-ids). Quem cruza as duas listas é a tela:
 * `registration` não consulta `billing` no back, e o grafo de módulos não muda
 * por causa de uma coluna.
 */
describe('situação de cobrança', () => {
  const BIA = student({ id: 2, name: 'Bia Barros', teacherId: 10 });

  it('marca quem tem fatura atrasada e diz que os demais estão em dia', async () => {
    server.use(
      meOk(ADMIN),
      teachersList(BRUNO),
      noAccessYet(),
      overdueStudentIds(1),
      studentsList(ANA, BIA),
    );

    renderApp('/admin/students');

    expect(within(await rowOf('Ana Alves')).getByText('Atrasado')).toBeInTheDocument();
    expect(within(await rowOf('Bia Barros')).getByText('Em dia')).toBeInTheDocument();
  });

  /**
   * A coluna vazia por falha é idêntica à coluna que ainda não chegou. Calada,
   * a tela faria o ADMIN concluir que ninguém está atrasado — a leitura mais
   * cara possível do erro. E a saída é tentar de novo: falhou a consulta, não
   * o cadastro.
   */
  it('explica quando não consegue carregar a situação de cobrança e refaz a consulta', async () => {
    let calls = 0;
    server.use(
      meOk(ADMIN),
      teachersList(BRUNO),
      noAccessYet(),
      studentsList(ANA),
      http.get('/api/invoices/overdue-student-ids', () => {
        calls += 1;
        return calls === 1 ? HttpResponse.error() : HttpResponse.json([1]);
      }),
    );

    const { user } = renderApp('/admin/students');

    expect(await screen.findByRole('alert')).toHaveTextContent(/situação de cobrança/i);

    await user.click(await screen.findByRole('button', { name: 'Tentar de novo' }));

    expect(await within(await rowOf('Ana Alves')).findByText('Atrasado')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });

  /**
   * A baixa acontece em outra tela, e é lá que o atraso deixa de existir. Sem
   * invalidar a chave da cobrança, voltar para os alunos mostraria "Atrasado"
   * para quem acabou de pagar.
   *
   * O cliente do teste tem staleTime e gcTime infinitos de propósito: com os
   * padrões, toda volta refaz a chamada de qualquer jeito, e o teste passaria
   * mesmo sem a invalidação — verde que não prova nada.
   */
  it('repergunta quem está atrasado depois de uma baixa em outra tela', async () => {
    let paid = false;
    server.use(
      meOk(ADMIN),
      teachersList(BRUNO),
      noAccessYet(),
      studentsList(ANA),
      overdueStudentIdsHandler(() => (paid ? [] : [1])),
      invoicesHandler(() => pageOf([invoice({ id: 1, studentId: 1, studentName: 'Ana Alves', status: 'OVERDUE' })])),
      registerPaymentOk(() => {
        paid = true;
      }),
    );

    const { user } = renderApp(
      '/admin/students',
      createTestQueryClient({ staleTime: Infinity, gcTime: Infinity }),
    );

    expect(within(await rowOf('Ana Alves')).getByText('Atrasado')).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Faturas' }));
    await user.click(await screen.findByRole('button', { name: 'Dar baixa na fatura de Ana Alves' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Dar baixa' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await user.click(screen.getByRole('link', { name: 'Alunos' }));

    expect(await within(await rowOf('Ana Alves')).findByText('Em dia')).toBeInTheDocument();
  });

  /**
   * "Ainda não sei" não pode virar "em dia" — mesmo cuidado da consulta de
   * acesso. Afirmar que está tudo certo antes da resposta é pior que não dizer
   * nada: é exatamente a informação que o ADMIN veio buscar.
   */
  it('não afirma nada enquanto a consulta de cobrança não responde', async () => {
    server.use(
      meOk(ADMIN),
      teachersList(BRUNO),
      noAccessYet(),
      studentsList(ANA),
      http.get('/api/invoices/overdue-student-ids', async () => {
        await delay('infinite');
        return HttpResponse.json([]);
      }),
    );

    renderApp('/admin/students');

    const row = await rowOf('Ana Alves');
    expect(within(row).queryByText('Em dia')).not.toBeInTheDocument();
    expect(within(row).queryByText('Atrasado')).not.toBeInTheDocument();
  });
});

describe('filtro de situação', () => {
  /** Critério de aceite: o filtro só existe se chegar ao backend. Default: ativos. */
  it('parte de ?active=true e alterna para inativos e todos', async () => {
    const sent: string[] = [];
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      noAccessYet(),
      studentsHandler((params) => {
        const active = params.get('active');
        return pageOf(
          [ANA, student({ id: 2, name: 'Bia Barros', active: false })].filter(
            (each) => active == null || String(each.active) === active,
          ),
        );
      }, sent),
    );

    const { user } = renderApp('/admin/students');

    await rowOf('Ana Alves');
    expect(new URLSearchParams(sent[0]!).get('active')).toBe('true');

    await user.click(screen.getByRole('button', { name: 'Inativos' }));

    await waitFor(() => expect(sent).toHaveLength(2));
    expect(new URLSearchParams(sent[1]!).get('active')).toBe('false');
    expect(await rowOf('Bia Barros')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Todos' }));

    await waitFor(() => expect(sent).toHaveLength(3));
    expect(new URLSearchParams(sent[2]!).get('active')).toBeNull();
  });
});

describe('cadastro de aluno', () => {
  /** Critério de aceite: o ciclo inteiro de criação, com o telefone em E.164. */
  it('cria o aluno e a lista volta com ele', async () => {
    const sent: StudentInput[] = [];
    const students: Student[] = [];

    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      noAccessYet(),
      studentsHandler(() => pageOf(students)),
      createStudentOk((body) => {
        sent.push(body);
        students.push(student({ id: 99, ...body }));
      }),
    );

    const { user } = renderApp('/admin/students');

    await user.click(await screen.findByRole('button', { name: 'Novo aluno' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Nome'), 'Ana Alves');
    await user.type(within(dialog).getByLabelText('Telefone'), '+5511999990001');
    await user.selectOptions(within(dialog).getByLabelText('Professor'), '10');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(sent).toEqual([{ name: 'Ana Alves', phone: '+5511999990001', teacherId: 10 }]),
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await rowOf('Ana Alves')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(/aluno cadastrado/i));
  });

  /** Critério de aceite: o zod espelha o limite do back e barra antes da rede. */
  it('recusa telefone fora do E.164 sem chamar a API', async () => {
    const { user } = renderStudents(studentsList());

    await user.click(await screen.findByRole('button', { name: 'Novo aluno' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Nome'), 'Ana Alves');
    await user.type(within(dialog).getByLabelText('Telefone'), '11999990001');
    await user.selectOptions(within(dialog).getByLabelText('Professor'), '10');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    // Sem handler de POST: se a chamada saísse, o MSW derrubaria o teste.
    expect(await within(dialog).findByText(/E\.164/)).toBeInTheDocument();
  });

  /**
   * Critério de aceite: select de professor nunca oferece inativo — o back
   * recusa o vínculo, e oferecer a opção seria prometer o que não se cumpre.
   */
  it('o select de professor só oferece ativos', async () => {
    const { user } = renderStudents(studentsList());

    await user.click(await screen.findByRole('button', { name: 'Novo aluno' }));
    const dialog = await screen.findByRole('dialog');

    const select = within(dialog).getByLabelText('Professor');
    expect(within(select).getByRole('option', { name: 'Bruno Braga' })).toBeInTheDocument();
    expect(within(select).queryByRole('option', { name: 'Clara Cruz' })).not.toBeInTheDocument();
  });

  it('edita o aluno com os dados já preenchidos', async () => {
    const sent: Array<{ id: number; body: StudentInput }> = [];
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      noAccessYet(),
      studentsList(ANA),
      updateStudentOk((id, body) => sent.push({ id, body })),
    );

    const { user } = renderApp('/admin/students');
    const dialog = await openMenu(user, 'Ana Alves', 'Editar');

    expect(within(dialog).getByLabelText('Nome')).toHaveValue('Ana Alves');
    expect(within(dialog).getByLabelText('Telefone')).toHaveValue('+5511999990001');

    await user.clear(within(dialog).getByLabelText('Nome'));
    await user.type(within(dialog).getByLabelText('Nome'), 'Ana Alves Lima');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(sent).toEqual([
        { id: 1, body: { name: 'Ana Alves Lima', phone: '+5511999990001', teacherId: 10 } },
      ]),
    );
  });
});

describe('desativação', () => {
  /**
   * Critério de aceite: desativar avisa que o acesso do aluno cai junto. É
   * consequência automática (evento no back) e a reconcessão é manual — quem
   * desativa precisa saber disso antes de confirmar.
   */
  it('avisa que o acesso será revogado e desativa', async () => {
    const deactivated: number[] = [];
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      noAccessYet(),
      studentsList(ANA),
      deactivateStudentOk((id) => deactivated.push(id)),
    );

    const { user } = renderApp('/admin/students');
    const dialog = await openMenu(user, 'Ana Alves', 'Desativar');

    expect(within(dialog).getByText(/acesso do aluno será revogado/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Desativar' }));

    await waitFor(() => expect(deactivated).toEqual([1]));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  /**
   * Reativar o aluno é o caminho de volta — e a tela diz o que ele NÃO faz:
   * o login continua revogado, porque reconceder acesso é decisão à parte.
   */
  it('reativa o aluno avisando que o login não volta junto', async () => {
    const activated: number[] = [];
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      noAccessYet(),
      studentsList(student({ id: 1, name: 'Ana Alves', active: false })),
      activateStudentOk((id) => activated.push(id)),
    );

    const { user } = renderApp('/admin/students?active=false');
    const dialog = await openMenu(user, 'Ana Alves', 'Ativar');

    expect(within(dialog).getByText(/login continua revogado/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Ativar' }));

    await waitFor(() => expect(activated).toEqual([1]));
  });

  /** Não existe DELETE de aluno no back: a linha nunca some, ela muda de situação. */
  it('não oferece excluir', async () => {
    const { user } = renderStudents(studentsList(ANA));

    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));

    expect(screen.queryByRole('menuitem', { name: /excluir/i })).not.toBeInTheDocument();
  });
});

describe('acesso do aluno', () => {
  /** Critério de aceite: criar o login do aluno pela própria linha da lista. */
  it('cria o acesso com login e senha', async () => {
    const sent: Array<{ login: string; role: string; studentId: number }> = [];
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      noAccessYet(),
      studentsList(ANA),
      createAccessOk((body) => sent.push({ login: body.login, role: body.role, studentId: body.studentId })),
    );

    const { user } = renderApp('/admin/students');
    const dialog = await openMenu(user, 'Ana Alves', 'Criar acesso');

    await user.type(within(dialog).getByLabelText('Login'), 'ana.alves');
    await user.type(within(dialog).getByLabelText('Senha'), 's3nha-forte');
    await user.click(within(dialog).getByRole('button', { name: 'Criar acesso' }));

    await waitFor(() => expect(sent).toEqual([{ login: 'ana.alves', role: 'STUDENT', studentId: 1 }]));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  /**
   * Quem já tem login revogado não precisa de outro: a reativação é a ação
   * certa, e ela só é possível porque o back diz qual usuário pertence ao
   * aluno (GET /users?studentIds=).
   */
  it('oferece reativar quando o aluno já tem login revogado', async () => {
    const activated: number[] = [];
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      studentsList(ANA),
      accessOfStudentsHandler(() => [accessUser({ id: 500, studentId: 1, active: false })]),
      activateAccessOk((id) => activated.push(id)),
    );

    const { user } = renderApp('/admin/students');
    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));

    expect(screen.queryByRole('menuitem', { name: 'Criar acesso' })).not.toBeInTheDocument();
    await user.click(await screen.findByRole('menuitem', { name: 'Reativar acesso' }));

    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Reativar acesso' }));

    await waitFor(() => expect(activated).toEqual([500]));
  });

  /** Login ativo não tem ação de acesso a oferecer — nem criar, nem reativar. */
  it('não oferece ação de acesso para quem já tem login ativo', async () => {
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      studentsList(ANA),
      accessOfStudentsHandler(() => [accessUser({ id: 500, studentId: 1, active: true })]),
    );

    const { user } = renderApp('/admin/students');
    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));

    await screen.findByRole('menuitem', { name: 'Editar' });
    expect(screen.queryByRole('menuitem', { name: /acesso/i })).not.toBeInTheDocument();
  });

  /**
   * Login já em uso é 409 do back. A mensagem dele é que explica o que houve, e
   * o botão continua armado: escolher outro login é a saída, diferente do 409
   * da baixa de fatura, onde repetir não adiantaria.
   */
  it('mostra a mensagem do back quando o login já existe', async () => {
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      noAccessYet(),
      studentsList(ANA),
      http.post('/api/users', () => problem(409, 'Conflict', 'Login já em uso: ana.alves')),
    );

    const { user } = renderApp('/admin/students');
    const dialog = await openMenu(user, 'Ana Alves', 'Criar acesso');

    await user.type(within(dialog).getByLabelText('Login'), 'ana.alves');
    await user.type(within(dialog).getByLabelText('Senha'), 's3nha-forte');
    await user.click(within(dialog).getByRole('button', { name: 'Criar acesso' }));

    expect(await within(dialog).findByText('Login já em uso: ana.alves')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Criar acesso' })).toBeEnabled();
  });

  /**
   * A tabela chega antes da consulta de acesso — são duas chamadas. Enquanto a
   * segunda não responde, "não sei" não pode virar "não tem login": o back não
   * impede um segundo usuário para o mesmo aluno (só o login é único), então um
   * clique nesse intervalo criaria uma conta duplicada.
   */
  it('não oferece ação de acesso enquanto a consulta não responde', async () => {
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      studentsList(ANA),
      http.get('/api/users', async () => {
        await delay('infinite');
        return HttpResponse.json([]);
      }),
    );

    const { user } = renderApp('/admin/students');
    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));

    await screen.findByRole('menuitem', { name: 'Editar' });
    expect(screen.queryByRole('menuitem', { name: /acesso/i })).not.toBeInTheDocument();
  });

  /**
   * Esconder a ação também é o certo quando a consulta falha de vez — o risco
   * da conta duplicada é o mesmo. O que não pode é sumir calado: sem explicação
   * o ADMIN vê "Criar acesso" desaparecer da tela e não tem o que fazer.
   */
  it('explica quando não consegue carregar a situação de acesso', async () => {
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      studentsList(ANA),
      http.get('/api/users', () => HttpResponse.error()),
    );

    const { user } = renderApp('/admin/students');

    expect(await screen.findByRole('alert')).toHaveTextContent(/situação de acesso/i);

    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));
    expect(screen.queryByRole('menuitem', { name: /acesso/i })).not.toBeInTheDocument();
  });

  /** E a saída é tentar de novo: a falha é da consulta, não do cadastro. */
  it('refaz a consulta de acesso e volta a oferecer a ação', async () => {
    let calls = 0;
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      studentsList(ANA),
      http.get('/api/users', () => {
        calls += 1;
        return calls === 1 ? HttpResponse.error() : HttpResponse.json([]);
      }),
    );

    const { user } = renderApp('/admin/students');

    await user.click(await screen.findByRole('button', { name: 'Tentar de novo' }));

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));
    expect(await screen.findByRole('menuitem', { name: 'Criar acesso' })).toBeInTheDocument();
  });

  /** Uma chamada por página, e não uma por linha. */
  it('consulta o acesso de todos os alunos da página de uma vez', async () => {
    const asked: string[] = [];
    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      studentsList(ANA, student({ id: 2, name: 'Bia Barros' })),
      accessOfStudentsHandler(() => [], asked),
    );

    renderApp('/admin/students');

    await rowOf('Bia Barros');
    await waitFor(() => expect(asked).toHaveLength(1));
    expect(asked[0]).toBe('1,2');
  });
});

/**
 * A última página some debaixo do ADMIN quando ele desativa o único aluno dela
 * sob o filtro "Ativos". Sem recuar, a tela diria "nenhum aluno ativo" com a
 * página 1 cheia.
 */
describe('paginação', () => {
  it('recua quando a página deixa de existir depois da desativação', async () => {
    let deactivated = false;
    const sent: string[] = [];

    server.use(
      meOk(ADMIN),
      overdueStudentIds(),
      teachersList(BRUNO),
      noAccessYet(),
      studentsHandler((params) => {
        const page = Number(params.get('page') ?? 0);
        if (deactivated) {
          return pageOf(page === 0 ? [ANA] : [], { number: page, totalElements: 1, totalPages: 1 });
        }
        return pageOf(page === 0 ? [ANA] : [student({ id: 2, name: 'Bia Barros' })], {
          number: page,
          totalElements: 2,
          totalPages: 2,
        });
      }, sent),
      deactivateStudentOk(() => {
        deactivated = true;
      }),
    );

    const { user } = renderApp('/admin/students?page=1', createTestQueryClient({ staleTime: Infinity }));

    const dialog = await openMenu(user, 'Bia Barros', 'Desativar');
    await user.click(within(dialog).getByRole('button', { name: 'Desativar' }));

    expect(await rowOf('Ana Alves')).toBeInTheDocument();
    expect(screen.queryByText(/nenhum aluno ativo/i)).not.toBeInTheDocument();
    expect(new URLSearchParams(sent[sent.length - 1]!).get('page')).toBe('0');
  });
});
