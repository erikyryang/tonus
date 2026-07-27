package br.com.tonus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulith;

/**
 * {@code @Modulith(sharedModules = "shared")}, e não {@code @SpringBootApplication}
 * puro: "shared module" no Spring Modulith não é inferido de
 * {@code allowedDependencies = {}} — é sempre declaração explícita aqui. Sem
 * isso, {@code shared.internal.*} (JpaAuditingConfig, AuditListener) fica de
 * fora do bootstrap de todo {@code @ApplicationModuleTest}, e created_at/
 * updated_at nascem nulos em qualquer entidade persistida por esse tipo de
 * teste — foi o que quebrava SchedulingEventsIT quando rodado isolado.
 */
@Modulith(sharedModules = "shared")
@ConfigurationPropertiesScan
public class TonusApplication {

    public static void main(String[] args) {
        SpringApplication.run(TonusApplication.class, args);
    }
}
