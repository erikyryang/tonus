package br.com.tonus.auth.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String login;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "teacher_id")
    private Long teacherId;

    protected User() {
    }

    public User(String login, String password, Role role, Long studentId, Long teacherId) {
        this.login = login;
        this.password = password;
        this.role = role;
        this.studentId = studentId;
        this.teacherId = teacherId;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getTeacherId() {
        return teacherId;
    }
}
