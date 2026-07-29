package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.BillingService;
import br.com.tonus.billing.internal.domain.InvoiceStatus;
import br.com.tonus.shared.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invoices")
class InvoiceController {

    private final BillingService billingService;

    InvoiceController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    Page<InvoiceResponse> list(@RequestParam(required = false) InvoiceStatus status, @PageableDefault Pageable pageable) {
        return billingService.findInvoices(status, pageable).map(InvoiceResponse::from);
    }

    /** Quem registrou vem do principal autenticado — nunca do cliente. */
    @PostMapping("/{id}/payment")
    InvoiceResponse registerPayment(@PathVariable Long id,
                                    @Valid @RequestBody PaymentRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return InvoiceResponse.from(billingService.registerPayment(id, user.userId(), request.paymentDate()));
    }
}
