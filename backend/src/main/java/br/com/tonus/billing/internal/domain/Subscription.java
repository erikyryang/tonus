package br.com.tonus.billing.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Assinatura mensal de um aluno: valor e dia de vencimento.
 *
 * studentId sem FK, mesma decisão de default_schedule.studentId (ver
 * docs/ddl.md): a integridade é garantida em código, via RegistrationFacade.
 */
@Entity
@Table(name = "subscription")
public class Subscription extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "monthly_amount", nullable = false)
    private BigDecimal monthlyAmount;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(nullable = false)
    private boolean active;

    protected Subscription() {
    }

    public Subscription(Long studentId, BigDecimal monthlyAmount, int dueDay) {
        this.studentId = studentId;
        this.monthlyAmount = monthlyAmount;
        this.dueDay = dueDay;
        this.active = true;
    }

    public void update(BigDecimal monthlyAmount, int dueDay) {
        this.monthlyAmount = monthlyAmount;
        this.dueDay = dueDay;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    /**
     * "Aniversário do pagamento": a data em que a baixa aconteceu vira o novo
     * dia de vencimento da assinatura.
     */
    public void realignDueDayTo(LocalDate paymentDate) {
        this.dueDay = paymentDate.getDayOfMonth();
    }

    /**
     * Resolve o vencimento para uma competência. dia_vencimento inexistente no
     * mês (31 em fevereiro) cai no último dia do mês.
     */
    public LocalDate dueDateFor(YearMonth referenceMonth) {
        int day = Math.min(dueDay, referenceMonth.lengthOfMonth());
        return referenceMonth.atDay(day);
    }

    public Long getStudentId() {
        return studentId;
    }

    public BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    public int getDueDay() {
        return dueDay;
    }

    public boolean isActive() {
        return active;
    }
}
