package com.careerpilot.job.config;

import com.careerpilot.job.api.dto.JobDtos.JobPage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache for job search results.
 *
 * <p>Serialization is <strong>typed</strong> rather than polymorphic. An earlier version
 * used {@code GenericJackson2JsonRedisSerializer} with default typing, which produced a
 * nasty failure mode: writes succeeded and every subsequent <em>read</em> threw, so a
 * search worked once and then returned 500 on the second identical request. Because this
 * cache holds exactly one concrete type, binding the serializer to {@link JobPage} removes
 * the type-metadata negotiation entirely instead of trying to configure it correctly.
 *
 * <p>The TTL is a backstop, not the primary invalidation mechanism: ingestion clears this
 * cache when it finishes, so new postings appear at once. The TTL only bounds staleness if
 * an ingestion run fails.
 */
@Configuration
public class CacheConfig {

    private static final String JOB_SEARCH_CACHE = "jobSearch";

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return baseConfig();
    }

    /**
     * Binds the {@code jobSearch} cache to a {@link JobPage}-typed serializer. Any cache
     * added later falls back to the default configuration above.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer jobSearchCacheCustomizer() {
        return builder -> builder.withCacheConfiguration(JOB_SEARCH_CACHE,
                baseConfig().serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper(), JobPage.class))));
    }

    private RedisCacheConfiguration baseConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                // Null results are not cached: a search that returned nothing because
                // ingestion had not run yet must not be pinned for 30 minutes.
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()));
    }

    /** JSON rather than JDK serialization, so entries are readable in redis-cli. */
    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // ISO-8601 instants rather than epoch numbers, so cached JSON is legible.
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Tolerate fields added to JobPage since an entry was cached, instead of throwing.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
