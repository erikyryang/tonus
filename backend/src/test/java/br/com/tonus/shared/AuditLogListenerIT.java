package br.com.tonus.shared;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.tonus.AbstractIntegrationTest;
import br.com.tonus.shared.internal.domain.AuditLog;
import br.com.tonus.shared.internal.domain.AuditLogRepository;
import br.com.tonus.shared.support.SampleAuditedEntity;
import br.com.tonus.shared.support.SampleAuditedEntityRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestClockConfig.class)
class AuditLogListenerIT extends AbstractIntegrationTest {

    @Autowired
    private SampleAuditedEntityRepository entityRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void savingNewAuditedEntityCreatesAuditLogEntry() {
        SampleAuditedEntity saved = entityRepository.saveAndFlush(new SampleAuditedEntity("foo"));

        List<AuditLog> logs = auditLogRepository.findByEntityNameAndEntityId(
                "SampleAuditedEntity", saved.getId().toString());

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("CREATE");
        assertThat(logs.get(0).getChangedAt()).isEqualTo(TestClockConfig.FIXED_INSTANT);
    }

    @Test
    void updatingAuditedEntityCreatesAdditionalAuditLogEntry() {
        SampleAuditedEntity saved = entityRepository.saveAndFlush(new SampleAuditedEntity("foo"));

        saved.setName("bar");
        entityRepository.saveAndFlush(saved);

        List<AuditLog> logs = auditLogRepository.findByEntityNameAndEntityId(
                "SampleAuditedEntity", saved.getId().toString());

        assertThat(logs).hasSize(2);
        assertThat(logs).anyMatch(log -> log.getAction().equals("UPDATE"));
    }

    /**
     * The audit write runs in its own transaction (REQUIRES_NEW): a business
     * change that later rolls back still leaves the trace of the attempt. For
     * a compliance log, an attempted-and-rejected modification is exactly
     * what should be findable.
     */
    @Test
    void rolledBackChangeStillLeavesAnAuditTrace() {
        Long entityId = transactionTemplate.execute(status -> {
            SampleAuditedEntity saved = entityRepository.saveAndFlush(new SampleAuditedEntity("foo"));
            status.setRollbackOnly();
            return saved.getId();
        });

        List<AuditLog> logs = auditLogRepository.findByEntityNameAndEntityId(
                "SampleAuditedEntity", entityId.toString());

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("CREATE");
    }
}
