package com.careerpilot.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Owns the candidate's structured profile and the canonical skill taxonomy.
 *
 * <p>The taxonomy is the interesting part architecturally: resume-service needs it to
 * extract skills from CV text, and matching-service needs it to compare a profile against
 * job requirements. Neither reaches into this database -- they call {@code /api/v1/skills}
 * over HTTP. One service owns the data, everyone else asks it. That boundary is what keeps
 * the taxonomy authoritative instead of quietly forking into three divergent copies.
 */
@SpringBootApplication
public class ProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfileServiceApplication.class, args);
    }
}
