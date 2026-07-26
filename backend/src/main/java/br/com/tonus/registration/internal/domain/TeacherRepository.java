package br.com.tonus.registration.internal.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Page<Teacher> findByActive(boolean active, Pageable pageable);

    boolean existsByIdAndActiveTrue(Long id);
}
