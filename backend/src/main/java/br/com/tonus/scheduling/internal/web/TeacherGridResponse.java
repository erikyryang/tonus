package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.TeacherClassEntry;
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

    public static TeacherGridResponse of(LocalDate date, List<TeacherClassEntry> entries) {
        Map<LocalTime, List<Student>> byTime = new TreeMap<>();
        for (TeacherClassEntry entry : entries) {
            byTime.computeIfAbsent(entry.time(), time -> new ArrayList<>())
                    .add(new Student(entry.studentId(), entry.studentName(), entry.studentPhone()));
        }
        return new TeacherGridResponse(date, byTime.entrySet().stream()
                .map(slot -> new Slot(slot.getKey(), slot.getValue()))
                .toList());
    }

    public record Slot(LocalTime time, List<Student> students) {
    }

    /**
     * Id, nome e telefone. O telefone está aqui por finalidade, não por
     * conveniência: quem lê esta resposta é o professor daquele aluno, e falar
     * com ele — desmarcar, avisar de atraso — é o atendimento, não um extra.
     *
     * A minimização continua valendo no que não mudou: nenhum outro caminho da
     * agenda carrega telefone, e este só carrega o dos alunos do professor
     * logado (o id vem do token). Nunca logar o valor.
     */
    public record Student(Long studentId, String studentName, String studentPhone) {
    }
}
