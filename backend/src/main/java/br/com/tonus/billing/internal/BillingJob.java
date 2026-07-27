package br.com.tonus.billing.internal;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job diário da spec: gera fatura do dia e marca fatura vencida como
 * atrasada. O lock garante que só uma instância execute por vez; a
 * idempotência de não duplicar fatura/evento vem de
 * {@link BillingService#generateDueInvoices()} em si (ver uk_invoice_subscription_period).
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
