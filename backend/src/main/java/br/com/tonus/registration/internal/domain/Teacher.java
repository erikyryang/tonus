package br.com.tonus.registration.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "teacher")
public class Teacher extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    protected Teacher() {
    }

    public Teacher(String name, String phone) {
        this.name = name;
        this.phone = phone;
        this.active = true;
    }

    public void update(String name, String phone) {
        this.name = name;
        this.phone = phone;
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

    public boolean isActive() {
        return active;
    }
}
