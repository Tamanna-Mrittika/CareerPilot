package com.careerpilot.tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Kanban application tracker with email-driven auto-transitions.
 *
 * <p>Two things here are worth more than the CRUD: the status field is a real state
 * machine that refuses illegal moves (see ApplicationStatus), and the inbound webhook is
 * authenticated by HMAC signature rather than JWT, because its caller is a mail provider
 * with no user session.
 */
@SpringBootApplication
public class TrackerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackerServiceApplication.class, args);
    }
}
