package br.com.tonus.auth.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLogin(String login);

    List<User> findByStudentId(Long studentId);

    /** Uma consulta para a página inteira de alunos, e não uma por linha (SPEC-009). */
    List<User> findByStudentIdIn(Collection<Long> studentIds);

    List<User> findByTeacherId(Long teacherId);
}
