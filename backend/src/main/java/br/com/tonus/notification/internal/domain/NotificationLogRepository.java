package br.com.tonus.notification.internal.domain;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByTemplateAndStudentIdAndReferenceKey(
            MessageTemplate template, Long studentId, String referenceKey);

    /**
     * Bulk DELETE, not {@code deleteAll} over loaded entities: the pruning
     * runs over months of history and bringing it all into memory to delete
     * row by row has no upside — there's no lifecycle callback on {@link
     * NotificationLog} that would be missed.
     */
    @Modifying
    @Query("DELETE FROM NotificationLog l WHERE l.sentAt < :threshold")
    int deleteSentBefore(@Param("threshold") Instant threshold);
}
