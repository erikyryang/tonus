package br.com.tonus.registration.internal;

import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentContact;
import br.com.tonus.registration.StudentSummary;
import br.com.tonus.registration.internal.domain.Student;
import br.com.tonus.registration.internal.domain.StudentRepository;
import br.com.tonus.registration.internal.domain.TeacherRepository;
import java.util.List;
import java.util.Optional;
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
    public List<StudentSummary> findActiveStudents() {
        return studentRepository.findByActiveTrue().stream().map(RegistrationFacadeImpl::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummary> findStudentsByTeacher(Long teacherId) {
        return studentRepository.findByTeacherId(teacherId).stream().map(RegistrationFacadeImpl::toSummary).toList();
    }

    private static StudentSummary toSummary(Student student) {
        return new StudentSummary(student.getId(), student.getName(), student.getTeacherId(), student.isActive());
    }
}
