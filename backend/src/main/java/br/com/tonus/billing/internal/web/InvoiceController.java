package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.BillingService;
import br.com.tonus.billing.internal.domain.InvoiceStatus;
import br.com.tonus.shared.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
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

    /**
     * The ids of students with an OVERDUE invoice.
     *
     * Not paginated, and it's the conscious exception to the paginated
     * listing rule: the response is the set the client cross-references
     * with the student page it already has: paginating it would make the
     * screen need every page before painting the first row. The size is
     * bounded by the number of students in the studio, and each item is a
     * number.
     *
     * The path comes before any {@code /{id}} — there isn't one today, and
     * it's good that it stays that way: "overdue-student-ids" is never an id.
     */
    @GetMapping("/overdue-student-ids")
    List<Long> overdueStudentIds() {
        return billingService.findOverdueStudentIds();
    }

    /** Who registered it comes from the authenticated principal — never from the client. */
    @PostMapping("/{id}/payment")
    InvoiceResponse registerPayment(@PathVariable Long id,
                                    @Valid @RequestBody PaymentRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return InvoiceResponse.from(billingService.registerPayment(id, user.userId(), request.paymentDate()));
    }
}
