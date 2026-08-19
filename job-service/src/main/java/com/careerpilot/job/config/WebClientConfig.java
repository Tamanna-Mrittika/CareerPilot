package com.careerpilot.job.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                // A generous BACKSTOP, not the real enforcement point. That is
                // provider.fetchTimeout() in JobIngestionService, applied per provider via
                // the Reactor .timeout() operator. This one is transport-level (Netty) and
                // sits below Reactor's -- a short value here previously fired before the
                // per-provider Reactor timeout ever got a chance, discarding an Apify run
                // that had already succeeded and been billed. Set above the longest
                // configured provider timeout (Apify's 5 minutes) so it can only fire when
                // something is genuinely stuck, never as the normal path.
                .responseTimeout(Duration.ofMinutes(6))
                .followRedirect(true);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // These feeds are large -- Arbeitnow's is ~2 MB and RemoteOK's ~500 KB.
                // WebClient's default 256 KB buffer truncates them into a parse error that
                // looks like malformed JSON rather than a size limit.
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
    }
}
