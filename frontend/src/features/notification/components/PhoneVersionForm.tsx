import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorNotice } from '@/components/ErrorNotice';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { apiMessage } from '@/lib/apiError';
import type { WhatsAppInstance, WhatsAppStatus } from '../api/notificationApi';
import { useUpdateWhatsAppSettings } from '../api/notificationQueries';

/**
 * O back é a autoridade sobre o formato e sobre os limites de tamanho; aqui é
 * só para o erro aparecer no campo em vez de voltar como 400 sem endereço.
 */
const schema = z.object({
  instance: z.string().min(1, 'Escolha a instância.').max(100, 'A instância tem no máximo 100 caracteres.'),
  desiredPhoneVersion: z
    .string()
    .max(40, 'A versão tem no máximo 40 caracteres.')
    .regex(/^2\.\d+\.\d+$/, 'Use o formato 2.x.y, como 2.3000.1023204200.'),
});

type VersionForm = z.infer<typeof schema>;

/** O pedaço do useQuery que o seletor precisa para se explicar quando falha. */
export interface InstancesState {
  data: WhatsAppInstance[] | undefined;
  isError: boolean;
  error: unknown;
  refetch: () => void;
}

export function PhoneVersionForm({
  status,
  instances,
  onSaved,
}: {
  status: WhatsAppStatus;
  instances: InstancesState;
  onSaved: (message: string) => void;
}) {
  const updateSettings = useUpdateWhatsAppSettings();
  const [switching, setSwitching] = useState<VersionForm | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<VersionForm>({
    resolver: zodResolver(schema),
    defaultValues: { instance: status.instance, desiredPhoneVersion: status.phoneVersion.desired },
  });

  const savedInstance = status.instance;
  const savedVersion = status.phoneVersion.desired;

  // Depende dos valores, e não do objeto do status: com o QR na tela o status é
  // refeito a cada 5s, e resetar a cada resposta apagaria o que está sendo
  // digitado no campo da versão.
  useEffect(() => {
    reset({ instance: savedInstance, desiredPhoneVersion: savedVersion });
  }, [savedInstance, savedVersion, reset]);

  const save = (values: VersionForm) =>
    updateSettings.mutate(values, {
      onSuccess: () => {
        setSwitching(null);
        onSaved('Configuração salva.');
      },
    });

  // A confirmação é da troca de instância com sessão de pé, não de todo salvamento:
  // gravar uma versão desejada não muda por onde as mensagens saem hoje.
  const submit = (values: VersionForm) => {
    if (values.instance !== savedInstance && status.state === 'CONNECTED') {
      setSwitching(values);
      return;
    }
    save(values);
  };

  // A instância configurada pode não existir no gateway — é o caso de hoje, e
  // some do seletor se ele só listar o que a Evolution devolveu.
  const options = [...new Set([savedInstance, ...(instances.data ?? []).map((each) => each.name)])];

  return (
    <>
      <form onSubmit={handleSubmit(submit)} noValidate className="max-w-lg space-y-4">
        <h2 className="text-lg font-semibold text-foreground">Gateway</h2>

        <div className="space-y-1">
          <Label htmlFor="instance">Instância</Label>
          <Select id="instance" {...register('instance')}>
            {options.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </Select>
          {instances.isError && (
            <ErrorNotice
              message={`Não foi possível carregar as instâncias do gateway (${apiMessage(instances.error)}). O seletor mostra só a instância configurada.`}
              onRetry={instances.refetch}
            />
          )}
          {instances.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">
              O gateway ainda não tem nenhuma instância criada — parear cria {savedInstance}.
            </p>
          )}
        </div>

        <div className="space-y-1">
          <p className="text-sm text-muted-foreground">
            Versão em vigor: <span className="text-foreground">{status.phoneVersion.inEffect}</span>
          </p>
          <Label htmlFor="desiredPhoneVersion">Versão desejada do WhatsApp</Label>
          <Input
            id="desiredPhoneVersion"
            aria-invalid={errors.desiredPhoneVersion ? true : undefined}
            {...register('desiredPhoneVersion')}
          />
          {errors.desiredPhoneVersion && (
            <p className="text-sm text-danger">{errors.desiredPhoneVersion.message}</p>
          )}
          <p className="text-sm text-muted-foreground">
            Quando o QR é lido e mesmo assim a conexão não se completa, o sintoma costuma ser versão de
            WhatsApp Web recusada pelo aparelho: informe aqui uma versão mais recente e peça para recriar o
            container do gateway.
          </p>
        </div>

        {status.phoneVersion.pending && (
          <p className="rounded-md bg-warning-subtle px-3 py-2 text-sm text-warning-emphasis">
            A versão desejada só passa a valer quando o container do gateway for recriado. Até lá o gateway
            continua com {status.phoneVersion.inEffect}.
          </p>
        )}

        {updateSettings.error != null && switching === null && (
          <p role="alert" className="text-sm text-danger">
            {apiMessage(updateSettings.error)}
          </p>
        )}

        <Button type="submit" disabled={updateSettings.isPending}>
          {updateSettings.isPending ? 'Salvando…' : 'Salvar versão'}
        </Button>
      </form>

      {switching && (
        <ConfirmDialog
          title="Trocar a instância"
          confirmLabel="Trocar a instância"
          pendingLabel="Salvando…"
          pending={updateSettings.isPending}
          error={updateSettings.error}
          onClose={() => setSwitching(null)}
          onConfirm={() => save(switching)}
        >
          <p>
            As mensagens do estúdio passam a sair pela instância <strong>{switching.instance}</strong>. O
            número conectado hoje ({status.maskedPhone}) continua pareado em{' '}
            <strong>{savedInstance}</strong>, mas nenhum aviso sai mais por ele — e nada chega aos alunos
            até <strong>{switching.instance}</strong> estar conectada.
          </p>
        </ConfirmDialog>
      )}
    </>
  );
}
