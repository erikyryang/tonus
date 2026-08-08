package br.com.tonus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulith;

/**
 * {@code @Modulith(sharedModules = "shared")}, not a plain
 * {@code @SpringBootApplication}: a "shared module" in Spring Modulith isn't
 * inferred from {@code allowedDependencies = {}} — it's always an explicit
 * declaration here. Without it, {@code shared.internal.*}
 * (JpaAuditingConfig, AuditListener) is left out of the bootstrap of every
 * {@code @ApplicationModuleTest}, and created_at/updated_at come back null
 * on any entity persisted by that kind of test — that's what was breaking
 * SchedulingEventsIT when run in isolation.
 */
@Modulith(sharedModules = "shared")
@ConfigurationPropertiesScan
public class TonusApplication {

    public static void main(String[] args) {
        SpringApplication.run(TonusApplication.class, args);
    }
}
