package br.com.tonus.registration.internal;

import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentContact;
import br.com.tonus.registration.StudentSummary;
import br.com.tonus.registration.internal.domain.Student;
import br.com.tonus.registration.internal.domain.StudentRepository;
import br.com.tonus.registration.internal.domain.Teacher;
import br.com.tonus.registration.internal.domain.TeacherRepository;
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

    RegistrationFacadeImpl(StudentRepository studentRepository, TeacherRepository teacherRepository) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
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
    public List<StudentSummary> findActiveStudents() {
        return toSummaries(studentRepository.findByActiveTrue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummary> findStudentsByTeacher(Long teacherId) {
        return toSummaries(studentRepository.findByTeacherId(teacherId));
    }

    /**
     * Os nomes dos professores saem de UMA consulta, não de uma por aluno: esta
     * lista é percorrida inteira a cada abertura da agenda do dia, e um N+1 aqui
     * apareceria justamente na tela mais usada do estúdio.
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
