package br.com.tonus.scheduling.internal.domain;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HolidayRepository extends JpaRepository<Holiday, Long>, JpaSpecificationExecutor<Holiday> {

    boolean existsByDate(LocalDate date);

    /** A data é UNIQUE: no máximo um feriado por dia. */
    Optional<Holiday> findByDate(LocalDate date);
}
