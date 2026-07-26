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
     * O aluno é buscado antes do professor de propósito: se o aluno não existe,
     * a resposta certa é 404 do recurso pedido, não 400 do corpo enviado.
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
     * Desativação é idempotente: chamar de novo num aluno já inativo devolve o
     * mesmo estado, sem erro.
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
     * Reativar exige que o professor vinculado ainda esteja ativo — senão o
     * aluno voltaria já apontando para alguém que saiu do estúdio.
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
     * Paginação sem ORDER BY total é não-determinística no Postgres: a mesma
     * consulta pode repetir ou omitir linhas entre páginas. O id entra como
     * critério de desempate de qualquer ordenação pedida pelo cliente.
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
