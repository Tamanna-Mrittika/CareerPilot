package com.careerpilot.job.repository;

import com.careerpilot.job.domain.Job;
import com.careerpilot.job.domain.JobSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findBySourceAndExternalId(JobSource source, String externalId);

    List<Job> findByContentHash(String contentHash);

    long countBySource(JobSource source);

    /**
     * Full-text search over the generated {@code search_vector} column, with optional
     * filters. Native because {@code tsvector}, {@code plainto_tsquery} and {@code ts_rank}
     * have no JPQL equivalent -- and this is exactly the query that would otherwise justify
     * running an Elasticsearch container.
     *
     * <p>{@code :query} being null or blank short-circuits the text predicate, so the same
     * statement serves browsing and searching without a second code path.
     */
    @Query(value = """
            SELECT * FROM job j
            WHERE (:query IS NULL OR :query = ''
                   OR j.search_vector @@ plainto_tsquery('english', :query))
              AND (:scope = 'ALL'
                   OR (:scope = 'REMOTE' AND j.remote = TRUE)
                   OR (:scope = 'LOCAL' AND j.remote = FALSE))
              AND (:source IS NULL OR j.source = :source)
              AND (:employmentType IS NULL OR j.employment_type = :employmentType)
              AND (:minSalary IS NULL OR j.salary_max IS NULL OR j.salary_max >= :minSalary)
              AND (:country IS NULL OR LOWER(j.location_country) = LOWER(:country))
              -- City matched loosely: boards write "Dhaka", "Dhaka, Bangladesh" and
              -- "Dhaka Division" for the same place.
              AND (:city IS NULL
                   OR LOWER(j.location_city) LIKE LOWER(CONCAT('%', :city, '%'))
                   OR LOWER(j.location_raw) LIKE LOWER(CONCAT('%', :city, '%')))
            ORDER BY
              CASE WHEN :query IS NULL OR :query = '' THEN 0
                   ELSE ts_rank(j.search_vector, plainto_tsquery('english', :query)) END DESC,
              j.posted_at DESC NULLS LAST
            """,
            countQuery = """
            SELECT count(*) FROM job j
            WHERE (:query IS NULL OR :query = ''
                   OR j.search_vector @@ plainto_tsquery('english', :query))
              AND (:scope = 'ALL'
                   OR (:scope = 'REMOTE' AND j.remote = TRUE)
                   OR (:scope = 'LOCAL' AND j.remote = FALSE))
              AND (:source IS NULL OR j.source = :source)
              AND (:employmentType IS NULL OR j.employment_type = :employmentType)
              AND (:minSalary IS NULL OR j.salary_max IS NULL OR j.salary_max >= :minSalary)
              AND (:country IS NULL OR LOWER(j.location_country) = LOWER(:country))
              -- City matched loosely: boards write "Dhaka", "Dhaka, Bangladesh" and
              -- "Dhaka Division" for the same place.
              AND (:city IS NULL
                   OR LOWER(j.location_city) LIKE LOWER(CONCAT('%', :city, '%'))
                   OR LOWER(j.location_raw) LIKE LOWER(CONCAT('%', :city, '%')))
            """,
            nativeQuery = true)
    Page<Job> search(@Param("query") String query,
                     @Param("scope") String scope,
                     @Param("city") String city,
                     @Param("source") String source,
                     @Param("employmentType") String employmentType,
                     @Param("minSalary") java.math.BigDecimal minSalary,
                     @Param("country") String country,
                     Pageable pageable);

    /**
     * Fuzzy company lookup via the pg_trgm similarity operator -- tolerates the spelling
     * drift that is routine across boards ("Google" vs "Google LLC").
     */
    @Query(value = """
            SELECT * FROM job j
            WHERE j.company % :company
            ORDER BY similarity(j.company, :company) DESC
            LIMIT 50
            """, nativeQuery = true)
    List<Job> findByCompanyFuzzy(@Param("company") String company);

    /** Housekeeping: drops postings no longer seen in any feed. */
    @Modifying
    @Query("delete from Job j where j.updatedAt < :cutoff")
    int deleteStaleBefore(@Param("cutoff") Instant cutoff);

    @Query("select count(distinct j.contentHash) from Job j")
    long countDistinctContentHashes();
}
