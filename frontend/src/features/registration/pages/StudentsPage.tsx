import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorNotice } from '@/components/ErrorNotice';
import { ListFeedback } from '@/components/ListFeedback';
import { Pagination } from '@/components/Pagination';
import { StatusFilter, type StatusFilterOption, emptyMessageOf } from '@/components/StatusFilter';
import { Toast, useToast } from '@/components/Toast';
import { usePageClamp } from '@/components/usePageClamp';
import { ActionMenu, type MenuAction } from '@/components/ui/actionMenu';
import { Button } from '@/components/ui/button';
import { listQuery, parseActiveParam, parsePageParam } from '@/lib/listParams';
import type { AccessUser } from '@/features/auth/api/usersApi';
import { useAccessOfStudents, useActivateAccess, useCreateStudentAccess } from '@/features/auth/api/usersQueries';
import { CreateAccessDialog } from '@/features/auth/components/CreateAccessDialog';
import type { Plan, PlanEnrollment } from '@/features/billing/api/billingApi';
import {
  useBillingSettings,
  useEnroll,
  useEnrollmentsOfStudent,
  useOverdueStudentIds,
  usePlansLookup,
  useSetEnrollmentActive,
} from '@/features/billing/api/billingQueries';
import { BillingStatusBadge } from '@/features/billing/components/BillingStatusBadge';
import { StudentPlansDialog } from '@/features/billing/components/StudentPlansDialog';
import type { Student } from '../api/registrationApi';
import {
  nameById,
  useCreateStudent,
  useSetStudentActive,
  useStudents,
  useTeachersLookup,
  useUpdateStudent,
  withCurrent,
} from '../api/registrationQueries';
import { StudentFormDialog } from '../components/StudentFormDialog';

const FILTERS: StatusFilterOption[] = [
  { label: 'Ativos', active: true, empty: 'Nenhum aluno ativo.' },
  { label: 'Inativos', active: false, empty: 'Nenhum aluno inativo.' },
  { label: 'Todos', empty: 'Nenhum aluno cadastrado.' },
];

type Pending =
  | { kind: 'create' }
  | { kind: 'edit'; student: Student }
  | { kind: 'toggle'; student: Student }
  | { kind: 'access'; student: Student }
  | { kind: 'plans'; student: Student }
  | { kind: 'reactivate'; student: Student; userId: number };

