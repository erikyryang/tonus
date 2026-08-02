package br.com.tonus.billing.internal;

import br.com.tonus.billing.InvoiceDueSoon;
import br.com.tonus.billing.InvoiceOverdue;
import br.com.tonus.billing.PaymentRegistered;
import br.com.tonus.billing.internal.domain.Invoice;
import br.com.tonus.billing.internal.domain.InvoiceRepository;
import br.com.tonus.billing.internal.domain.InvoiceStatus;
import br.com.tonus.billing.internal.domain.BillingSettings;
import br.com.tonus.billing.internal.domain.BillingSettingsRepository;
import br.com.tonus.billing.internal.domain.Payment;
import br.com.tonus.billing.internal.domain.PaymentRepository;
import br.com.tonus.billing.internal.domain.Plan;
import br.com.tonus.billing.internal.domain.PlanEnrollment;
import br.com.tonus.billing.internal.domain.PlanEnrollmentRepository;
import br.com.tonus.billing.internal.domain.PlanRepository;
import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentContact;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;
    private final PlanEnrollmentRepository planEnrollmentRepository;
    private final BillingSettingsRepository billingSettingsRepository;
    private final RegistrationFacade registrationFacade;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final BillingProperties billingProperties;

    public BillingService(InvoiceRepository invoiceRepository,
                           PaymentRepository paymentRepository,
                           PlanRepository planRepository,
                           PlanEnrollmentRepository planEnrollmentRepository,
                           BillingSettingsRepository billingSettingsRepository,
                           RegistrationFacade registrationFacade,
                           ApplicationEventPublisher events,
                           Clock clock,
                           BillingProperties billingProperties) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.planRepository = planRepository;
        this.planEnrollmentRepository = planEnrollmentRepository;
        this.billingSettingsRepository = billingSettingsRepository;
        this.registrationFacade = registrationFacade;
        this.events = events;
        this.clock = clock;
        this.billingProperties = billingProperties;
    }

    // ---------------------------------------------------------------- planos

    @Transactional
    public PlanView createPlan(String name, BigDecimal monthlyPrice) {
        Plan plan = planRepository.save(new Plan(name, monthlyPrice));
        return new PlanView(plan, 0);
    }

    @Transactional
    public PlanView updatePlan(Long id, String name, BigDecimal monthlyPrice) {
        Plan plan = requirePlan(id);
        plan.update(name, monthlyPrice);
        return withEnrollmentCount(planRepository.save(plan));
    }

    /**
     * Desativar plano com matrícula ativa é permitido (SPEC-012): encerrar um
     * plano não pode exigir desmatricular aluno por aluno. Quem avisa do
     * estrago é a tela, com a contagem que {@code activeEnrollments} carrega.
     */
    @Transactional
    public PlanView setPlanActive(Long id, boolean active) {
        Plan plan = requirePlan(id);
        if (active) {
            plan.activate();
        } else {
            plan.deactivate();
        }
        return withEnrollmentCount(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public PlanView findPlan(Long id) {
        return withEnrollmentCount(requirePlan(id));
    }

    /**
     * A contagem de matriculados sai de UMA consulta para a página inteira. Uma
     * por plano seria N+1 justamente na tela em que o ADMIN decide desativar
     * algo — e é a contagem que ele lê antes de decidir.
     */
    @Transactional(readOnly = true)
    public Page<PlanView> findPlans(Boolean active, Pageable pageable) {
        Pageable stable = withStableSort(pageable);
        Page<Plan> plans = active == null
                ? planRepository.findAll(stable)
                : planRepository.findByActive(active, stable);

        List<Long> planIds = plans.getContent().stream().map(Plan::getId).toList();
        Map<Long, Long> counts = planIds.isEmpty()
                ? Map.of()
                : planEnrollmentRepository.countActiveByPlanIds(planIds).stream()
                        .collect(Collectors.toMap(
                                PlanEnrollmentRepository.PlanEnrollmentCount::getPlanId,
                                PlanEnrollmentRepository.PlanEnrollmentCount::getTotal));

        return plans.map(plan -> new PlanView(plan, counts.getOrDefault(plan.getId(), 0L)));
    }

    // ------------------------------------------------------------ matrículas

    @Transactional
    public PlanEnrollment enroll(Long studentId, Long planId) {
        requireActiveStudent(studentId);
        if (!planRepository.existsById(planId)) {
            throw new UnknownPlanException(planId);
        }
        if (planEnrollmentRepository.existsByStudentIdAndPlanId(studentId, planId)) {
            throw new EnrollmentAlreadyExistsException(studentId, planId);
        }
        return planEnrollmentRepository.save(new PlanEnrollment(studentId, planId));
    }

    @Transactional
    public PlanEnrollment setEnrollmentActive(Long id, boolean active) {
        PlanEnrollment enrollment = planEnrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException(id));
        if (active) {
            enrollment.activate();
        } else {
            enrollment.deactivate();
        }
        return planEnrollmentRepository.save(enrollment);
    }

    @Transactional(readOnly = true)
    public List<PlanEnrollment> findEnrollmentsOfStudent(Long studentId) {
        return planEnrollmentRepository.findByStudentId(studentId);
    }

    // ---------------------------------------------------------- configuração

    @Transactional(readOnly = true)
    public BillingSettings findSettings() {
        return requireSettings();
    }

    @Transactional
    public BillingSettings updateSettings(BigDecimal multiPlanDiscountPercent) {
        BillingSettings settings = requireSettings();
        settings.update(multiPlanDiscountPercent);
        return billingSettingsRepository.save(settings);
    }

    private BillingSettings requireSettings() {
        return billingSettingsRepository.findById(BillingSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "billing_settings sem a linha única; a migration V019 deveria tê-la criado"));
    }

    private Plan requirePlan(Long id) {
        return planRepository.findById(id).orElseThrow(() -> new PlanNotFoundException(id));
    }

    private PlanView withEnrollmentCount(Plan plan) {
        return new PlanView(plan, planEnrollmentRepository.countByPlanIdAndActiveTrue(plan.getId()));
    }

    /**
     * O dia de vencimento vem do cadastro do aluno, não do pedido: é o dia do
     * mês em que ele entrou no estúdio (SPEC-004).
     */
    @Transactional(readOnly = true)
    public Page<InvoiceView> findInvoices(InvoiceStatus status, Pageable pageable) {
        Pageable stable = withStableSort(pageable);
        Page<Invoice> invoices = status == null
                ? invoiceRepository.findAll(stable)
                : invoiceRepository.findByStatus(status, stable);

        Set<Long> studentIds = invoices.getContent().stream().map(Invoice::getStudentId).collect(Collectors.toSet());
        Map<Long, String> studentNames = registrationFacade.findStudentNames(studentIds);
        Map<Long, LocalDate> paymentDates = paymentDatesOf(invoices.getContent());

        return invoices.map(invoice -> new InvoiceView(invoice, invoice.getStudentId(),
                studentNames.get(invoice.getStudentId()), paymentDates.get(invoice.getId())));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceView> findInvoicesOfStudent(Long studentId, Pageable pageable) {
        Pageable stable = withStableSort(pageable);
        Page<Invoice> invoices = invoiceRepository.findByStudentId(studentId, stable);
        // Um aluno só: o nome é uma consulta, não uma por linha.
        String studentName = studentNameOf(studentId);
        Map<Long, LocalDate> paymentDates = paymentDatesOf(invoices.getContent());
        return invoices.map(invoice ->
                new InvoiceView(invoice, studentId, studentName, paymentDates.get(invoice.getId())));
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
        return invoiceRepository.findByStatus(InvoiceStatus.OVERDUE).stream()
                .map(Invoice::getStudentId)
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

        events.publishEvent(new PaymentRegistered(invoiceId, invoice.getStudentId(), paymentDate, invoice.getAmount()));
        return new InvoiceView(invoice, invoice.getStudentId(), studentNameOf(invoice.getStudentId()), paymentDate);
    }

    /**
     * Passo 1 do job diário: gera a fatura da competência para cada aluno com
     * plano ativo cujo vencimento cai hoje.
     *
     * Uma fatura por aluno, com a soma dos planos — o aluno de três planos
     * recebe uma cobrança, não três. A checagem de existência é a idempotência
     * de negócio; uk_invoice_student_period é a rede de segurança do banco por
     * trás dela.
     *
     * As consultas são por conjunto, não por aluno: planos ativos, matrículas
     * ativas e datas de cadastro vêm em três consultas, e o laço só trabalha em
     * memória. Perguntar a data de cadastro aluno a aluno seria N+1 no job que
     * roda sobre o estúdio inteiro.
     */
    @Transactional
    public void generateDueInvoices() {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);

        Map<Long, BigDecimal> pricesByPlan = planRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(Plan::getId, Plan::getMonthlyPrice));

        Map<Long, List<BigDecimal>> pricesByStudent = planEnrollmentRepository.findByActiveTrue().stream()
                .filter(enrollment -> pricesByPlan.containsKey(enrollment.getPlanId()))
                .collect(Collectors.groupingBy(PlanEnrollment::getStudentId,
                        Collectors.mapping(enrollment -> pricesByPlan.get(enrollment.getPlanId()),
                                Collectors.toList())));

        if (pricesByStudent.isEmpty()) {
            return;
        }

        BigDecimal discountPercent = requireSettings().getMultiPlanDiscountPercent();
        Map<Long, LocalDate> registrationDates = registrationFacade.findRegistrationDates(pricesByStudent.keySet());

        for (Map.Entry<Long, List<BigDecimal>> entry : pricesByStudent.entrySet()) {
            Long studentId = entry.getKey();
            LocalDate registration = registrationDates.get(studentId);
            if (registration == null) {
                continue;
            }
            if (!DueDates.forMonth(registration, currentMonth).equals(today)) {
                continue;
            }
            if (invoiceRepository.existsByStudentIdAndReferenceMonth(studentId, currentMonth.atDay(1))) {
                continue;
            }
            BigDecimal amount = amountOf(entry.getValue(), discountPercent);
            Invoice invoice = invoiceRepository.save(new Invoice(studentId, currentMonth, amount, today));
            events.publishEvent(new InvoiceDueSoon(invoice.getId(), studentId, today, amount));
        }
    }

    /**
     * A soma dos planos, com desconto a partir do segundo.
     *
     * O arredondamento acontece uma vez só, no total. Descontar plano a plano e
     * somar depois produz centavos diferentes conforme a ordem das parcelas, e
     * a diferença aparece no valor que o aluno recebe por WhatsApp.
     */
    private BigDecimal amountOf(List<BigDecimal> prices, BigDecimal discountPercent) {
        BigDecimal total = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (prices.size() > 1) {
            BigDecimal keptFraction = BigDecimal.ONE.subtract(
                    discountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            total = total.multiply(keptFraction);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
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
            events.publishEvent(new InvoiceOverdue(invoice.getId(), invoice.getStudentId(), invoice.getDueDate()));
        }
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
