package com.careerpilot.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The only container that publishes a host port. Everything else is reachable solely on
 * the internal Docker network, so this process is the entire attack surface of the system.
 *
 * <p>Responsibilities: route by service name via Eureka, authenticate at the edge, enforce
 * per-user rate limits, answer CORS preflights, and stamp a correlation ID on every request.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
