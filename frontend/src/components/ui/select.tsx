import { forwardRef } from 'react';
import { cn } from '@/lib/utils';

/**
 * Select nativo, pelo mesmo motivo do Dialog e do ActionMenu: um combobox
 * próprio não justifica dependência de UI nova, e o nativo é o que já funciona
 * com teclado, leitor de tela e o seletor do celular.
 */
export const Select = forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, ...props }, ref) => (
    <select
      ref={ref}
      className={cn(
        'flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50',
        className,
      )}
      {...props}
    />
  ),
);
Select.displayName = 'Select';
