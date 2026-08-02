package br.com.tonus.billing.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * O vínculo de um aluno com um plano.
 *
 * Desativar em vez de apagar, como no resto do sistema: "esse aluno já teve
 * esse plano" é pergunta de cobrança, e a linha apagada não responde.
 *
 * {@code studentId} sem FK, mesma decisão de {@code subscription.student_id}
 * (ver docs/ddl.md); {@code planId} tem FK de verdade, porque plano e matrícula
 * são do mesmo módulo.
 */
@Entity
@Table(name = "plan_enrollment")
public class PlanEnrollment extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false)
    private boolean active;

    protected PlanEnrollment() {
    }

    public PlanEnrollment(Long studentId, Long planId) {
        this.studentId = studentId;
        this.planId = planId;
        this.active = true;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getPlanId() {
        return planId;
    }

    public boolean isActive() {
        return active;
    }
}
