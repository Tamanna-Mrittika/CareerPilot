package com.careerpilot.job.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Distributed locking for scheduled ingestion.
 *
 * <p>Without this, running two job-service replicas means both wake at the same cron tick
 * and both hit the providers -- doubling consumption of an API quota that is already the
 * binding constraint, and racing each other on the upsert. The Redis lock ensures exactly
 * one instance performs each run.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "careerpilot");
    }
}
