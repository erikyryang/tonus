package br.com.tonus.registration;

import java.util.List;
import java.util.Optional;

/**
 * Contrato do módulo registration para os demais módulos (scheduling, billing,
 * notification). Consulta síncrona apenas — efeito colateral entre módulos é
 * por evento.
 */
public interface RegistrationFacade {

    Optional<StudentContact> findStudentContact(Long studentId);

    boolean studentExists(Long studentId);

    boolean teacherExists(Long teacherId);

    /**
     * Aluno existe e continua no estúdio. Separado de {@link #studentExists}
     * porque quem consulta precisa distinguir "não existe" de "saiu" — as duas
     * situações levam a mensagens e decisões diferentes.
     */
    boolean studentIsActive(Long studentId);

    boolean teacherIsActive(Long teacherId);

    List<StudentSummary> findActiveStudents();

    List<StudentSummary> findStudentsByTeacher(Long teacherId);
}
