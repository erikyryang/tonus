package br.com.tonus.scheduling.internal.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ScheduleExceptionRepository
        extends JpaRepository<ScheduleException, Long>, JpaSpecificationExecutor<ScheduleException> {

    boolean existsByStudentIdAndDate(Long studentId, LocalDate date);

    /** O que sai da agenda de D: toda exceção marcada naquele dia. */
    List<ScheduleException> findByDate(LocalDate date);

    /** O que entra na agenda de D: as remarcações cujo destino é D. */
    List<ScheduleException> findByNewDate(LocalDate newDate);

    /**
     * As exceções de um aluno num intervalo — as duas pontas de uma vez.
     *
     * `date` é de onde a aula saiu e `newDate` é para onde ela foi, e a janela
     * do aluno precisa das duas: uma aula pode ter saído de dentro da janela
     * para fora dela, ou entrado de fora para dentro. Buscar por intervalo em
     * vez de por dia é o que faz a janela de sete dias custar uma consulta em
     * vez de sete.
     */
    List<ScheduleException> findByStudentIdAndDateBetween(Long studentId, LocalDate from, LocalDate to);

    List<ScheduleException> findByStudentIdAndNewDateBetween(Long studentId, LocalDate from, LocalDate to);

    /**
     * Usado ao apagar o horário padrão do aluno. A exceção é indexada por
     * (student_id, date), sem qualquer vínculo com o id do horário — sem esta
     * limpeza, uma exceção antiga sobreviveria à exclusão e voltaria a
     * suprimir um horário novo criado depois para o mesmo aluno no mesmo dia.
     */
    void deleteByStudentId(Long studentId);
}
