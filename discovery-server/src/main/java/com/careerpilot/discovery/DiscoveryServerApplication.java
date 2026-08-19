package com.careerpilot.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service registry. Every other service registers here on startup and resolves its peers
 * by logical name ({@code lb://profile-service}) rather than by host and port.
 *
 * <p>The dashboard at http://localhost:8761 is also the fastest way to show, in a demo,
 * that this really is a distributed system rather than one process pretending.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
