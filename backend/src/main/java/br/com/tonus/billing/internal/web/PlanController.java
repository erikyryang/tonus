package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.BillingService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plans")
class PlanController {

    private final BillingService billingService;

    PlanController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping
    ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanRequest request) {
        PlanResponse response = PlanResponse.from(billingService.createPlan(request.name(), request.monthlyPrice()));
        return ResponseEntity.created(URI.create("/plans/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    PlanResponse get(@PathVariable Long id) {
        return PlanResponse.from(billingService.findPlan(id));
    }

    @GetMapping
    Page<PlanResponse> list(@RequestParam(required = false) Boolean active, @PageableDefault Pageable pageable) {
        return billingService.findPlans(active, pageable).map(PlanResponse::from);
    }

    @PutMapping("/{id}")
    PlanResponse update(@PathVariable Long id, @Valid @RequestBody PlanRequest request) {
        return PlanResponse.from(billingService.updatePlan(id, request.name(), request.monthlyPrice()));
    }

    @PatchMapping("/{id}/activate")
    PlanResponse activate(@PathVariable Long id) {
        return PlanResponse.from(billingService.setPlanActive(id, true));
    }

    @PatchMapping("/{id}/deactivate")
    PlanResponse deactivate(@PathVariable Long id) {
        return PlanResponse.from(billingService.setPlanActive(id, false));
    }
}
