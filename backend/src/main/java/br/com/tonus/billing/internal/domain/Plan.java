package br.com.tonus.billing.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A plan from the studio's catalog: name and monthly fee.
 *
 * There is no DELETE, same decision as for student and teacher: a
 * deactivated plan drops out of listings and new enrollments, but keeps
 * explaining the invoices that were already issued with it.
 */
@Entity
@Table(name = "plan")
public class Plan extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "monthly_price", nullable = false)
    private BigDecimal monthlyPrice;

    @Column(nullable = false)
    private boolean active;

    protected Plan() {
    }

    public Plan(String name, BigDecimal monthlyPrice) {
        this.name = name;
        this.monthlyPrice = monthlyPrice;
        this.active = true;
    }

    public void update(String name, BigDecimal monthlyPrice) {
        this.name = name;
        this.monthlyPrice = monthlyPrice;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public boolean isActive() {
        return active;
    }
}
