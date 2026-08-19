-- resume-service owns the 'resume' schema exclusively.
--
-- The PDF bytes themselves are never stored here -- only a MinIO object key. Keeping large
-- binary payloads out of Postgres is deliberate: this table stays small and fast regardless
-- of corpus size, and backup/replication of the database never has to move gigabytes of PDFs.

CREATE TABLE resume_upload (
    id                         UUID PRIMARY KEY,
    user_id                    UUID         NOT NULL,
    original_filename          VARCHAR(500) NOT NULL,
    size_bytes                 BIGINT       NOT NULL,
    -- Derived from a UUID at upload time, never from the user-supplied filename.
    storage_object_key         VARCHAR(255) NOT NULL,
    status                     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_message              VARCHAR(2000),
    -- Kept so re-scoring against a different job never needs to re-parse the PDF.
    extracted_text             TEXT,
    -- Inferred from date ranges found in the resume text -- deliberately independent of
    -- profile-service's own years_experience, which comes from structured user entries.
    inferred_years_experience  INTEGER,
    uploaded_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at               TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_resume_upload_storage_key ON resume_upload (storage_object_key);
CREATE INDEX ix_resume_upload_user ON resume_upload (user_id, uploaded_at DESC);

CREATE TABLE extracted_skill (
    id                UUID PRIMARY KEY,
    resume_upload_id  UUID         NOT NULL REFERENCES resume_upload (id) ON DELETE CASCADE,
    skill_slug        VARCHAR(120) NOT NULL,
    skill_name        VARCHAR(120) NOT NULL,
    category          VARCHAR(60)  NOT NULL,
    occurrence_count  INTEGER      NOT NULL DEFAULT 1
);
CREATE INDEX ix_extracted_skill_resume ON extracted_skill (resume_upload_id);

CREATE TABLE ats_check (
    id                UUID PRIMARY KEY,
    resume_upload_id  UUID        NOT NULL REFERENCES resume_upload (id) ON DELETE CASCADE,
    check_type        VARCHAR(30) NOT NULL,
    severity          VARCHAR(10) NOT NULL,
    message           VARCHAR(500) NOT NULL
);
CREATE INDEX ix_ats_check_resume ON ats_check (resume_upload_id);

CREATE TABLE suggestion (
    id                UUID         PRIMARY KEY,
    resume_upload_id  UUID         NOT NULL REFERENCES resume_upload (id) ON DELETE CASCADE,
    category          VARCHAR(30)  NOT NULL,
    severity          VARCHAR(10)  NOT NULL,
    message           VARCHAR(500) NOT NULL,
    -- The offending line verbatim; null for document-level findings (e.g. MISSING_SECTION).
    evidence          VARCHAR(500)
);
CREATE INDEX ix_suggestion_resume ON suggestion (resume_upload_id);
