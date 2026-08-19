package com.careerpilot.resume.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Resolves {@code lb://profile-service} and {@code lb://job-service} via Eureka, the same
 * way api-gateway resolves its routes -- these are the only two peers resume-service talks
 * to, and both are already registered in the same service registry, so reusing it for
 * service-to-service discovery costs nothing new.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder()
                // Default is 256KB. job-service's own /api/v1/jobs sample of 300 postings
                // with full descriptions blows past that easily -- same fix already applied
                // in job-service's own WebClientConfig for the same reason (Arbeitnow's feed).
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
    }
}
