package com.careerpilot.resume.service;

import com.careerpilot.common.error.UpstreamUnavailableException;
import com.careerpilot.resume.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Stores and retrieves resume PDF bytes in MinIO.
 *
 * <p>Never the database, never the container filesystem -- exactly the isolation the
 * original project proposal argued for: the CPU/memory-heavy parsing work in
 * {@code resume-service} is decoupled from where the bytes live, so this service can be
 * scaled, restarted or rescheduled without touching the actual files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    /** Object key is derived from a fresh UUID, never from the user-supplied filename. */
    public String store(byte[] pdfBytes, String contentType) {
        String objectKey = "resumes/" + UUID.randomUUID() + ".pdf";
        try (InputStream in = new ByteArrayInputStream(pdfBytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(in, pdfBytes.length, -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            log.error("Failed to store resume in MinIO", e);
            throw new UpstreamUnavailableException("Could not store the uploaded file. Please try again.");
        }
    }

    public byte[] retrieve(String objectKey) {
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build())) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UpstreamUnavailableException("Could not read the stored resume file.");
        } catch (Exception e) {
            log.error("Failed to retrieve resume {} from MinIO", objectKey, e);
            throw new UpstreamUnavailableException("Could not read the stored resume file.");
        }
    }
}
