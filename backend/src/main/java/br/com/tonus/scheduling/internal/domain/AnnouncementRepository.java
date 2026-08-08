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
     * Only the announcement that suspends erases the day. The one that only informs does not enter
     * this question — it is the difference between notifying and canceling.
     */
    boolean existsByDateAndSuspendsClassesTrue(LocalDate date);

    /** The date is UNIQUE: at most one announcement per day. */
    Optional<Announcement> findByDate(LocalDate date);

    /** The announcements of an interval, for whoever expands several days at once. */
    List<Announcement> findByDateBetween(LocalDate from, LocalDate to);
}
