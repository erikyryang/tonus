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
 * O dia de vencimento é o dia do mês em que o aluno se cadastrou. Fica copiado
 * aqui, e não é lido do cadastro a cada uso, porque a assinatura é quem responde
 * "quando vence" — e billing não pode depender do registration para resolver a
 * data de cada fatura que gera. É cópia de um valor que não muda: o cadastro do
 * aluno não retroage.
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

    /**
     * O dia de vencimento não entra aqui: ele vem do dia do cadastro do aluno e
     * não muda enquanto a assinatura for do mesmo aluno (SPEC-004).
     */
    public void update(BigDecimal monthlyAmount) {
        this.monthlyAmount = monthlyAmount;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    /**
     * Resolve o vencimento para uma competência. dia_vencimento inexistente no
     * mês (31 em fevereiro) cai no último dia do mês.
     *
     * O ajuste é feito aqui, e o dia guardado continua sendo o do cadastro:
     * gravar 28 para quem se cadastrou no dia 31 perderia a informação em
     * março, que tem 31.
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
