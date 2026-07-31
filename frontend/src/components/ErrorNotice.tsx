import { Button } from '@/components/ui/button';

/**
 * Falhou, e o que fazer a respeito.
 *
 * O par "o que deu errado" + "tentar de novo" é sempre o mesmo, mas a frase
 * não: uma listagem que não carrega se explica pelo erro da API, enquanto uma
 * consulta secundária precisa dizer o que deixou de funcionar por causa dela —
 * a mensagem vem de fora por isso.
 */
export function ErrorNotice({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="space-y-2">
      <p role="alert" className="text-sm text-red-600">
        {message}
      </p>
      <Button variant="outline" size="sm" onClick={onRetry}>
        Tentar de novo
      </Button>
    </div>
  );
}
