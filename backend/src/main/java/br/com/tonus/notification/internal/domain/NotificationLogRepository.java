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
     * DELETE em massa, e não {@code deleteAll} sobre entidades carregadas: a
     * poda roda sobre meses de histórico e trazer tudo para a memória para
     * apagar linha a linha não tem contrapartida — não há callback de ciclo de
     * vida em {@link NotificationLog} que se perca.
     */
    @Modifying
    @Query("DELETE FROM NotificationLog l WHERE l.sentAt < :threshold")
    int deleteSentBefore(@Param("threshold") Instant threshold);
}
