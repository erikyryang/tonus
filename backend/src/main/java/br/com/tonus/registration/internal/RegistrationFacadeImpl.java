package br.com.tonus.registration.internal;

import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentContact;
import br.com.tonus.registration.StudentSummary;
import br.com.tonus.registration.internal.domain.Student;
import br.com.tonus.registration.internal.domain.StudentRepository;
import br.com.tonus.registration.internal.domain.Teacher;
import br.com.tonus.registration.internal.domain.TeacherRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RegistrationFacadeImpl implements RegistrationFacade {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final Clock clock;

    RegistrationFacadeImpl(StudentRepository studentRepository, TeacherRepository teacherRepository, Clock clock) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentContact> findStudentContact(Long studentId) {
        return studentRepository.findById(studentId)
                .map(s -> new StudentContact(s.getId(), s.getName(), s.getPhone()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean studentExists(Long studentId) {
        return studentId != null && studentRepository.existsById(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean teacherExists(Long teacherId) {
        return teacherId != null && teacherRepository.existsById(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean studentIsActive(Long studentId) {
        return studentId != null && studentRepository.existsByIdAndActiveTrue(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean teacherIsActive(Long teacherId) {
        return teacherId != null && teacherRepository.existsByIdAndActiveTrue(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findStudentNames(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findStudentPhones(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getPhone));
    }

    /**
     * The registration instant becomes a day in the studio's timezone, and not in UTC: a
     * registration made at 9:30 PM Brasília time is on day 5 for whoever was there, and in
     * UTC it would be day 6 — the monthly fee would be due one day later than it
     * should, for every student registered late at night.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> findRegistrationDate(Long studentId) {
        return studentRepository.findById(studentId)
                .map(student -> LocalDate.ofInstant(student.getCreatedAt(), clock.getZone()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, LocalDate> findRegistrationDates(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId,
                        student -> LocalDate.ofInstant(student.getCreatedAt(), clock.getZone())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentSummary> findStudentSummary(Long studentId) {
        return studentRepository.findById(studentId)
                .map(student -> toSummaries(List.of(student)).getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummary> findActiveStudents() {
        return toSummaries(studentRepository.findByActiveTrue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummary> findStudentsByTeacher(Long teacherId) {
        return toSummaries(studentRepository.findByTeacherId(teacherId));
    }

    /**
     * The teachers' names come from ONE query, not one per student: this
     * list is traversed entirely on every opening of the day's schedule, and an N+1 here
     * would show up precisely on the most used screen of the studio.
     */
    private List<StudentSummary> toSummaries(List<Student> students) {
        Set<Long> teacherIds = students.stream().map(Student::getTeacherId).collect(Collectors.toSet());
        Map<Long, String> teacherNames = teacherRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Teacher::getName));
        return students.stream()
                .map(student -> new StudentSummary(student.getId(), student.getName(), student.getTeacherId(),
                        teacherNames.get(student.getTeacherId()), student.isActive()))
                .toList();
    }
}
