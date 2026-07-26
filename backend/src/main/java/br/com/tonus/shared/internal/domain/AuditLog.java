package br.com.tonus.shared.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_name", nullable = false)
    private String entityName;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(nullable = false)
    private String action;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected AuditLog() {
    }

    public AuditLog(String entityName, String entityId, String action, Instant changedAt) {
        this.entityName = entityName;
        this.entityId = entityId;
        this.action = action;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
