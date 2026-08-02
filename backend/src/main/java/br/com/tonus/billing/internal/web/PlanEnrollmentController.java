package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.BillingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O vínculo aluno → plano vive aqui, e não em {@code /students/{id}/plans}:
 * matrícula é de billing, e pendurá-la sob o caminho do cadastro sugeriria que
 * o registration a conhece — ele não conhece cobrança nenhuma.
 *
 * A listagem não é paginada porque não é listagem: é a resposta a "quais são os
 * planos deste aluno", limitada pelo tamanho do catálogo.
 */
@RestController
@RequestMapping("/plan-enrollments")
class PlanEnrollmentController {

    private final BillingService billingService;

    PlanEnrollmentController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping
    ResponseEntity<PlanEnrollmentResponse> create(@Valid @RequestBody PlanEnrollmentRequest request) {
        PlanEnrollmentResponse response = PlanEnrollmentResponse.from(
                billingService.enroll(request.studentId(), request.planId()));
        return ResponseEntity.created(URI.create("/plan-enrollments/" + response.id())).body(response);
    }

    @GetMapping
    List<PlanEnrollmentResponse> list(@RequestParam Long studentId) {
        return billingService.findEnrollmentsOfStudent(studentId).stream()
                .map(PlanEnrollmentResponse::from)
                .toList();
    }

    @PatchMapping("/{id}/activate")
    PlanEnrollmentResponse activate(@PathVariable Long id) {
        return PlanEnrollmentResponse.from(billingService.setEnrollmentActive(id, true));
    }

    @PatchMapping("/{id}/deactivate")
    PlanEnrollmentResponse deactivate(@PathVariable Long id) {
        return PlanEnrollmentResponse.from(billingService.setEnrollmentActive(id, false));
    }
}
