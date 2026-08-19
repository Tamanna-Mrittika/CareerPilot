package com.careerpilot.resume.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client for the object store that holds uploaded resume PDFs.
 *
 * <p>Reuses the same MinIO root credentials the {@code minio} container is configured
 * with in {@code docker-compose.yml} rather than provisioning a dedicated bucket-scoped
 * identity -- consistent with how this project already trusts a single Postgres superuser
 * to create each service's own role at startup (see {@code infra/postgres/init.sql}).
 * A per-service MinIO access key would be the stricter production posture; not worth the
 * extra setup at this project's scale and timeline.
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@Slf4j
public class MinioConfig {

    private final MinioProperties properties;

    public MinioConfig(MinioProperties properties) {
        this.properties = properties;
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * Creates the bucket on startup if it does not exist, so a fresh {@code docker compose
     * up} needs no manual MinIO console step before the first upload works.
     */
    @Bean
    public ApplicationRunner ensureBucketExists(MinioClient minioClient) {
        return args -> {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                log.info("Created MinIO bucket '{}'", properties.bucket());
            }
        };
    }
}
