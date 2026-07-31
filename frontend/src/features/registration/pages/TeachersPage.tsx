import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ListFeedback } from '@/components/ListFeedback';
import { Pagination } from '@/components/Pagination';
import { StatusFilter, type StatusFilterOption, emptyMessageOf } from '@/components/StatusFilter';
import { Toast, useToast } from '@/components/Toast';
import { usePageClamp } from '@/components/usePageClamp';
import { ActionMenu } from '@/components/ui/actionMenu';
import { Button } from '@/components/ui/button';
import { listQuery, parseActiveParam, parsePageParam } from '@/lib/listParams';
import type { Teacher } from '../api/registrationApi';
import { useCreateTeacher, useSetTeacherActive, useTeachers, useUpdateTeacher } from '../api/registrationQueries';
import { TeacherFormDialog } from '../components/TeacherFormDialog';

const FILTERS: StatusFilterOption[] = [
  { label: 'Ativos', active: true, empty: 'Nenhum professor ativo.' },
  { label: 'Inativos', active: false, empty: 'Nenhum professor inativo.' },
  { label: 'Todos', empty: 'Nenhum professor cadastrado.' },
];

type Pending = { kind: 'create' } | { kind: 'edit'; teacher: Teacher } | { kind: 'toggle'; teacher: Teacher };

export function TeachersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const active = parseActiveParam(searchParams.get('active'));
  const page = parsePageParam(searchParams.get('page'));

  const [pending, setPending] = useState<Pending | null>(null);
  const toast = useToast();

  const teachers = useTeachers({ active, page });
  const createTeacher = useCreateTeacher();
  const updateTeacher = useUpdateTeacher();
  const setActive = useSetTeacherActive();

  function closeDialog() {
    setPending(null);
    createTeacher.reset();
    updateTeacher.reset();
    setActive.reset();
  }

  function succeed(message: string) {
    toast.show(message);
    closeDialog();
  }

  const data = teachers.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(listQuery(active, next), { replace: true }));

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Professores</h1>
        <Button onClick={() => setPending({ kind: 'create' })}>Novo professor</Button>
      </div>

      <StatusFilter
        filters={FILTERS}
        active={active}
        onChange={(next) => setSearchParams(listQuery(next, 0))}
      />

      <Toast message={toast.message} serial={toast.serial} />

      <ListFeedback
        state={teachers}
        loading="Carregando os professores…"
        empty={data && data.content.length === 0 && !clamping ? emptyMessageOf(FILTERS, active) : undefined}
      />

      {data && data.content.length > 0 && (
        <>
          <table className="w-full border-collapse text-sm">
            <caption className="sr-only">Professores</caption>
            <thead>
              <tr className="border-b border-slate-200 text-left text-slate-500">
                <th scope="col" className="px-3 py-2 font-medium">
                  Nome
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Telefone
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Situação
                </th>
                <th scope="col" className="px-3 py-2">
                  <span className="sr-only">Ações</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((teacher) => (
                <tr key={teacher.id} data-active={teacher.active} className="border-b border-slate-100">
                  <td className="px-3 py-2 text-slate-900">{teacher.name}</td>
                  <td className="px-3 py-2">{teacher.phone}</td>
                  <td className="px-3 py-2">{teacher.active ? 'Ativo' : 'Inativo'}</td>
                  <td className="px-3 py-2 text-right">
                    <ActionMenu
                      label={`Ações de ${teacher.name}`}
                      actions={[
                        { label: 'Editar', onSelect: () => setPending({ kind: 'edit', teacher }) },
                        {
                          label: teacher.active ? 'Desativar' : 'Ativar',
                          onSelect: () => setPending({ kind: 'toggle', teacher }),
                        },
                      ]}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            onChange={(next) => setSearchParams(listQuery(active, next))}
          />
        </>
      )}

      {pending?.kind === 'create' && (
        <TeacherFormDialog
          title="Novo professor"
          pending={createTeacher.isPending}
          error={createTeacher.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            createTeacher.mutate(values, { onSuccess: () => succeed('Professor cadastrado.') })
          }
        />
      )}

      {pending?.kind === 'edit' && (
        <TeacherFormDialog
          title="Editar professor"
          defaultValues={{ name: pending.teacher.name, phone: pending.teacher.phone }}
          pending={updateTeacher.isPending}
          error={updateTeacher.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            updateTeacher.mutate(
              { id: pending.teacher.id, input: values },
              { onSuccess: () => succeed('Professor atualizado.') },
            )
          }
        />
      )}

      {pending?.kind === 'toggle' && (
        <ConfirmDialog
          title={pending.teacher.active ? 'Desativar professor' : 'Ativar professor'}
          confirmLabel={pending.teacher.active ? 'Desativar' : 'Ativar'}
          pendingLabel={pending.teacher.active ? 'Desativando…' : 'Ativando…'}
          pending={setActive.isPending}
          error={setActive.error}
          onClose={closeDialog}
          onConfirm={() =>
            setActive.mutate(
              { id: pending.teacher.id, active: !pending.teacher.active },
              {
                onSuccess: () =>
                  succeed(pending.teacher.active ? 'Professor desativado.' : 'Professor ativado.'),
              },
            )
          }
        >
          {pending.teacher.active ? (
            <p>
              <strong>{pending.teacher.name}</strong> some dos selects e o acesso dele será revogado. Os alunos
              que estão com ele continuam apontando para o professor desativado e precisarão de remanejamento.
            </p>
          ) : (
            <p>
              <strong>{pending.teacher.name}</strong> volta a aparecer nos selects. O login continua revogado
              até ser reativado à mão.
            </p>
          )}
        </ConfirmDialog>
      )}
    </section>
  );
}
