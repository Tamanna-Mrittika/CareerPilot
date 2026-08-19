package com.careerpilot.resume.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "careerpilot.storage")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {
    public MinioProperties {
        if (bucket == null || bucket.isBlank()) {
            bucket = "resumes";
        }
    }
}
