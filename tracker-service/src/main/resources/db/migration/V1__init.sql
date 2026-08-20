-- tracker-service owns the 'tracker' schema exclusively.

CREATE TABLE job_application (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL,
    -- Soft link to job-service. Nullable: a user can track a role that was never in our
    -- corpus. No foreign key -- that table lives in another service's schema.
    job_id      UUID,
    -- Title and company are copied, not referenced: a posting deleted upstream must not
    -- erase the user's own record of having applied to it.
    job_title   VARCHAR(500) NOT NULL,
    company     VARCHAR(300) NOT NULL,
    apply_url   VARCHAR(1000),
    location    VARCHAR(300),
    status      VARCHAR(20)  NOT NULL DEFAULT 'WISHLIST',
    notes       VARCHAR(4000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_job_application_user ON job_application (user_id, updated_at DESC);
-- One card per job per user; enforced by the database so concurrent creates cannot both
-- pass a service-layer check. Partial, because job_id is null for externally-found roles
-- and several of those must be allowed.
CREATE UNIQUE INDEX ux_job_application_user_job
    ON job_application (user_id, job_id) WHERE job_id IS NOT NULL;
CREATE INDEX ix_job_application_status ON job_application (status);

CREATE TABLE application_event (
    id             UUID PRIMARY KEY,
    application_id UUID        NOT NULL REFERENCES job_application (id) ON DELETE CASCADE,
    -- Null on the creation event, which has no prior state.
    from_status    VARCHAR(20),
    to_status      VARCHAR(20) NOT NULL,
    -- MANUAL / EMAIL_WEBHOOK / INITIAL: lets the UI show why a card moved on its own.
    source         VARCHAR(20) NOT NULL,
    note           VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_application_event_application ON application_event (application_id, created_at);
