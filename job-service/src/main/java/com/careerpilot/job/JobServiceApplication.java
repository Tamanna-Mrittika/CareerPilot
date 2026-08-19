package com.careerpilot.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aggregates job postings from four external boards into one searchable corpus.
 *
 * <p>Everything interesting about this service follows from one external constraint: the
 * free API tiers are small (Adzuna allows ~1,000 calls a MONTH, Remotive advises no more
 * than four a day). Querying providers per user search is therefore impossible, which is
 * what drives scheduled ingestion, a local searchable corpus, Redis caching, and a circuit
 * breaker per provider. The quota shapes the architecture rather than decorating it.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableScheduling
public class JobServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobServiceApplication.class, args);
    }
}
