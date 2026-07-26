package br.com.tonus.shared.support;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleAuditedEntityRepository extends JpaRepository<SampleAuditedEntity, Long> {
}
