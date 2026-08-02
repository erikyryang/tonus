package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.BillingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sem POST e sem DELETE: a linha existe desde a migration e não se cria nem se
 * apaga. Só se lê e se edita.
 */
@RestController
@RequestMapping("/billing-settings")
class BillingSettingsController {

    private final BillingService billingService;

    BillingSettingsController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    BillingSettingsResponse get() {
        return BillingSettingsResponse.from(billingService.findSettings());
    }

    @PutMapping
    BillingSettingsResponse update(@Valid @RequestBody BillingSettingsRequest request) {
        return BillingSettingsResponse.from(
                billingService.updateSettings(request.multiPlanDiscountPercent()));
    }
}
