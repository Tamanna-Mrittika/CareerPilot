package com.careerpilot.resume;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Parses uploaded resumes and scores them against job postings.
 *
 * <p>Upload -&gt; MinIO (never the database, never the container filesystem) -&gt; 202
 * Accepted with a status URL; parsing runs on a bounded {@code @Async} executor and the
 * client polls. That is the async story this project uses in place of a message broker
 * (a deliberate constraint, not an oversight -- see CLAUDE.md).
 *
 * <p>Two peers, both read-only and both public (no user context needed):
 * profile-service's skill taxonomy (for the Aho-Corasick automaton) and job-service's
 * listings (for IDF-weighted keyword scoring). Both are refreshed on a schedule rather
 * than fetched per request.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ResumeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeServiceApplication.class, args);
    }
}
