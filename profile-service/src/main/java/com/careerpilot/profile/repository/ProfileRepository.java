package com.careerpilot.profile.repository;

import com.careerpilot.profile.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    /**
     * Loads a profile; its education, experience and skill collections are then filled in
     * by Hibernate batch fetching when the mapper touches them inside the transaction.
     *
     * <p>There is deliberately no {@code @EntityGraph} join-fetching all three collections.
     * Hibernate rejects that outright ({@code MultipleBagFetchException}: multiple bags),
     * and the usual workaround -- retyping them as {@code Set} -- only hides the real
     * problem, because a single query joining three one-to-many collections returns their
     * cartesian product: 3 education x 4 experience x 10 skills is 120 rows to build one
     * profile.
     *
     * <p>With {@code default_batch_fetch_size} configured, this instead costs a small
     * constant number of queries (profile, then one per collection) and no row
     * multiplication. It is not the N+1 it superficially resembles.
     */
    Optional<Profile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
