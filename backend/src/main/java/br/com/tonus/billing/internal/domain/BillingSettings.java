package br.com.tonus.billing.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

/**
 * A configuração de cobrança do estúdio. Linha única, com id fixo.
 *
 * Não herda de {@code BaseEntity} porque o id não é gerado: ele é 1, sempre, e
 * o {@code CHECK (id = 1)} do banco é quem garante que não existe uma segunda
 * linha para discordar desta.
 */
@Entity
@Table(name = "billing_settings")
@EntityListeners(AuditingEntityListener.class)
public class BillingSettings {

    /** O id da única linha que pode existir. */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "multi_plan_discount_percent", nullable = false)
    private BigDecimal multiPlanDiscountPercent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BillingSettings() {
    }

    public void update(BigDecimal multiPlanDiscountPercent) {
        this.multiPlanDiscountPercent = multiPlanDiscountPercent;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getMultiPlanDiscountPercent() {
        return multiPlanDiscountPercent;
    }
}
