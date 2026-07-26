package br.com.tonus.shared.internal;

import br.com.tonus.shared.Audited;
import br.com.tonus.shared.BaseEntity;
import br.com.tonus.shared.internal.domain.AuditLog;
import br.com.tonus.shared.internal.domain.AuditLogRepository;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import java.time.Clock;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class AuditListener {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditListener(@Lazy AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
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
        auditLogRepository.save(new AuditLog(entity.getClass().getSimpleName(), entityId, action, clock.instant()));
    }
}
