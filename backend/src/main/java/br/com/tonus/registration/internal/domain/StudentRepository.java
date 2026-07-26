package br.com.tonus.registration.internal.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByActiveTrue();

    List<Student> findByTeacherId(Long teacherId);

    Page<Student> findByActive(boolean active, Pageable pageable);

    boolean existsByIdAndActiveTrue(Long id);
}
