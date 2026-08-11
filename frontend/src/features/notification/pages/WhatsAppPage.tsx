import { useEffect, useState } from 'react';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorNotice } from '@/components/ErrorNotice';
import { Toast, useToast } from '@/components/Toast';
import { ApiError } from '@/lib/apiClient';
import { apiMessage } from '@/lib/apiError';
import {
  useCreateWhatsAppSession,
  useDeleteWhatsAppSession,
  useWhatsAppInstances,
  useWhatsAppStatus,
} from '../api/notificationQueries';
import { ConnectionPanel } from '../components/ConnectionPanel';
import { PhoneVersionForm } from '../components/PhoneVersionForm';
import { QrPanel } from '../components/QrPanel';

/**
 * Quanto tempo o QR fica de pé antes de a tela parar de oferecê-lo.
 *
 * Quem decide de verdade é o gateway (QRCODE_LIMIT), e ele não avisa: um QR
 * vencido volta na mesma imagem do válido. Contar aqui é aproximação
 * deliberada e conservadora — errar para menos custa um clique em "gerar
 * outro"; errar para mais custa alguém lendo um código morto e culpando o
 * celular.
 */
const QR_LIFETIME_MS = 60_000;

/** 503 é a única condição em que a API admite não saber responder. */
function isNotConfigured(error: unknown) {
  return error instanceof ApiError && error.status === 503;
}

type Confirming = 'disconnect' | 'switch-number';

/**
 * O que hoje se faz no Manager da Evolution, com a chave do gateway na mão.
 *
 * A tela é uma máquina de estados, não um formulário com um botão: sem
 * configuração, sem instância, sem sessão, pareando e conectada são situações
 * diferentes, e cada uma tem (ou não tem) sua própria ação.
 */
export function WhatsAppPage() {
  const [qrCode, setQrCode] = useState<string | null>(null);
  const [qrExpired, setQrExpired] = useState(false);
  const [confirming, setConfirming] = useState<Confirming | null>(null);
  const toast = useToast();

  // O polling existe enquanto há QR válido na tela: o pareamento termina no
  // celular, e sem repetir o status a tela mostraria um QR já consumido.
  // Expirado, ninguém mais vai parear com ele — e continuar perguntando seria
  // exatamente a aba esquecida girando à toa.
  const status = useWhatsAppStatus(qrCode !== null && !qrExpired);
  const instances = useWhatsAppInstances(status.isSuccess);
  const createSession = useCreateWhatsAppSession();
  const deleteSession = useDeleteWhatsAppSession();

  const state = status.data?.state;

  // Conectou: o QR sai da tela e o polling para junto com ele.
  useEffect(() => {
    if (state === 'CONNECTED') setQrCode(null);
  }, [state]);

  useEffect(() => {
    if (qrCode === null || qrExpired) return;
    const timer = setTimeout(() => setQrExpired(true), QR_LIFETIME_MS);
    return () => clearTimeout(timer);
  }, [qrCode, qrExpired]);

  // `?? null`: numa instância já conectada o back devolve o estado e nenhum QR.
  // Sem normalizar, um campo ausente ligaria o painel de QR (e o polling) para
  // uma imagem que não existe.
  const pair = () =>
    createSession.mutate(undefined, {
      onSuccess: (session) => {
        setQrExpired(false);
        setQrCode(session.qrCode ?? null);
      },
    });

  const closeQr = () => {
    setQrCode(null);
    setQrExpired(false);
  };

  const disconnect = () =>
    deleteSession.mutate(undefined, {
      onSuccess: () => {
        const switching = confirming === 'switch-number';
        setConfirming(null);
        // Trocar de número é desconectar e parear em seguida: parar no meio
        // deixaria o estúdio mudo sem ninguém ter pedido isso.
        if (switching) pair();
        else toast.show('WhatsApp desconectado.');
      },
    });

  return (
    <section className="space-y-6">
      <h1 className="text-xl font-semibold text-foreground">WhatsApp</h1>

      <Toast message={toast.message} serial={toast.serial} />

      {status.isPending && <p className="text-sm text-muted-foreground">Consultando o gateway…</p>}

      {isNotConfigured(status.error) && (
        <p className="max-w-lg text-sm text-muted-foreground">
          O gateway de WhatsApp não está configurado neste ambiente: falta a URL base ou a chave de API nas
          variáveis do servidor. Não há nada a fazer por esta tela até que a configuração exista.
        </p>
      )}

      {status.isError && !isNotConfigured(status.error) && (
        <ErrorNotice message={apiMessage(status.error)} onRetry={status.refetch} />
      )}

      {createSession.error != null && (
        <p role="alert" className="max-w-lg text-sm text-danger">
          {apiMessage(createSession.error)}
        </p>
      )}

      {status.data &&
        (qrCode === null ? (
          <ConnectionPanel
            status={status.data}
            pairing={createSession.isPending}
            onPair={pair}
            onDisconnect={() => setConfirming('disconnect')}
            onSwitchNumber={() => setConfirming('switch-number')}
          />
        ) : (
          <QrPanel
            qrCode={qrCode}
            expired={qrExpired}
            pairing={createSession.isPending}
            onRefresh={pair}
            onClose={closeQr}
          />
        ))}

      {status.data && (
        <PhoneVersionForm status={status.data} instances={instances} onSaved={toast.show} />
      )}

      {confirming !== null && (
        <ConfirmDialog
          title={confirming === 'disconnect' ? 'Desconectar o WhatsApp' : 'Parear outro número'}
          confirmLabel={confirming === 'disconnect' ? 'Desconectar' : 'Desconectar e parear'}
          pendingLabel="Desconectando…"
          pending={deleteSession.isPending}
          error={deleteSession.error}
          onClose={() => setConfirming(null)}
          onConfirm={disconnect}
        >
          {confirming === 'disconnect' ? (
            <p>
              Os avisos de aula e de cobrança param de sair até alguém parear um número de novo. A
              instância continua existindo — desconectar não é apagar.
            </p>
          ) : (
            <p>
              O gateway mantém um número por instância: para parear outro, este ({status.data?.maskedPhone}
              ) é desconectado primeiro. Os avisos de aula e de cobrança param de sair até o novo número
              terminar o pareamento pelo QR.
            </p>
          )}
        </ConfirmDialog>
      )}
    </section>
  );
}
