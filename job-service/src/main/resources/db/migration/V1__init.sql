-- job-service owns the 'job' schema exclusively.

CREATE TABLE job (
    id                UUID PRIMARY KEY,
    source            VARCHAR(20)  NOT NULL,
    -- The provider's own identifier. Combined with source it forms the upsert key, so
    -- re-ingesting refreshes a posting instead of duplicating it every six hours.
    external_id       VARCHAR(255) NOT NULL,
    title             VARCHAR(500) NOT NULL,
    company           VARCHAR(300) NOT NULL,
    location_raw      VARCHAR(300),
    location_city     VARCHAR(120),
    location_country  VARCHAR(120),
    remote            BOOLEAN      NOT NULL DEFAULT FALSE,
    employment_type   VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    -- Plain text: provider HTML is stripped at ingestion so full-text search and the
    -- downstream TF-IDF scorer see prose rather than markup.
    description       TEXT,
    salary_min        NUMERIC(12, 2),
    salary_max        NUMERIC(12, 2),
    salary_currency   VARCHAR(8),
    -- Verbatim provider text ("$120 - $170 /hour"). Kept because parsing it is lossy and
    -- an hourly rate stored as an annual figure would silently corrupt salary filtering.
    salary_raw        VARCHAR(255),
    apply_url         VARCHAR(1000) NOT NULL,
    posted_at         TIMESTAMPTZ,
    -- SHA-256 over normalised title + company + remote flag. The same vacancy listed on
    -- several boards collides here on purpose.
    content_hash      VARCHAR(64)  NOT NULL,
    ingested_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_job_source_external ON job (source, external_id);
CREATE INDEX ix_job_content_hash ON job (content_hash);
CREATE INDEX ix_job_posted_at ON job (posted_at DESC NULLS LAST);
CREATE INDEX ix_job_remote ON job (remote) WHERE remote = TRUE;
CREATE INDEX ix_job_country ON job (LOWER(location_country));
-- Local search filters on city, so it needs its own index.
CREATE INDEX ix_job_city ON job (LOWER(location_city));
CREATE INDEX ix_job_updated_at ON job (updated_at);

CREATE TABLE job_tag (
    job_id UUID         NOT NULL REFERENCES job (id) ON DELETE CASCADE,
    tag    VARCHAR(100) NOT NULL,
    PRIMARY KEY (job_id, tag)
);
CREATE INDEX ix_job_tag_tag ON job_tag (LOWER(tag));

-- ---------------------------------------------------------------------------
-- Full-text search.
--
-- A GENERATED column keeps the vector in step with the row automatically -- no trigger to
-- forget and no application code that can leave the index stale after an update. Weighting
-- title (A) above company (B) above description (C) means a search for "kubernetes" ranks
-- a Kubernetes Engineer above a posting that merely mentions kubernetes in paragraph nine.
--
-- This plus pg_trgm is what lets the system do relevance-ranked search without running an
-- Elasticsearch container.
-- ---------------------------------------------------------------------------
ALTER TABLE job ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', COALESCE(title, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(company, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(description, '')), 'C')
    ) STORED;

CREATE INDEX ix_job_search_vector ON job USING GIN (search_vector);

-- Trigram index for fuzzy company lookup ("Google" vs "Google LLC"). Requires the pg_trgm
-- extension created in infra/postgres/init.sql.
CREATE INDEX ix_job_company_trgm ON job USING GIN (company public.gin_trgm_ops);
