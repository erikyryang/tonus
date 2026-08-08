package br.com.tonus.billing.internal;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily job from the spec: generates today's invoice and marks an overdue
 * invoice as such. The lock guarantees only one instance runs at a time; the
 * idempotency of not duplicating an invoice/event comes from
 * {@link BillingService#generateDueInvoices()} itself (see uk_invoice_subscription_period).
 */
@Component
public class BillingJob {

    private final BillingService billingService;

    public BillingJob(BillingService billingService) {
        this.billingService = billingService;
    }

    @Scheduled(cron = "${billing.job-cron:0 0 3 * * *}")
    @SchedulerLock(name = "billing-daily-job", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void runDailyJob() {
        billingService.generateDueInvoices();
        billingService.markOverdueInvoices();
    }
}
