import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ListFeedback } from '@/components/ListFeedback';
import { Pagination } from '@/components/Pagination';
import { Toast, useToast } from '@/components/Toast';
import { usePageClamp } from '@/components/usePageClamp';
import { ActionMenu } from '@/components/ui/actionMenu';
import { Button } from '@/components/ui/button';
import { pageQuery, parsePageParam } from '@/lib/listParams';
import { formatDate } from '@/lib/formatters';
import type { Announcement } from '../api/schedulingApi';
import { useAnnouncements, useCreateAnnouncement, useDeleteAnnouncement } from '../api/schedulingQueries';
import { AnnouncementFormDialog } from '../components/AnnouncementFormDialog';

type Pending = { kind: 'create' } | { kind: 'delete'; announcement: Announcement };

export function AnnouncementsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePageParam(searchParams.get('page'));

  const [pending, setPending] = useState<Pending | null>(null);
  const toast = useToast();

  const announcements = useAnnouncements(page);
  const createAnnouncement = useCreateAnnouncement();
  const deleteAnnouncement = useDeleteAnnouncement();

  function closeDialog() {
    setPending(null);
    createAnnouncement.reset();
    deleteAnnouncement.reset();
  }

  function succeed(message: string) {
    toast.show(message);
    closeDialog();
  }

  const data = announcements.data;
  const clamping = usePageClamp(data, (next) => setSearchParams(pageQuery(next), { replace: true }));

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Comunicados</h1>
        <Button onClick={() => setPending({ kind: 'create' })}>Novo comunicado</Button>
      </div>

      <Toast message={toast.message} serial={toast.serial} />

      <ListFeedback
        state={announcements}
        loading="Carregando os comunicados…"
        empty={data && data.content.length === 0 && !clamping ? 'Nenhum comunicado cadastrado.' : undefined}
      />

      {data && data.content.length > 0 && (
        <>
          <table className="w-full border-collapse text-sm">
            <caption className="sr-only">Comunicados</caption>
            <thead>
              <tr className="border-b border-slate-200 text-left text-slate-500">
                <th scope="col" className="px-3 py-2 font-medium">
                  Data
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Descrição
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Efeito
                </th>
                <th scope="col" className="px-3 py-2">
                  <span className="sr-only">Ações</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((announcement) => (
                <tr key={announcement.id} className="border-b border-slate-100">
                  <td className="px-3 py-2 text-slate-900">{formatDate(announcement.date)}</td>
                  <td className="px-3 py-2">{announcement.description}</td>
                  {/* O efeito é o que distingue um comunicado do outro na
                      lista: sem a coluna, "Recesso" e "Obra na rua" pareceriam
                      a mesma coisa, e uma delas apaga o dia. */}
                  <td className="px-3 py-2">
                    {announcement.suspendsClasses ? (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-900">
                        Aulas suspensas
                      </span>
                    ) : (
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
                        Aulas normais
                      </span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-right">
                    {/* Não existe edição de comunicado no back: data ou
                        descrição erradas se resolvem apagando e cadastrando de
                        novo — e o aviso já saiu de qualquer forma. */}
                    <ActionMenu
                      label={`Ações de ${announcement.description}`}
                      actions={[
                        { label: 'Excluir', onSelect: () => setPending({ kind: 'delete', announcement }) },
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
        <AnnouncementFormDialog
          pending={createAnnouncement.isPending}
          error={createAnnouncement.error}
          onClose={closeDialog}
          onSubmit={(values) =>
            createAnnouncement.mutate(values, {
              // "serão avisados", não "receberam": a entrega do WhatsApp é
              // assíncrona (evento + reentrega at-least-once).
              onSuccess: () => succeed('Comunicado cadastrado. Os alunos ativos serão avisados por WhatsApp.'),
            })
          }
        />
      )}

      {pending?.kind === 'delete' && (
        <ConfirmDialog
          title="Excluir comunicado"
          confirmLabel="Excluir"
          pendingLabel="Excluindo…"
          pending={deleteAnnouncement.isPending}
          error={deleteAnnouncement.error}
          onClose={closeDialog}
          onConfirm={() =>
            deleteAnnouncement.mutate(pending.announcement.id, {
              onSuccess: () => succeed('Comunicado excluído.'),
            })
          }
        >
          {/* Só quem suspendia devolve aula ao ser apagado. Prometer isso para
              um aviso comum faria o ADMIN esperar uma mudança que não vem. */}
          <p>
            {pending.announcement.suspendsClasses && (
              <>
                As aulas de <strong>{formatDate(pending.announcement.date)}</strong> voltam para a agenda.{' '}
              </>
            )}
            Quem já foi avisado por WhatsApp não recebe desmentido — o aviso precisa ser dado à mão.
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
}
