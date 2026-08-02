package br.com.tonus.billing.internal.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanEnrollmentRepository extends JpaRepository<PlanEnrollment, Long> {

    List<PlanEnrollment> findByStudentId(Long studentId);

    boolean existsByStudentIdAndPlanId(Long studentId, Long planId);

    /** O insumo do job diário: todo vínculo vivo do estúdio, numa consulta. */
    List<PlanEnrollment> findByActiveTrue();

    long countByPlanIdAndActiveTrue(Long planId);

    /**
     * Quantos alunos ativos cada plano tem, para a página inteira de uma vez.
     *
     * Existe porque a listagem mostra a contagem em toda linha: uma consulta por
     * plano é N+1 numa tela que o ADMIN abre para decidir desativar algo —
     * exatamente onde a contagem importa. Plano sem matrícula não aparece no
     * resultado, e quem chama trata a ausência como zero.
     */
    @Query("""
            SELECT e.planId AS planId, COUNT(e) AS total
            FROM PlanEnrollment e
            WHERE e.active = true AND e.planId IN :planIds
            GROUP BY e.planId
            """)
    List<PlanEnrollmentCount> countActiveByPlanIds(Collection<Long> planIds);

    interface PlanEnrollmentCount {

        Long getPlanId();

        long getTotal();
    }
}
