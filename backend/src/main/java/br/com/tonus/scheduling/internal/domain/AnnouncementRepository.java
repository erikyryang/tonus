package br.com.tonus.scheduling.internal.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AnnouncementRepository
        extends JpaRepository<Announcement, Long>, JpaSpecificationExecutor<Announcement> {

    boolean existsByDate(LocalDate date);

    /**
     * Só o comunicado que suspende apaga o dia. O que apenas informa não entra
     * nesta pergunta — é a diferença entre avisar e cancelar.
     */
    boolean existsByDateAndSuspendsClassesTrue(LocalDate date);

    /** A data é UNIQUE: no máximo um comunicado por dia. */
    Optional<Announcement> findByDate(LocalDate date);

    /** Os comunicados de um intervalo, para quem expande vários dias de uma vez. */
    List<Announcement> findByDateBetween(LocalDate from, LocalDate to);
}
