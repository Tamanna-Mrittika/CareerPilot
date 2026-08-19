-- profile-service owns the 'profile' schema exclusively.
--
-- user_id is a bare UUID with no foreign key: the users table lives in the identity
-- schema, which this service has no grants on. Referential integrity across a service
-- boundary is the application's responsibility, and that is the deliberate cost of
-- service-owned data.

CREATE TABLE profile (
    id                UUID PRIMARY KEY,
    user_id           UUID         NOT NULL,
    full_name         VARCHAR(120) NOT NULL,
    headline          VARCHAR(160),
    summary           VARCHAR(4000),
    email             VARCHAR(254),
    phone             VARCHAR(40),
    location_city     VARCHAR(120),
    location_country  VARCHAR(120),
    remote_preference VARCHAR(16)  NOT NULL DEFAULT 'ANY',
    -- Derived from experience rows, never accepted from the client.
    years_experience  INTEGER,
    linkedin_url      VARCHAR(500),
    github_url        VARCHAR(500),
    portfolio_url     VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- One profile per user, enforced by the database rather than by a service-layer check
-- that would race under concurrent first-access requests.
CREATE UNIQUE INDEX ux_profile_user ON profile (user_id);

CREATE TABLE education (
    id             UUID PRIMARY KEY,
    profile_id     UUID         NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    institution    VARCHAR(200) NOT NULL,
    degree         VARCHAR(200),
    field_of_study VARCHAR(200),
    start_date     DATE,
    end_date       DATE,
    grade          VARCHAR(50),
    description    VARCHAR(2000)
);
CREATE INDEX ix_education_profile ON education (profile_id);

CREATE TABLE experience (
    id              UUID PRIMARY KEY,
    profile_id      UUID         NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    company         VARCHAR(200) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    employment_type VARCHAR(50),
    location_city   VARCHAR(120),
    start_date      DATE         NOT NULL,
    end_date        DATE,
    current         BOOLEAN      NOT NULL DEFAULT FALSE,
    description     VARCHAR(4000),
    -- A role cannot simultaneously be current and have an end date.
    CONSTRAINT ck_experience_current_has_no_end CHECK (NOT (current AND end_date IS NOT NULL)),
    CONSTRAINT ck_experience_dates_ordered CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_experience_profile ON experience (profile_id);

-- ---------------------------------------------------------------------------
-- Canonical skill taxonomy. Seeded by migration, read-only at runtime: letting users
-- invent skills would fragment the vocabulary that matching depends on.
-- ---------------------------------------------------------------------------

CREATE TABLE skill (
    id       UUID PRIMARY KEY,
    name     VARCHAR(120) NOT NULL,
    slug     VARCHAR(120) NOT NULL,
    category VARCHAR(60)  NOT NULL
);
CREATE UNIQUE INDEX ux_skill_name ON skill (name);
CREATE UNIQUE INDEX ux_skill_slug ON skill (slug);
CREATE INDEX ix_skill_category ON skill (category);

CREATE TABLE skill_alias (
    skill_id UUID         NOT NULL REFERENCES skill (id) ON DELETE CASCADE,
    alias    VARCHAR(120) NOT NULL,
    PRIMARY KEY (skill_id, alias)
);
-- Aliases must be globally unique, or "go" could resolve to two different skills and
-- resume extraction would become non-deterministic.
CREATE UNIQUE INDEX ux_skill_alias ON skill_alias (alias);

CREATE TABLE profile_skill (
    id                    UUID PRIMARY KEY,
    profile_id            UUID        NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    skill_id              UUID        NOT NULL REFERENCES skill (id) ON DELETE CASCADE,
    proficiency           VARCHAR(20) NOT NULL DEFAULT 'INTERMEDIATE',
    years_experience      INTEGER,
    extracted_from_resume BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT ux_profile_skill UNIQUE (profile_id, skill_id)
);
CREATE INDEX ix_profile_skill_profile ON profile_skill (profile_id);
CREATE INDEX ix_profile_skill_skill ON profile_skill (skill_id);
