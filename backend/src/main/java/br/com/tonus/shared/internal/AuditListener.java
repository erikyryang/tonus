package br.com.tonus.shared.internal;

import br.com.tonus.shared.Audited;
import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import java.time.Clock;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class AuditListener {

    private final AuditLogWriter auditLogWriter;
    private final Clock clock;

    public AuditListener(@Lazy AuditLogWriter auditLogWriter, Clock clock) {
        this.auditLogWriter = auditLogWriter;
        this.clock = clock;
    }

    @PostPersist
    public void onPersist(Object entity) {
        record(entity, "CREATE");
    }

    @PostUpdate
    public void onUpdate(Object entity) {
        record(entity, "UPDATE");
    }

    private void record(Object entity, String action) {
        if (!entity.getClass().isAnnotationPresent(Audited.class)) {
            return;
        }
        String entityId = entity instanceof BaseEntity base ? String.valueOf(base.getId()) : "unknown";
        // REQUIRES_NEW via the writer: the audit commits even if the business
        // transaction rolls back, so an attempted change leaves its trace.
        auditLogWriter.record(entity.getClass().getSimpleName(), entityId, action, clock.instant());
    }
}
