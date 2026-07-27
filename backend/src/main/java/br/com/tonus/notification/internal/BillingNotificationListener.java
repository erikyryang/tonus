package br.com.tonus.notification.internal;

import br.com.tonus.billing.InvoiceDueSoon;
import br.com.tonus.billing.InvoiceOverdue;
import br.com.tonus.billing.PaymentRegistered;
import br.com.tonus.notification.internal.domain.MessageTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * O que acontece com a mensalidade do aluno, ele fica sabendo.
 *
 * A chave de idempotência é o id da fatura: os três eventos são fatos que
 * acontecem no máximo uma vez por fatura, e o template já separa qual deles é.
 */
@Component
class BillingNotificationListener {

    private final NotificationService notifications;

    BillingNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(InvoiceDueSoon event) {
        notifications.notifyStudent(event.studentId(), MessageTemplate.INVOICE_DUE_SOON,
                event.invoiceId().toString(),
                MessageTemplate.money(event.amount()), MessageTemplate.date(event.dueDate()));
    }

    @ApplicationModuleListener
    public void on(InvoiceOverdue event) {
        notifications.notifyStudent(event.studentId(), MessageTemplate.INVOICE_OVERDUE,
                event.invoiceId().toString(), MessageTemplate.date(event.dueDate()));
    }

    @ApplicationModuleListener
    public void on(PaymentRegistered event) {
        notifications.notifyStudent(event.studentId(), MessageTemplate.PAYMENT_REGISTERED,
                event.invoiceId().toString(),
                MessageTemplate.money(event.amount()), MessageTemplate.date(event.paymentDate()));
    }
}
