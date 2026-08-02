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
     * Os ids dos alunos com fatura ATRASADA.
     *
     * Não é paginado, e é a exceção consciente à regra de listagem paginada: a
     * resposta é o conjunto que o cliente cruza com a página de alunos que ele
     * já tem: paginá-la faria a tela precisar de todas as páginas antes de
     * pintar a primeira linha. O tamanho é limitado pelo número de alunos do
     * estúdio, e cada item é um número.
     *
     * O caminho vem antes de qualquer {@code /{id}} — não há nenhum hoje, e é
     * bom que continue assim: "overdue-student-ids" nunca é um id.
     */
    @GetMapping("/overdue-student-ids")
    List<Long> overdueStudentIds() {
        return billingService.findOverdueStudentIds();
    }

    /** Quem registrou vem do principal autenticado — nunca do cliente. */
    @PostMapping("/{id}/payment")
    InvoiceResponse registerPayment(@PathVariable Long id,
                                    @Valid @RequestBody PaymentRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return InvoiceResponse.from(billingService.registerPayment(id, user.userId(), request.paymentDate()));
    }
}
