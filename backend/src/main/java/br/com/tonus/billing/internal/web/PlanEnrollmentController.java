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
 * The student → plan link lives here, not in {@code /students/{id}/plans}:
 * enrollment belongs to billing, and hanging it under the registration path
 * would suggest that registration knows about it — it doesn't know about
 * billing at all.
 *
 * The listing isn't paginated because it isn't a listing: it's the answer to
 * "what are this student's plans", bounded by the catalog's size.
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
