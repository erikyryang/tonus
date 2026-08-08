package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.TeacherClassEntry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The teacher's grid: their schedule grouped by time.
 *
 * The grouping is done here, at the edge, and not in the database: the "group" is not an
 * entity, it is just the format in which the teacher reads their own schedule.
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
     * Id, name and phone. The phone is here for a purpose, not for
     * convenience: whoever reads this response is that student's teacher, and speaking
     * with them — canceling, notifying of a delay — is the service, not an extra.
     *
     * Minimization still holds for what did not change: no other schedule
     * path carries phone, and this one only carries that of the logged-in
     * teacher's students (the id comes from the token). Never log the value.
     */
    public record Student(Long studentId, String studentName, String studentPhone) {
    }
}
