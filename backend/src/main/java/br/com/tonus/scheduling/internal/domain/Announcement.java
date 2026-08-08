package br.com.tonus.scheduling.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * A studio notice for a day. It can suspend that day's classes or
 * just inform.
 *
 * The two things live in the same table because they are the same act — the studio
 * notifying everyone about a day —, and separating them would force the schedule to
 * query in two places to build a single day.
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
