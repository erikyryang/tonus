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
     * Usado ao apagar o horário padrão do aluno. A exceção é indexada por
     * (student_id, date), sem qualquer vínculo com o id do horário — sem esta
     * limpeza, uma exceção antiga sobreviveria à exclusão e voltaria a
     * suprimir um horário novo criado depois para o mesmo aluno no mesmo dia.
     */
    void deleteByStudentId(Long studentId);
}
