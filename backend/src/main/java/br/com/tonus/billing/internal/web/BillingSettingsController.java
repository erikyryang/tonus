package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.BillingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No POST and no DELETE: the row exists since the migration and is neither
 * created nor deleted. It's only read and edited.
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
