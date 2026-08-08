package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.BillingService;
import br.com.tonus.shared.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /me/invoices: the student id comes from the token, never from path or
 * query — same pattern as {@code MyScheduleController}.
 */
@RestController
@RequestMapping("/me")
class MyInvoicesController {

    private final BillingService billingService;

    MyInvoicesController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/invoices")
    Page<InvoiceResponse> myInvoices(@AuthenticationPrincipal AuthenticatedUser user, @PageableDefault Pageable pageable) {
        Long studentId = requireLink(user == null ? null : user.studentId());
        return billingService.findInvoicesOfStudent(studentId, pageable).map(InvoiceResponse::from);
    }

    private Long requireLink(Long studentId) {
        if (studentId == null) {
            throw new AccessDeniedException("Usuário autenticado não está vinculado a nenhum aluno");
        }
        return studentId;
    }
}