export function StudentsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const active = parseActiveParam(searchParams.get('active'));
  const page = parsePageParam(searchParams.get('page'));

  const [pending, setPending] = useState<Pending | null>(null);
  const toast = useToast();

  const students = useStudents({ active, page });
  const teachers = useTeachersLookup();

  const studentIds = students.data?.content.map((student) => student.id) ?? [];
  const access = useAccessOfStudents(studentIds);
  /*
   * A situação de cobrança vem do módulo dono da fatura, em consulta própria.
   * O back não junta as duas coisas: `registration` não consulta `billing`, e
   * uma coluna na tela não é motivo para abrir essa aresta. Quem cruza é aqui.
   */
  const overdue = useOverdueStudentIds();
  const plans = usePlansLookup();
  const planStudentId = pending?.kind === 'plans' ? pending.student.id : undefined;
  const enrollments = useEnrollmentsOfStudent(planStudentId);
  const enroll = useEnroll();
  const setEnrollmentActive = useSetEnrollmentActive();
  const billingSettings = useBillingSettings();

  const createStudent = useCreateStudent();
  const updateStudent = useUpdateStudent();
  const setActive = useSetStudentActive();
  const createAccess = useCreateStudentAccess();
  const activateAccess = useActivateAccess();

  function closeDialog() {
    setPending(null);
    createStudent.reset();
    updateStudent.reset();
    setActive.reset();
    createAccess.reset();
    activateAccess.reset();
    enroll.reset();
    setEnrollmentActive.reset();
  }

  function succeed(message: string) {
    toast.show(message);
    closeDialog();
  }

  const data = students.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(listQuery(active, next), { replace: true }));
  const teacherNames = nameById(teachers.data);
  const activeTeachers = (teachers.data ?? []).filter((teacher) => teacher.active);

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Alunos</h1>
        <Button onClick={() => setPending({ kind: 'create' })}>Novo aluno</Button>
      </div>

      <StatusFilter
        filters={FILTERS}
        active={active}
        onChange={(next) => setSearchParams(listQuery(next, 0))}
      />

      <Toast message={toast.message} serial={toast.serial} />

      {/* A consulta de acesso falha sozinha, e a linha esconde as ações quando
          não sabe — o mesmo cuidado do carregamento. Calado, porém, o ADMIN
          veria "Criar acesso" sumir da tela sem ter o que fazer. */}
      {access.isError && (
        <ErrorNotice
          message="Não foi possível carregar a situação de acesso dos alunos. As ações de acesso ficam indisponíveis até a consulta responder."
          onRetry={access.refetch}
        />
      )}

      {/* Falha na cobrança esvazia a coluna inteira, e coluna vazia é igual a
          coluna que ainda não chegou. Sem o aviso, o ADMIN concluiria que
          ninguém está atrasado — a leitura mais cara possível do erro. */}
      {overdue.isError && (
        <ErrorNotice
          message="Não foi possível carregar a situação de cobrança dos alunos. A coluna fica vazia até a consulta responder."
          onRetry={overdue.refetch}
        />
      )}

      <ListFeedback
        state={students}
        loading="Carregando os alunos…"
        empty={data && data.content.length === 0 && !clamping ? emptyMessageOf(FILTERS, active) : undefined}
      />

      {data && data.content.length > 0 && (
        <>
          <StudentTable
            students={data.content}
            teacherNames={teacherNames}
            access={access.data}
            overdueStudentIds={overdue.data}
            onEdit={(student) => setPending({ kind: 'edit', student })}
            onToggle={(student) => setPending({ kind: 'toggle', student })}
            onCreateAccess={(student) => setPending({ kind: 'access', student })}
            onManagePlans={(student) => setPending({ kind: 'plans', student })}
            onReactivateAccess={(student, userId) => setPending({ kind: 'reactivate', student, userId })}
          />
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            onChange={(next) => setSearchParams(listQuery(active, next))}
          />
        </>
      )}

      {pending?.kind === 'create' && (
        <StudentFormDialog
          title="Novo aluno"
          teachers={activeTeachers}
          pending={createStudent.isPending}
          error={createStudent.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            createStudent.mutate(values, { onSuccess: () => succeed('Aluno cadastrado.') })
          }
        />
      )}

      {pending?.kind === 'edit' && (
        <StudentFormDialog
          title="Editar aluno"
          // O professor atual entra na lista mesmo inativo: sem ele o select
          // abriria em "Selecione" e uma edição de telefone trocaria o
          // professor do aluno sem ninguém pedir.
          teachers={withCurrent(activeTeachers, teachers.data, pending.student.teacherId)}
          defaultValues={{
            name: pending.student.name,
            phone: pending.student.phone,
            teacherId: pending.student.teacherId,
          }}
          pending={updateStudent.isPending}
          error={updateStudent.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            updateStudent.mutate(
              { id: pending.student.id, input: values },
              { onSuccess: () => succeed('Aluno atualizado.') },
            )
          }
        />
      )}

      {pending?.kind === 'toggle' && (
        <ConfirmDialog
          title={pending.student.active ? 'Desativar aluno' : 'Ativar aluno'}
          confirmLabel={pending.student.active ? 'Desativar' : 'Ativar'}
          pendingLabel={pending.student.active ? 'Desativando…' : 'Ativando…'}
          pending={setActive.isPending}
          error={setActive.error}
          onClose={closeDialog}
          onConfirm={() =>
            setActive.mutate(
              { id: pending.student.id, active: !pending.student.active },
              {
                onSuccess: () =>
                  succeed(pending.student.active ? 'Aluno desativado.' : 'Aluno ativado.'),
              },
            )
          }
        >
          {pending.student.active ? (
            <p>
              <strong>{pending.student.name}</strong> sai da agenda e das listagens de ativos, e o acesso do
              aluno será revogado — reconceder o login depois é manual.
            </p>
          ) : (
            <p>
              <strong>{pending.student.name}</strong> volta para a agenda. O login continua revogado até ser
              reativado à mão.
            </p>
          )}
        </ConfirmDialog>
      )}

      {pending?.kind === 'access' && (
        <CreateAccessDialog
          studentName={pending.student.name}
          pending={createAccess.isPending}
          error={createAccess.error}
          onClose={closeDialog}
          onConfirm={({ login, password }) =>
            createAccess.mutate(
              { login, password, studentId: pending.student.id },
              { onSuccess: () => succeed('Acesso criado.') },
            )
          }
        />
      )}

      {pending?.kind === 'plans' && (
        <StudentPlansDialog
          studentName={pending.student.name}
          plans={(plans.data ?? []).filter((plan: Plan) => plan.active)}
          enrollments={enrollments.data}
          discountPercent={billingSettings.data?.multiPlanDiscountPercent}
          pending={enroll.isPending || setEnrollmentActive.isPending}
          error={enroll.error ?? setEnrollmentActive.error}
          onClose={closeDialog}
          onToggle={(plan: Plan, enrollment: PlanEnrollment | undefined) => {
            if (enrollment == null) {
              enroll.mutate({ studentId: pending.student.id, planId: plan.id });
            } else {
              setEnrollmentActive.mutate({ id: enrollment.id, active: !enrollment.active });
            }
          }}
        />
      )}

      {pending?.kind === 'reactivate' && (
        <ConfirmDialog
          title="Reativar acesso"
          confirmLabel="Reativar acesso"
          pendingLabel="Reativando…"
          pending={activateAccess.isPending}
          error={activateAccess.error}
          onClose={closeDialog}
          onConfirm={() =>
            activateAccess.mutate(pending.userId, { onSuccess: () => succeed('Acesso reativado.') })
          }
        >
          <p>
            <strong>{pending.student.name}</strong> volta a conseguir entrar com o login que já tinha. A senha
            continua a mesma.
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
}

function StudentTable({
  students,
  teacherNames,
  access,
  overdueStudentIds,
  onEdit,
  onToggle,
  onCreateAccess,
  onManagePlans,
  onReactivateAccess,
}: {
  students: Student[];
  teacherNames: Map<number, string>;
  access: Map<number, AccessUser> | undefined;
  /** Nada enquanto a consulta de cobrança não responde — "não sei" não é "em dia". */
  overdueStudentIds: Set<number> | undefined;
  onEdit: (student: Student) => void;
  onToggle: (student: Student) => void;
  onCreateAccess: (student: Student) => void;
  onManagePlans: (student: Student) => void;
  onReactivateAccess: (student: Student, userId: number) => void;
}) {
  return (
    <table className="w-full border-collapse text-sm">
      <caption className="sr-only">Alunos</caption>
      <thead>
        <tr className="border-b border-slate-200 text-left text-slate-500">
          <th scope="col" className="px-3 py-2 font-medium">
            Nome
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Telefone
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Professor
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Situação
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Cobrança
          </th>
          <th scope="col" className="px-3 py-2">
            <span className="sr-only">Ações</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {students.map((student) => (
          <tr key={student.id} data-active={student.active} className="border-b border-slate-100">
            <td className="px-3 py-2 text-slate-900">{student.name}</td>
            <td className="px-3 py-2">{student.phone}</td>
            {/* O back devolve só o id; o nome vem da lista de apoio. Enquanto
                ela não chega, o id é melhor que célula em branco. */}
            <td className="px-3 py-2">{teacherNames.get(student.teacherId) ?? `Professor #${student.teacherId}`}</td>
            <td className="px-3 py-2">{student.active ? 'Ativo' : 'Inativo'}</td>
            <td className="px-3 py-2">
              {overdueStudentIds && <BillingStatusBadge overdue={overdueStudentIds.has(student.id)} />}
            </td>
            <td className="px-3 py-2 text-right">
              <ActionMenu
                label={`Ações de ${student.name}`}
                actions={actionsFor(student, access, {
                  onEdit,
                  onToggle,
                  onCreateAccess,
                  onManagePlans,
                  onReactivateAccess,
                })}
              />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/**
 * A ação de acesso da linha depende do que existe hoje: nada a fazer quando o
 * login está ativo, "Criar acesso" quando nunca houve, "Reativar acesso" quando
 * houve e caiu (revogação é automática, reconcessão é manual — SPEC-009).
 *
 * Aluno inativo não recebe ação de acesso: o back recusa criar login para quem
 * saiu do estúdio, e reativar o login de quem não está mais na agenda seria
 * conceder acesso a uma conta que a próxima desativação derrubaria de novo.
 *
 * `access` só existe depois que a consulta de acesso responde, e é por isso que
 * ele é um mapa ou nada, e não um mapa vazio: "ainda não sei" e "não tem login"
 * levariam à mesma oferta de criar acesso. A tabela chega antes dessa resposta
 * (são duas chamadas), e o back não impede um segundo login para o mesmo aluno
 * — só o login é único. Sem a distinção, um clique no intervalo entre as duas
 * respostas criaria uma conta duplicada que ninguém pediu.
 */
function actionsFor(
  student: Student,
  access: Map<number, AccessUser> | undefined,
  handlers: {
    onEdit: (student: Student) => void;
    onToggle: (student: Student) => void;
    onCreateAccess: (student: Student) => void;
    onManagePlans: (student: Student) => void;
    onReactivateAccess: (student: Student, userId: number) => void;
  },
): MenuAction[] {
  const actions: MenuAction[] = [
    { label: 'Editar', onSelect: () => handlers.onEdit(student) },
    // Planos aparece para todo aluno, inclusive inativo: quem saiu do estúdio
    // ainda precisa ter a cobrança encerrada, e é aqui que isso se faz.
    { label: 'Planos', onSelect: () => handlers.onManagePlans(student) },
    {
      label: student.active ? 'Desativar' : 'Ativar',
      onSelect: () => handlers.onToggle(student),
    },
  ];

  if (!student.active || access == null) return actions;

  const user = access.get(student.id);
  if (user == null) {
    actions.push({ label: 'Criar acesso', onSelect: () => handlers.onCreateAccess(student) });
  } else if (!user.active) {
    actions.push({ label: 'Reativar acesso', onSelect: () => handlers.onReactivateAccess(student, user.id) });
  }

  return actions;
}
