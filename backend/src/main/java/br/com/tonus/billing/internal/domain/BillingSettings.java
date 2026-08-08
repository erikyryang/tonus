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
 * The studio's billing configuration. Single row, with a fixed id.
 *
 * Doesn't extend {@code BaseEntity} because the id isn't generated: it's
 * always 1, and the database's {@code CHECK (id = 1)} is what guarantees
 * there's no second row to disagree with this one.
 */
@Entity
@Table(name = "billing_settings")
@EntityListeners(AuditingEntityListener.class)
public class BillingSettings {

    /** The id of the only row that can exist. */
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
