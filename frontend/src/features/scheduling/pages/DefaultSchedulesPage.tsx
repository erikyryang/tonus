import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ListFeedback } from '@/components/ListFeedback';
import { Pagination } from '@/components/Pagination';
import { Toast, useToast } from '@/components/Toast';
import { usePageClamp } from '@/components/usePageClamp';
import { ActionMenu } from '@/components/ui/actionMenu';
import { Button } from '@/components/ui/button';
import { nameById, useStudentsLookup } from '@/features/registration/api/registrationQueries';
import { pageQuery, parsePageParam } from '@/lib/listParams';
import type { DefaultSchedule } from '../api/schedulingApi';
import {
  useCreateDefaultSchedule,
  useDefaultSchedules,
  useDeleteDefaultSchedule,
  useUpdateDefaultSchedule,
} from '../api/schedulingQueries';
import { DefaultScheduleFormDialog } from '../components/DefaultScheduleFormDialog';

type Pending =
  | { kind: 'create' }
  | { kind: 'edit'; schedule: DefaultSchedule; studentName: string }
  | { kind: 'delete'; schedule: DefaultSchedule; studentName: string };

/**
 * O horário padrão é a base da agenda: um aluno, uma hora, de segunda a sexta.
 * Não há filtro de situação porque o back não pagina isso por aluno ativo — a
 * lista é pequena e mostra o que existe.
 */
export function DefaultSchedulesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePageParam(searchParams.get('page'));

  const [pending, setPending] = useState<Pending | null>(null);
  const toast = useToast();

  const schedules = useDefaultSchedules(page);
  const students = useStudentsLookup();

  const createSchedule = useCreateDefaultSchedule();
  const updateSchedule = useUpdateDefaultSchedule();
  const deleteSchedule = useDeleteDefaultSchedule();

  function closeDialog() {
    setPending(null);
    createSchedule.reset();
    updateSchedule.reset();
    deleteSchedule.reset();
  }

  function succeed(message: string) {
    toast.show(message);
    closeDialog();
  }

  const data = schedules.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(pageQuery(next), { replace: true }));
  const studentNames = nameById(students.data);
  const activeStudents = (students.data ?? []).filter((student) => student.active);
  const nameOf = (studentId: number) => studentNames.get(studentId) ?? `Aluno #${studentId}`;

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Horários</h1>
        <Button onClick={() => setPending({ kind: 'create' })}>Novo horário</Button>
      </div>

      <Toast message={toast.message} />

      <ListFeedback
        state={schedules}
        loading="Carregando os horários…"
        empty={data && data.content.length === 0 && !clamping ? 'Nenhum horário cadastrado.' : undefined}
      />

      {data && data.content.length > 0 && (
        <>
          <table className="w-full border-collapse text-sm">
            <caption className="sr-only">Horários padrão</caption>
            <thead>
              <tr className="border-b border-slate-200 text-left text-slate-500">
                <th scope="col" className="px-3 py-2 font-medium">
                  Aluno
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Hora
                </th>
                <th scope="col" className="px-3 py-2">
                  <span className="sr-only">Ações</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((schedule) => (
                <tr key={schedule.id} className="border-b border-slate-100">
                  <td className="px-3 py-2 text-slate-900">{nameOf(schedule.studentId)}</td>
                  <td className="px-3 py-2">{schedule.time}</td>
                  <td className="px-3 py-2 text-right">
                    <ActionMenu
                      label={`Ações de ${nameOf(schedule.studentId)}`}
                      actions={[
                        {
                          label: 'Editar',
                          onSelect: () =>
                            setPending({ kind: 'edit', schedule, studentName: nameOf(schedule.studentId) }),
                        },
                        {
                          label: 'Excluir',
                          onSelect: () =>
                            setPending({ kind: 'delete', schedule, studentName: nameOf(schedule.studentId) }),
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
            onChange={(next) => setSearchParams(pageQuery(next))}
          />
        </>
      )}

      {pending?.kind === 'create' && (
        <DefaultScheduleFormDialog
          title="Novo horário"
          students={activeStudents}
          pending={createSchedule.isPending}
          error={createSchedule.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            createSchedule.mutate(values, { onSuccess: () => succeed('Horário cadastrado.') })
          }
        />
      )}

      {pending?.kind === 'edit' && (
        <DefaultScheduleFormDialog
          title="Editar horário"
          students={activeStudents}
          defaultValues={{ studentId: pending.schedule.studentId, time: pending.schedule.time }}
          pending={updateSchedule.isPending}
          error={updateSchedule.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            updateSchedule.mutate(
              { id: pending.schedule.id, input: values },
              { onSuccess: () => succeed('Horário atualizado.') },
            )
          }
        />
      )}

      {pending?.kind === 'delete' && (
        <ConfirmDialog
          title="Excluir horário"
          confirmLabel="Excluir"
          pendingLabel="Excluindo…"
          pending={deleteSchedule.isPending}
          error={deleteSchedule.error}
          onClose={closeDialog}
          onConfirm={() =>
            deleteSchedule.mutate(pending.schedule.id, { onSuccess: () => succeed('Horário excluído.') })
          }
        >
          <p>
            <strong>{pending.studentName}</strong> fica sem aula recorrente, e todas as exceções deste aluno
            serão apagadas junto — cancelamentos e remarcações, passados e futuros.
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
}
