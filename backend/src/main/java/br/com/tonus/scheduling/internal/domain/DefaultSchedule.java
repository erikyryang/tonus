package br.com.tonus.scheduling.internal.domain;

import br.com.tonus.shared.Audited;
import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalTime;

/**
 * Horário recorrente do aluno, válido de segunda a sexta.
 *
 * Auditado por decisão de arquitetura (docs/ddl.md): mudar o horário de um
 * aluno muda a agenda de todo mundo daquele horário, e depois ninguém lembra
 * quem mudou o quê.
 */
@Entity
@Table(name = "default_schedule")
@Audited
public class DefaultSchedule extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "time_of_day", nullable = false)
    private LocalTime time;

    protected DefaultSchedule() {
    }

    public DefaultSchedule(Long studentId, LocalTime time) {
        this.studentId = studentId;
        this.time = time;
    }

    public void update(Long studentId, LocalTime time) {
        this.studentId = studentId;
        this.time = time;
    }

    public Long getStudentId() {
        return studentId;
    }

    public LocalTime getTime() {
        return time;
    }
}
