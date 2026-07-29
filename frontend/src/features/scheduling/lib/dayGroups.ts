import type { DayScheduleEntry } from '../api/schedulingApi';

export interface TeacherGroup {
  teacherId: number;
  teacherName: string;
  entries: DayScheduleEntry[];
}

export interface HourBlock {
  time: string;
  groups: TeacherGroup[];
}

/**
 * Hora → professor → alunos. O grupo emerge de professor + hora, como no
 * domínio: não existe turma como entidade, então não há id de turma para o
 * front agrupar por. Só horas com aula viram bloco — painel de recepção não
 * mostra linha vazia das 10h só porque as 9h e as 11h existem.
 */
export function groupByHour(entries: DayScheduleEntry[]): HourBlock[] {
  const byHour = new Map<string, Map<number, TeacherGroup>>();

  for (const entry of entries) {
    const groups = byHour.get(entry.time) ?? new Map<number, TeacherGroup>();
    byHour.set(entry.time, groups);

    const group = groups.get(entry.teacherId) ?? {
      teacherId: entry.teacherId,
      teacherName: entry.teacherName,
      entries: [],
    };
    group.entries.push(entry);
    groups.set(entry.teacherId, group);
  }

  return [...byHour.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([time, groups]) => ({
      time,
      groups: [...groups.values()]
        .sort((a, b) => a.teacherName.localeCompare(b.teacherName, 'pt-BR'))
        .map((group) => ({
          ...group,
          entries: [...group.entries].sort((a, b) => a.studentName.localeCompare(b.studentName, 'pt-BR')),
        })),
    }));
}
