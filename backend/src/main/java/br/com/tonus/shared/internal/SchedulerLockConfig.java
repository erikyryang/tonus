package br.com.tonus.shared.internal;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga agendamento e lock distribuído para a aplicação inteira.
 *
 * Ficava em billing enquanto o job diário de cobrança era o único consumidor —
 * a decisão registrada então era "a tabela de lock é infra genérica e mora em
 * shared, mas quem liga o suporte é quem usa". Com o job de republicação de
 * eventos ({@link EventRepublicationJob}), passou a haver dois consumidores em
 * módulos diferentes, e manter a chave em billing faria o job de shared depender
 * de um módulo que shared não pode conhecer.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
