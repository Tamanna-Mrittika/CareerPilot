package com.careerpilot.matching.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Applies Boot's own {@link WebClientCustomizer} beans by hand.
     *
     * <p>This is load-bearing for distributed tracing. Declaring a {@code WebClient.Builder}
     * bean makes Spring Boot's WebClientAutoConfiguration back off -- and with it the
     * Micrometer observation customizer that injects W3C traceparent headers into outbound
     * calls. Without this loop the calls still work, but each one starts a NEW trace
     * instead of continuing the caller's, so Zipkin shows several shallow traces rather
     * than one that spans the whole request. Verified: the fan-out span was missing until
     * these customizers were applied.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder(ObjectProvider<WebClientCustomizer> customizers) {
        WebClient.Builder builder = WebClient.builder()
                // job-service returns full descriptions; a page of them exceeds WebClient's
                // 256KB default. Same fix, same reason, as job-service and resume-service.
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }
}
