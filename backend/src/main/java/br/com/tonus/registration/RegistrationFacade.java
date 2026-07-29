package br.com.tonus.registration;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * Nomes de vários alunos de uma vez, indexados por id.
     *
     * Existe para listagens que mostram aluno linha a linha (faturas, SPEC-008).
     * {@link #findStudentContact} não serve: seria uma consulta por linha e
     * entregaria telefone — dado pessoal — a quem só precisa do nome. Ids sem
     * aluno correspondente simplesmente não aparecem no mapa.
     *
     * Inclui aluno desativado de propósito: quem saiu do estúdio mantém
     * histórico de fatura (SPEC-002 não tem DELETE), e a tela continua tendo de
     * dizer de quem é a cobrança.
     */
    Map<Long, String> findStudentNames(Collection<Long> studentIds);

    List<StudentSummary> findActiveStudents();

    List<StudentSummary> findStudentsByTeacher(Long teacherId);
}
