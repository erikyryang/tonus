package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.BillingService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
class SubscriptionController {

    private final BillingService billingService;

    SubscriptionController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping
    ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody SubscriptionRequest request) {
        SubscriptionResponse response = SubscriptionResponse.from(
                billingService.createSubscription(request.studentId(), request.monthlyAmount(), request.dueDay()));
        return ResponseEntity.created(URI.create("/subscriptions/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    SubscriptionResponse get(@PathVariable Long id) {
        return SubscriptionResponse.from(billingService.findSubscription(id));
    }

    @GetMapping
    Page<SubscriptionResponse> list(@RequestParam(required = false) Boolean active, @PageableDefault Pageable pageable) {
        return billingService.findSubscriptions(active, pageable).map(SubscriptionResponse::from);
    }

    @PutMapping("/{id}")
    SubscriptionResponse update(@PathVariable Long id, @Valid @RequestBody SubscriptionRequest request) {
        return SubscriptionResponse.from(
                billingService.updateSubscription(id, request.monthlyAmount(), request.dueDay()));
    }

    @PatchMapping("/{id}/activate")
    SubscriptionResponse activate(@PathVariable Long id) {
        return SubscriptionResponse.from(billingService.activateSubscription(id));
    }

    @PatchMapping("/{id}/deactivate")
    SubscriptionResponse deactivate(@PathVariable Long id) {
        return SubscriptionResponse.from(billingService.deactivateSubscription(id));
    }
}
