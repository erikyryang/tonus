package br.com.tonus.shared.internal;

import br.com.tonus.shared.internal.domain.AuditLog;
import br.com.tonus.shared.internal.domain.AuditLogRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit row in its own transaction.
 *
 * Lives outside {@link AuditListener} because the listener is a JPA callback
 * that fires inside the business transaction, and a self-invoked
 * {@code @Transactional} would never go through the proxy. With REQUIRES_NEW
 * the audit commits on its own: a business change that later rolls back still
 * leaves the trace of the attempt — for a compliance log, an attempted-and-
 * rejected modification is exactly what should be findable.
 */
@Component
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    AuditLogWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityName, String entityId, String action, Instant changedAt) {
        auditLogRepository.save(new AuditLog(entityName, entityId, action, changedAt));
    }
}