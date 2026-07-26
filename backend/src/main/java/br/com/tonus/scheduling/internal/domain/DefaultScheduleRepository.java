package br.com.tonus.scheduling.internal.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefaultScheduleRepository extends JpaRepository<DefaultSchedule, Long> {

    boolean existsByStudentId(Long studentId);

    Optional<DefaultSchedule> findByStudentId(Long studentId);
}
