package br.com.tonus.scheduling.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Um aviso do estúdio para um dia. Pode suspender as aulas daquele dia ou
 * apenas informar.
 *
 * As duas coisas moram na mesma tabela porque são o mesmo ato — o estúdio
 * avisando todo mundo sobre um dia —, e separá-las obrigaria a agenda a
 * perguntar em dois lugares para montar um dia só.
 */
@Entity
@Table(name = "announcement")
public class Announcement extends BaseEntity {

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String description;

    @Column(name = "suspends_classes", nullable = false)
    private boolean suspendsClasses;

    protected Announcement() {
    }

    public Announcement(LocalDate date, String description, boolean suspendsClasses) {
        this.date = date;
        this.description = description;
        this.suspendsClasses = suspendsClasses;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public boolean suspendsClasses() {
        return suspendsClasses;
    }
}
