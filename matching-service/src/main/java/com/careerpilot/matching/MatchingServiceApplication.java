package com.careerpilot.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Composes profile-service and job-service into ranked, explainable job matches.
 *
 * <p>The API-composition service in this architecture: it owns almost no data (just the
 * free-course catalog) and exists to produce something neither peer can alone. Two
 * periodically-refreshed caches back it -- the skill taxonomy (to read skills out of job
 * prose) and a rarity index over the live job corpus (so matching a rare skill counts for
 * more than matching a ubiquitous one).
 */
@SpringBootApplication
@EnableScheduling
public class MatchingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingServiceApplication.class, args);
    }
}
