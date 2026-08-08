package br.com.tonus.shared.internal;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduling and distributed locking for the whole application.
 *
 * Used to live in billing while the daily billing job was the only consumer
 * — the decision recorded back then was "the lock table is generic
 * infrastructure and lives in shared, but whoever uses it turns on the
 * support". With the event republication job ({@link EventRepublicationJob}),
 * there came to be two consumers in different modules, and keeping the
 * switch in billing would make shared's job depend on a module that shared
 * cannot know about.
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
