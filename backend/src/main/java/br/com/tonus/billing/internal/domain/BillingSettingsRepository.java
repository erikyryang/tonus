package br.com.tonus.billing.internal.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingSettingsRepository extends JpaRepository<BillingSettings, Long> {
}
