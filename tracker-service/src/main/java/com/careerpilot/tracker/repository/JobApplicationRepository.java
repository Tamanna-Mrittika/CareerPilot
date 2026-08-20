package com.careerpilot.tracker.repository;

import com.careerpilot.tracker.domain.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    /**
     * Scoped to the owner in the query itself, so a caller cannot even observe that another
     * user's application exists -- same pattern as resume-service's findByIdAndUserId.
     */
    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

    /** Guards against the same job being tracked twice on one board. */
    Optional<JobApplication> findByUserIdAndJobId(UUID userId, UUID jobId);

    /**
     * Candidate applications an inbound email might refer to. Terminal ones are excluded:
     * an email should never resurrect a closed application, and matching against them only
     * creates false positives.
     */
    @org.springframework.data.jpa.repository.Query("""
            select a from JobApplication a
            where a.status not in (com.careerpilot.tracker.domain.ApplicationStatus.REJECTED,
                                   com.careerpilot.tracker.domain.ApplicationStatus.WITHDRAWN)
            """)
    List<JobApplication> findAllActive();
}
