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

    // ---------------------------------------------------------------- plans

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
     * Deactivating a plan with an active enrollment is allowed (SPEC-012):
     * closing a plan can't require unenrolling students one by one. It's the
     * screen that warns of the impact, with the count that {@code
     * activeEnrollments} carries.
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
     * The enrollment count comes from ONE query for the whole page. One per
     * plan would be N+1 exactly on the screen where the ADMIN decides to
     * deactivate something — and it's the count they read before deciding.
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

    // ------------------------------------------------------------ enrollments

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

    // ---------------------------------------------------------- configuration

    @Transactional(readOnly = true)
    /**
     * The multi-plan discount, read only for the invoice calculation.
     *
     * There is no longer a way to change it through the API: the configuration
     * screen was removed and both /billing-settings verbs went with it. The row
     * still governs billing, and whoever needs another value edits the table.
     */
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
     * The due day comes from the student's registration, not the request:
     * it's the day of the month they joined the studio (SPEC-004).
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
        // Just one student: the name is one query, not one per row.
        String studentName = studentNameOf(studentId);
        Map<Long, LocalDate> paymentDates = paymentDatesOf(invoices.getContent());
        return invoices.map(invoice ->
                new InvoiceView(invoice, studentId, studentName, paymentDates.get(invoice.getId())));
    }

    /**
     * Who has an overdue invoice, as student ids.
     *
     * Answers the question the student screen asks without {@code
     * registration} coming to know {@code billing}: whoever owns the invoice
     * answers, and whoever cross-references the two lists is the client. The
     * edge that doesn't exist in the module graph keeps not existing.
     *
     * Distinct and sorted ids: the answer is about students, not invoices —
     * two overdue months are the same student just once.
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
     * Payment: invoice becomes PAID, a payment is created, an event is
     * published. All in the same transaction, as the spec requires.
     *
     * The subscription's due date <strong>does not</strong> move.
     * It used to — it became the payment's anniversary —, and paying late
     * pushed the monthly fee to the day of the late payment, month after
     * month. See SPEC-004.
     *
     * The date always comes from the caller — the Clock here is the
     * authority on "today" that decides whether it's in the future, not a
     * default value.
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
     * Step 1 of the daily job: generates the billing period's invoice for
     * every student with an active plan whose due date is today — or has
     * already passed without being invoiced.
     *
     * One invoice per student, with the sum of the plans — the student with
     * three plans gets one charge, not three. The existence check is the
     * business idempotency; uk_invoice_student_period is the database safety
     * net behind it.
     *
     * The comparison is "due date not after today" on purpose: gating on
     * strict equality would permanently lose a month whenever the job didn't
     * run on the exact due day (deployment, outage), with no catch-up in the
     * following runs. The idempotency above turns the wider window into a
     * safe catch-up instead of a double charge. The invoice keeps its real
     * due date even when it is born late, so the overdue countdown and the
     * notice reflect when the charge was actually due.
     *
     * The queries are set-based, not per student: active plans, active
     * enrollments and registration dates come in three queries, and the loop
     * only works in memory. Asking for the registration date student by
     * student would be N+1 in the job that runs over the whole studio.
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
            LocalDate dueDate = DueDates.forMonth(registration, currentMonth);
            if (dueDate.isAfter(today)) {
                continue;
            }
            if (invoiceRepository.existsByStudentIdAndReferenceMonth(studentId, currentMonth.atDay(1))) {
                continue;
            }
            BigDecimal amount = amountOf(entry.getValue(), discountPercent);
            Invoice invoice = invoiceRepository.save(new Invoice(studentId, currentMonth, amount, dueDate));
            events.publishEvent(new InvoiceDueSoon(invoice.getId(), studentId, dueDate, amount));
        }
    }

    /**
     * The sum of the plans, with a discount from the second one on.
     *
     * The rounding happens only once, on the total. Discounting plan by plan
     * and summing afterward produces different cents depending on the order
     * of the installments, and the difference shows up in the amount the
     * student receives via WhatsApp.
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
     * Step 2 of the daily job: an OPEN invoice overdue by more than N days
     * (billing.days-overdue) becomes OVERDUE. The query only picks up
     * whoever is still OPEN, so running it again on the same day doesn't
     * repeat the transition.
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
     * The page's payment dates, in ONE query — and only for PAID invoices.
     *
     * An invoice that isn't PAID has no row in payment: both things are born
     * in the same transaction as {@link #registerPayment}. Querying them
     * would be guaranteed wasted work, exactly on the "Overdue" filter, which
     * is the screen's most frequent use.
     */
    private Map<Long, LocalDate> paymentDatesOf(List<Invoice> invoices) {
        Set<Long> paidIds = invoices.stream()
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.PAID)
                .map(Invoice::getId)
                .collect(Collectors.toSet());
        if (paidIds.isEmpty()) {
            return Map.of();
        }
        // uk_payment_invoice guarantees at most one payment per invoice: no collision.
        return paymentRepository.findByInvoiceIdIn(paidIds).stream()
                .collect(Collectors.toMap(Payment::getInvoiceId, Payment::getPaymentDate));
    }

    /**
     * A deactivated student still has invoice history (SPEC-002: there is no
     * DELETE), so the name has to come out anyway.
     */
    private String studentNameOf(Long studentId) {
        return registrationFacade.findStudentContact(studentId)
                .map(StudentContact::name)
                .orElse(null);
    }

    /**
     * The day of the month of registration. The student has already been
     * checked as active before this point, so "couldn't find the date" is a
     * real inconsistency, not an edge case — and it becomes the same unknown
     * student error instead of a made-up due date.
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
