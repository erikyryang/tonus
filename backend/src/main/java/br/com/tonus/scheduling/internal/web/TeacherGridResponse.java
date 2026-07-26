package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.ScheduleEntry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A grade do professor: a agenda dele agrupada por hora.
 *
 * O agrupamento é feito aqui, na borda, e não no banco: o "grupo" não é uma
 * entidade, é só o formato em que o professor lê a própria agenda.
 */
public record TeacherGridResponse(LocalDate date, List<Slot> slots) {

    public static TeacherGridResponse of(LocalDate date, List<ScheduleEntry> entries) {
        Map<LocalTime, List<Student>> byTime = new TreeMap<>();
        for (ScheduleEntry entry : entries) {
            byTime.computeIfAbsent(entry.time(), time -> new ArrayList<>())
                    .add(new Student(entry.studentId(), entry.studentName()));
        }
        return new TeacherGridResponse(date, byTime.entrySet().stream()
                .map(slot -> new Slot(slot.getKey(), slot.getValue()))
                .toList());
    }

    public record Slot(LocalTime time, List<Student> students) {
    }

    /**
     * Só id e nome: o professor não precisa do telefone do aluno para dar aula,
     * e dado que não trafega não vaza (minimização, LGPD).
     */
    public record Student(Long studentId, String studentName) {
    }
}
