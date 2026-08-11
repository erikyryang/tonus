import { Button } from '@/components/ui/button';
import type { WhatsAppStatus } from '../api/notificationApi';

/**
 * Os estados da conexão, cada um com o próprio conteúdo. Nenhum deles é erro —
 * nem "a instância não existe", que é onde o ambiente está hoje: é o começo do
 * caminho, e renderizá-lo como falha esconderia a única ação que resolve.
 */
export function ConnectionPanel({
  status,
  pairing,
  onPair,
  onDisconnect,
  onSwitchNumber,
}: {
  status: WhatsAppStatus;
  pairing: boolean;
  onPair: () => void;
  onDisconnect: () => void;
  onSwitchNumber: () => void;
}) {
  if (status.state === 'CONNECTED') {
    return (
      <Panel title="Conectado">
        <p className="text-sm text-muted-foreground">
          Número pareado: <span className="text-foreground">{status.maskedPhone}</span>
        </p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={onDisconnect}>
            Desconectar
          </Button>
          {/* Não chama o POST direto: numa instância conectada ele é idempotente e
              devolve o mesmo estado sem QR (SPEC-013), o que seria um botão que
              não faz nada. Trocar de número passa por desconectar, e a
              confirmação é onde isso fica dito. */}
          <Button onClick={onSwitchNumber} disabled={pairing}>
            Parear outro número
          </Button>
        </div>
      </Panel>
    );
  }

  if (status.state === 'CONNECTING') {
    return (
      <Panel title="Pareando">
        <p className="text-sm text-muted-foreground">
          O gateway está pareando a instância <strong>{status.instance}</strong>. Gere um QR para terminar
          no celular.
        </p>
        <Button onClick={onPair} disabled={pairing}>
          {pairing ? 'Gerando o QR…' : 'Gerar QR'}
        </Button>
      </Panel>
    );
  }

  if (status.state === 'INSTANCE_MISSING') {
    return (
      <Panel title="Instância não criada">
        <p className="text-sm text-muted-foreground">
          A instância <strong>{status.instance}</strong> ainda não existe no gateway. Criar e parear resolve
          nos dois passos de uma vez.
        </p>
        <Button onClick={onPair} disabled={pairing}>
          {pairing ? 'Criando…' : 'Criar e parear'}
        </Button>
      </Panel>
    );
  }

  return (
    <Panel title="Desconectado">
      <p className="text-sm text-muted-foreground">
        A instância <strong>{status.instance}</strong> existe, mas está sem sessão: nenhum aviso sai
        enquanto ninguém parear um número.
      </p>
      <Button onClick={onPair} disabled={pairing}>
        {pairing ? 'Gerando o QR…' : 'Parear'}
      </Button>
    </Panel>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="max-w-lg space-y-3 rounded-lg border border-border bg-card p-4">
      <h2 className="text-lg font-semibold text-foreground">{title}</h2>
      {children}
    </section>
  );
}
