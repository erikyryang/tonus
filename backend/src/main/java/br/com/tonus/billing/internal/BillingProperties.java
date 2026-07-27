package br.com.tonus.billing.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing")
public record BillingProperties(Integer daysOverdue) {

    public BillingProperties {
        if (daysOverdue == null) {
            daysOverdue = 1;
        }
    }
}
