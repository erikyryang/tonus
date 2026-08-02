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
import br.com.tonus.registration.StudentContact;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    /**
     * O dia de vencimento vem do cadastro do aluno, não do pedido: é o dia do
     * mês em que ele entrou no estúdio (SPEC-004).
     */
    @Transactional
    public Subscription createSubscription(Long studentId, BigDecimal monthlyAmount) {
        requireActiveStudent(studentId);
        if (subscriptionRepository.existsByStudentId(studentId)) {
            throw new SubscriptionAlreadyExistsException(studentId);
        }
        return subscriptionRepository.save(
                new Subscription(studentId, monthlyAmount, dueDayOfRegistration(studentId)));
    }

    @Transactional
    public Subscription updateSubscription(Long id, BigDecimal monthlyAmount) {
        Subscription subscription = requireSubscription(id);
        subscription.update(monthlyAmount);
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

        Map<Long, Long> studentIds = studentIdsBySubscription(invoices.getContent());
        Map<Long, String> studentNames = registrationFacade.findStudentNames(Set.copyOf(studentIds.values()));
        Map<Long, LocalDate> paymentDates = paymentDatesOf(invoices.getContent());

        return invoices.map(invoice -> {
            Long studentId = studentIds.get(invoice.getSubscriptionId());
            return new InvoiceView(invoice, studentId, studentNames.get(studentId), paymentDates.get(invoice.getId()));
        });
    }

    @Transactional(readOnly = true)
    public Page<InvoiceView> findInvoicesOfStudent(Long studentId, Pageable pageable) {
        Pageable stable = withStableSort(pageable);
        return subscriptionRepository.findByStudentId(studentId)
                .map(subscription -> {
                    Page<Invoice> invoices = invoiceRepository.findBySubscriptionId(subscription.getId(), stable);
                    // Um aluno só: o nome é uma consulta, não uma por linha.
                    String studentName = studentNameOf(studentId);
                    Map<Long, LocalDate> paymentDates = paymentDatesOf(invoices.getContent());
                    return invoices.map(invoice ->
                            new InvoiceView(invoice, studentId, studentName, paymentDates.get(invoice.getId())));
                })
                .orElseGet(() -> Page.empty(stable));
    }

    /**
     * Quem está com fatura atrasada, em ids de aluno.
     *
     * Responde à pergunta que a tela de alunos faz sem que {@code registration}
     * passe a conhecer {@code billing}: quem é dono da fatura responde, e quem
     * cruza as duas listas é o cliente. A aresta que não existe no grafo de
     * módulos continua não existindo.
     *
     * Ids distintos e ordenados: a resposta é sobre alunos, não sobre faturas —
     * dois meses atrasados são o mesmo aluno uma vez só.
     */
    @Transactional(readOnly = true)
    public List<Long> findOverdueStudentIds() {
        List<Invoice> overdue = invoiceRepository.findByStatus(InvoiceStatus.OVERDUE);
        return studentIdsBySubscription(overdue).values().stream()
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Baixa: fatura vira PAGA, pagamento é criado, evento é publicado. Tudo na
     * mesma transação, como a spec exige.
     *
     * O vencimento da assinatura <strong>não</strong> se mexe. Mexia — virava o
     * aniversário do pagamento —, e pagar em atraso empurrava a mensalidade para
     * o dia do atraso, mês após mês. Ver SPEC-004.
     *
     * A data vem sempre de quem chama — o Clock aqui é a autoridade do "hoje"
     * que decide se ela está no futuro, não um valor padrão.
     */
    @Transactional
    public InvoiceView registerPayment(Long invoiceId, Long registeredBy, LocalDate paymentDate) {
        Invoice invoice = requireInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvoiceAlreadyPaidException(invoiceId);
        }
        if (paymentDate.isAfter(LocalDate.now(clock))) {
            throw new FuturePaymentDateException(paymentDate);
        }
        invoice.markPaid();
        invoiceRepository.save(invoice);
        paymentRepository.save(new Payment(invoiceId, paymentDate, registeredBy));

        Subscription subscription = requireSubscription(invoice.getSubscriptionId());
        events.publishEvent(new PaymentRegistered(invoiceId, subscription.getStudentId(), paymentDate, invoice.getAmount()));
        return new InvoiceView(invoice, subscription.getStudentId(),
                studentNameOf(subscription.getStudentId()), paymentDate);
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

    /**
     * O aluno de cada fatura da página, em UMA consulta.
     *
     * invoice.subscription_id tem FK de verdade (V012), então toda fatura da
     * página tem assinatura e nenhum id fica de fora do mapa.
     */
    private Map<Long, Long> studentIdsBySubscription(List<Invoice> invoices) {
        Set<Long> subscriptionIds = invoices.stream().map(Invoice::getSubscriptionId).collect(Collectors.toSet());
        if (subscriptionIds.isEmpty()) {
            return Map.of();
        }
        return subscriptionRepository.findAllById(subscriptionIds).stream()
                .collect(Collectors.toMap(Subscription::getId, Subscription::getStudentId));
    }

    /**
     * As datas de baixa da página, em UMA consulta — e só para as faturas PAGAS.
     *
     * Fatura que não está PAGA não tem linha em payment: as duas coisas nascem
     * na mesma transação de {@link #registerPayment}. Consultá-las seria
     * trabalho garantidamente vazio, justamente no filtro "Atrasadas", que é o
     * uso mais frequente da tela.
     */
    private Map<Long, LocalDate> paymentDatesOf(List<Invoice> invoices) {
        Set<Long> paidIds = invoices.stream()
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.PAID)
                .map(Invoice::getId)
                .collect(Collectors.toSet());
        if (paidIds.isEmpty()) {
            return Map.of();
        }
        // uk_payment_invoice garante no máximo uma baixa por fatura: sem colisão.
        return paymentRepository.findByInvoiceIdIn(paidIds).stream()
                .collect(Collectors.toMap(Payment::getInvoiceId, Payment::getPaymentDate));
    }

    /**
     * Aluno desativado continua com histórico de fatura (SPEC-002: não há
     * DELETE), então o nome tem de sair mesmo assim.
     */
    private String studentNameOf(Long studentId) {
        return registrationFacade.findStudentContact(studentId)
                .map(StudentContact::name)
                .orElse(null);
    }

    /**
     * O dia do mês do cadastro. O aluno já foi conferido como ativo antes daqui,
     * então "não achei a data" é inconsistência de verdade, não caso de borda —
     * e vira o mesmo erro de aluno desconhecido em vez de um vencimento
     * inventado.
     */
    private int dueDayOfRegistration(Long studentId) {
        return registrationFacade.findRegistrationDate(studentId)
                .orElseThrow(() -> new UnknownStudentException(studentId))
                .getDayOfMonth();
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
