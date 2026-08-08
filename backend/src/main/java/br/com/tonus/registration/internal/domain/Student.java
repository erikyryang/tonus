package br.com.tonus.registration.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The teacher is referenced by id (and not by @ManyToOne) to keep the
 * entity simple and the link explicit — integrity is guaranteed by the database
 * FK.
 */
@Entity
@Table(name = "student")
public class Student extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false)
    private boolean active = true;

    protected Student() {
    }

    public Student(String name, String phone, Long teacherId) {
        this.name = name;
        this.phone = phone;
        this.teacherId = teacherId;
        this.active = true;
    }

    public void update(String name, String phone, Long teacherId) {
        this.name = name;
        this.phone = phone;
        this.teacherId = teacherId;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public boolean isActive() {
        return active;
    }
}
