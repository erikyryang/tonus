package br.com.tonus.billing.internal;

import br.com.tonus.billing.InvoiceDueSoon;
import br.com.tonus.billing.InvoiceOverdue;
import br.com.tonus.billing.PaymentRegistered;
import br.com.tonus.billing.internal.domain.Invoice;
import br.com.tonus.billing.internal.domain.InvoiceRepository;
import br.com.tonus.billing.internal.domain.InvoiceStatus;
import br.com.tonus.billing.internal.domain.Payment;
import br.com.tonus.billing.internal.domain.PaymentRepository;
import br.com.tonus.billing.internal.domain.Subscription;
import br.com.tonus.billing.internal.domain.SubscriptionRepository;
import br.com.tonus.registration.RegistrationFacade;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final RegistrationFacade registrationFacade;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final BillingProperties billingProperties;

    public BillingService(SubscriptionRepository subscriptionRepository,
                           InvoiceRepository invoiceRepository,
                           PaymentRepository paymentRepository,
                           RegistrationFacade registrationFacade,
                           ApplicationEventPublisher events,
                           Clock clock,
                           BillingProperties billingProperties) {
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.registrationFacade = registrationFacade;
        this.events = events;
        this.clock = clock;
        this.billingProperties = billingProperties;
    }

    @Transactional
    public Subscription createSubscription(Long studentId, BigDecimal monthlyAmount, int dueDay) {
        requireActiveStudent(studentId);
        if (subscriptionRepository.existsByStudentId(studentId)) {
            throw new SubscriptionAlreadyExistsException(studentId);
        }
        return subscriptionRepository.save(new Subscription(studentId, monthlyAmount, dueDay));
    }

    @Transactional
    public Subscription updateSubscription(Long id, BigDecimal monthlyAmount, int dueDay) {
        Subscription subscription = requireSubscription(id);
        subscription.update(monthlyAmount, dueDay);
        return subscriptionRepository.save(subscription);
    }

    /**
     * Reativar exige que o aluno vinculado ainda esteja ativo — senão a
     * assinatura voltaria a gerar fatura para quem já saiu do estúdio no
     * próximo job diário. Mesma regra de RegistrationService.activateStudent
     * para o professor.
     */
    @Transactional
    public Subscription activateSubscription(Long id) {
        Subscription subscription = requireSubscription(id);
        requireActiveStudent(subscription.getStudentId());
        subscription.activate();
        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public Subscription deactivateSubscription(Long id) {
        Subscription subscription = requireSubscription(id);
        subscription.deactivate();
        return subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public Subscription findSubscription(Long id) {
        return requireSubscription(id);
    }

    @Transactional(readOnly = true)
    public Page<Subscription> findSubscriptions(Boolean active, Pageable pageable) {
        Pageable stable = withStableSort(pageable);
        return active == null
                ? subscriptionRepository.findAll(stable)
                : subscriptionRepository.findByActive(active, stable);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceView> findInvoices(InvoiceStatus status, Pageable pageable) {
        Pageable stable = withStableSort(pageable);
        Page<Invoice> invoices = status == null
                ? invoiceRepository.findAll(stable)
                : invoiceRepository.findByStatus(status, stable);
        return invoices.map(this::toView);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceView> findInvoicesOfStudent(Long studentId, Pageable pageable) {
        return subscriptionRepository.findByStudentId(studentId)
                .map(subscription -> invoiceRepository
                        .findBySubscriptionId(subscription.getId(), withStableSort(pageable))
                        .map(invoice -> new InvoiceView(invoice, studentId)))
                .orElseGet(() -> Page.empty(withStableSort(pageable)));
    }

    /**
     * Baixa: fatura vira PAGA, pagamento é criado, assinatura recalcula o
     * vencimento para o aniversário do pagamento, evento é publicado. Tudo na
     * mesma transação, como a spec exige.
     */
    @Transactional
    public InvoiceView registerPayment(Long invoiceId, Long registeredBy) {
        Invoice invoice = requireInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvoiceAlreadyPaidException(invoiceId);
        }
        LocalDate paymentDate = LocalDate.now(clock);
        invoice.markPaid();
        invoiceRepository.save(invoice);
        paymentRepository.save(new Payment(invoiceId, paymentDate, registeredBy));

        Subscription subscription = requireSubscription(invoice.getSubscriptionId());
        subscription.realignDueDayTo(paymentDate);
        subscriptionRepository.save(subscription);

        events.publishEvent(new PaymentRegistered(invoiceId, subscription.getStudentId(), paymentDate, invoice.getAmount()));
        return new InvoiceView(invoice, subscription.getStudentId());
    }

    /**
     * Passo 1 do job diário: gera a fatura da competência para assinaturas
     * ativas cujo vencimento cai hoje. A checagem de existência é a
     * idempotência de negócio; uk_invoice_subscription_period é a rede de
     * segurança do banco por trás dela.
     */
    @Transactional
    public void generateDueInvoices() {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        for (Subscription subscription : subscriptionRepository.findByActiveTrue()) {
            LocalDate dueDate = subscription.dueDateFor(currentMonth);
            if (!dueDate.equals(today)) {
                continue;
            }
            if (invoiceRepository.existsBySubscriptionIdAndReferenceMonth(subscription.getId(), currentMonth.atDay(1))) {
                continue;
            }
            Invoice invoice = invoiceRepository.save(
                    new Invoice(subscription.getId(), currentMonth, subscription.getMonthlyAmount(), dueDate));
            events.publishEvent(new InvoiceDueSoon(invoice.getId(), subscription.getStudentId(), dueDate, invoice.getAmount()));
        }
    }

    /**
     * Passo 2 do job diário: fatura ABERTA vencida há mais de N dias
     * (billing.days-overdue) vira ATRASADA. A query só pega quem ainda está
     * ABERTA, então rodar de novo no mesmo dia não repete a transição.
     */
    @Transactional
    public void markOverdueInvoices() {
        LocalDate cutoff = LocalDate.now(clock).minusDays(billingProperties.daysOverdue());
        for (Invoice invoice : invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.OPEN, cutoff)) {
            invoice.markOverdue();
            invoiceRepository.save(invoice);
            Subscription subscription = requireSubscription(invoice.getSubscriptionId());
            events.publishEvent(new InvoiceOverdue(invoice.getId(), subscription.getStudentId(), invoice.getDueDate()));
        }
    }

    private InvoiceView toView(Invoice invoice) {
        Subscription subscription = requireSubscription(invoice.getSubscriptionId());
        return new InvoiceView(invoice, subscription.getStudentId());
    }

    private void requireActiveStudent(Long studentId) {
        if (!registrationFacade.studentIsActive(studentId)) {
            throw new UnknownStudentException(studentId);
        }
    }

    private Subscription requireSubscription(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));
    }

    private Invoice requireInvoice(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    private Pageable withStableSort(Pageable pageable) {
        if (pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(Sort.by("id")));
    }
}
