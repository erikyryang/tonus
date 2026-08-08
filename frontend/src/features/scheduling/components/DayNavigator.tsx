import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { addDays, formatWeekday, todayIsoDate } from '@/lib/dates';
import { formatDate } from '@/lib/formatters';

export function DayNavigator({ date, onChange }: { date: string; onChange: (date: string) => void }) {
  return (
    <nav aria-label="Navegação de data" className="flex flex-wrap items-center gap-2">
      <Button variant="outline" size="sm" aria-label="Dia anterior" onClick={() => onChange(addDays(date, -1))}>
        ‹
      </Button>
      <Button
        variant="outline"
        size="sm"
        disabled={date === todayIsoDate()}
        onClick={() => onChange(todayIsoDate())}
      >
        Hoje
      </Button>
      <Button variant="outline" size="sm" aria-label="Próximo dia" onClick={() => onChange(addDays(date, 1))}>
        ›
      </Button>

      <Input
        type="date"
        aria-label="Data da agenda"
        className="h-9 w-44"
        value={date}
        onChange={(event) => {
          if (event.target.value) onChange(event.target.value);
        }}
      />

      <p className="text-sm text-muted-foreground">
        {formatWeekday(date)}, {formatDate(date)}
      </p>
    </nav>
  );
}
