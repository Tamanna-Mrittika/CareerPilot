-- matching-service owns the 'matching' schema exclusively.
--
-- No profile, job or fit-score data is persisted here: a fit score is computed fresh on
-- every request from live profile-service and job-service data (see FitScoreService),
-- the same on-demand design resume-service uses for ATS scoring. Persisting scores per
-- (user, job) pair would need invalidation logic every time either side changed, for a
-- computation cheap enough to just redo. The only thing worth persisting is the course
-- catalog, because it is curated content, not a derived value.

CREATE TABLE course (
    id         UUID PRIMARY KEY,
    -- Matches profile-service's skill.slug. No foreign key: that table lives in another
    -- service's schema this role has no grants on, same reasoning as every other
    -- cross-service join key in this system.
    skill_slug VARCHAR(120) NOT NULL,
    provider   VARCHAR(100) NOT NULL,
    title      VARCHAR(300) NOT NULL,
    url        VARCHAR(500) NOT NULL
);

CREATE INDEX ix_course_skill_slug ON course (skill_slug);
