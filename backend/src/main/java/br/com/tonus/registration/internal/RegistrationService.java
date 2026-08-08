package br.com.tonus.registration.internal;

import br.com.tonus.registration.StudentDeactivated;
import br.com.tonus.registration.TeacherDeactivated;
import br.com.tonus.registration.internal.domain.Student;
import br.com.tonus.registration.internal.domain.StudentRepository;
import br.com.tonus.registration.internal.domain.Teacher;
import br.com.tonus.registration.internal.domain.TeacherRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ApplicationEventPublisher events;

    public RegistrationService(TeacherRepository teacherRepository,
                                StudentRepository studentRepository,
                                ApplicationEventPublisher events) {
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.events = events;
    }

    @Transactional
    public Teacher createTeacher(String name, String phone) {
        return teacherRepository.save(new Teacher(name, phone));
    }

    @Transactional
    public Teacher updateTeacher(Long id, String name, String phone) {
        Teacher teacher = requireTeacher(id);
        teacher.update(name, phone);
        return teacherRepository.save(teacher);
    }

    @Transactional
    public Teacher deactivateTeacher(Long id) {
        Teacher teacher = requireTeacher(id);
        teacher.deactivate();
        Teacher saved = teacherRepository.save(teacher);
        events.publishEvent(new TeacherDeactivated(id));
        return saved;
    }

    @Transactional
    public Teacher activateTeacher(Long id) {
        Teacher teacher = requireTeacher(id);
        teacher.activate();
        return teacherRepository.save(teacher);
    }

    @Transactional(readOnly = true)
    public Teacher findTeacher(Long id) {
        return requireTeacher(id);
    }

    @Transactional(readOnly = true)
    public Page<Teacher> findTeachers(Boolean active, Pageable pageable) {
        Pageable stable = withStableSort(pageable);
        return active == null
                ? teacherRepository.findAll(stable)
                : teacherRepository.findByActive(active, stable);
    }

    @Transactional
    public Student createStudent(String name, String phone, Long teacherId) {
        requireActiveTeacher(teacherId);
        return studentRepository.save(new Student(name, phone, teacherId));
    }

    /**
     * The student is looked up before the teacher on purpose: if the student does not exist,
     * the correct response is 404 for the requested resource, not 400 for the submitted body.
     */
    @Transactional
    public Student updateStudent(Long id, String name, String phone, Long teacherId) {
        Student student = requireStudent(id);
        if (!teacherId.equals(student.getTeacherId())) {
            requireActiveTeacher(teacherId);
        }
        student.update(name, phone, teacherId);
        return studentRepository.save(student);
    }

    /**
     * Deactivation is idempotent: calling it again on an already inactive student returns the
     * same state, without error.
     */
    @Transactional
    public Student deactivateStudent(Long id) {
        Student student = requireStudent(id);
        student.deactivate();
        Student saved = studentRepository.save(student);
        events.publishEvent(new StudentDeactivated(id));
        return saved;
    }

    /**
     * Reactivating requires that the linked teacher still be active — otherwise the
     * student would come back already pointing to someone who left the studio.
     */
    @Transactional
    public Student activateStudent(Long id) {
        Student student = requireStudent(id);
        requireActiveTeacher(student.getTeacherId());
        student.activate();
        return studentRepository.save(student);
    }

    @Transactional(readOnly = true)
    public Student findStudent(Long id) {
        return requireStudent(id);
    }

    @Transactional(readOnly = true)
    public Page<Student> findStudents(Boolean active, Pageable pageable) {
        Pageable stable = withStableSort(pageable);
        return active == null
                ? studentRepository.findAll(stable)
                : studentRepository.findByActive(active, stable);
    }

    /**
     * Pagination without a total ORDER BY is non-deterministic in Postgres: the same
     * query can repeat or omit rows between pages. The id enters as a
     * tie-breaking criterion for any ordering requested by the client.
     */
    private Pageable withStableSort(Pageable pageable) {
        if (pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(Sort.by("id")));
    }

    private Teacher requireTeacher(Long id) {
        return teacherRepository.findById(id)
                .orElseThrow(() -> new RegistrationNotFoundException("Professor não encontrado: " + id));
    }

    private Student requireStudent(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new RegistrationNotFoundException("Aluno não encontrado: " + id));
    }

    private void requireActiveTeacher(Long teacherId) {
        if (!teacherRepository.existsById(teacherId)) {
            throw new UnknownTeacherException(teacherId);
        }
        if (!teacherRepository.existsByIdAndActiveTrue(teacherId)) {
            throw new InactiveTeacherException(teacherId);
        }
    }
}
