import { Button } from '@/components/ui/button';

/** A Evolution às vezes já devolve o data URI pronto; às vezes só o base64. */
function imageSource(qrCode: string) {
  return qrCode.startsWith('data:') ? qrCode : `data:image/png;base64,${qrCode}`;
}

/**
 * O QR vive aqui e em lugar nenhum mais: não vai para storage nem para o cache
 * do Query. É credencial de pareamento com validade de segundos.
 *
 * Expirado, o código sai da tela em vez de virar um quadrado morto: um QR
 * vencido é indistinguível de um válido no olho, e quem tentasse lê-lo culparia
 * o celular por um código que o gateway já descartou.
 */
export function QrPanel({
  qrCode,
  expired,
  pairing,
  onRefresh,
  onClose,
}: {
  qrCode: string;
  expired: boolean;
  pairing: boolean;
  onRefresh: () => void;
  onClose: () => void;
}) {
  return (
    <section className="max-w-lg space-y-3 rounded-lg border border-border bg-card p-4">
      <h2 className="text-lg font-semibold text-foreground">
        {expired ? 'O QR expirou' : 'Leia o QR no celular'}
      </h2>

      {expired ? (
        <p className="text-sm text-muted-foreground">
          Este código já não vale — o gateway gera um novo a cada poucos segundos. Gere outro para
          continuar o pareamento.
        </p>
      ) : (
        <>
          <p className="text-sm text-muted-foreground">
            No celular: WhatsApp → Aparelhos conectados → Conectar um aparelho.
          </p>
          <img src={imageSource(qrCode)} alt="QR Code de pareamento do WhatsApp" className="h-64 w-64" />
        </>
      )}

      <div className="flex gap-2">
        <Button variant={expired ? 'default' : 'outline'} onClick={onRefresh} disabled={pairing}>
          {pairing ? 'Gerando…' : 'Gerar outro QR'}
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Fechar o QR
        </Button>
      </div>
    </section>
  );
}
