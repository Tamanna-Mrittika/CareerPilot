package com.careerpilot.resume.repository;

import com.careerpilot.resume.domain.ResumeUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeUploadRepository extends JpaRepository<ResumeUpload, UUID> {

    List<ResumeUpload> findByUserIdOrderByUploadedAtDesc(UUID userId);

    /**
     * Scoped to the owner in the query itself, not checked afterward -- a caller can never
     * even observe that a resume with a given id exists if it belongs to someone else.
     */
    Optional<ResumeUpload> findByIdAndUserId(UUID id, UUID userId);
}
