package com.careerpilot.matching.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder()
                // job-service returns full descriptions; a page of them exceeds WebClient's
                // 256KB default. Same fix, same reason, as job-service and resume-service.
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
    }
}
