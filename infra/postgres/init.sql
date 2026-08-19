-- Schema-per-service isolation.
--
-- True database-per-service would mean six Postgres containers on a student laptop. This
-- is the standard pragmatic compromise: one instance, but each service gets its own schema
-- AND its own login role, with grants only on that schema. REVOKE ALL on the public schema
-- and the absence of cross-schema grants mean a service physically cannot read another
-- service's tables, even by accident -- isolation enforced by the database rather than by
-- developer discipline.
--
-- Verify the isolation live (this must fail):
--   docker compose exec postgres psql -U profile_svc -d careerpilot \
--     -c 'SELECT * FROM identity.user_account;'
--   ERROR: permission denied for schema identity

\set ON_ERROR_STOP on

-- Password hashing for job-provider credentials is not needed, but pg_trgm powers the
-- fuzzy company/title search in job-service, replacing an Elasticsearch container.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Nobody creates objects in public.
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- ---------------------------------------------------------------------------
-- One role + one schema per service.
-- ---------------------------------------------------------------------------

CREATE ROLE identity_svc  LOGIN PASSWORD 'identity_pw';
CREATE ROLE profile_svc   LOGIN PASSWORD 'profile_pw';
CREATE ROLE resume_svc    LOGIN PASSWORD 'resume_pw';
CREATE ROLE job_svc       LOGIN PASSWORD 'job_pw';
CREATE ROLE matching_svc  LOGIN PASSWORD 'matching_pw';
CREATE ROLE tracker_svc   LOGIN PASSWORD 'tracker_pw';

CREATE SCHEMA identity AUTHORIZATION identity_svc;
CREATE SCHEMA profile  AUTHORIZATION profile_svc;
CREATE SCHEMA resume   AUTHORIZATION resume_svc;
CREATE SCHEMA job      AUTHORIZATION job_svc;
CREATE SCHEMA matching AUTHORIZATION matching_svc;
CREATE SCHEMA tracker  AUTHORIZATION tracker_svc;

-- Each role sees only its own schema by default; no search_path leakage into others.
ALTER ROLE identity_svc  SET search_path = identity;
ALTER ROLE profile_svc   SET search_path = profile;
ALTER ROLE resume_svc    SET search_path = resume;
-- job_svc also needs public on its search_path: pg_trgm installs gin_trgm_ops, the
-- '%' similarity operator and similarity() there, and job-service's fuzzy company
-- lookup cannot resolve them otherwise. USAGE on public grants no access to any
-- other service's schema, so isolation is unaffected.
ALTER ROLE job_svc       SET search_path = job, public;
ALTER ROLE matching_svc  SET search_path = matching;
ALTER ROLE tracker_svc   SET search_path = tracker;

-- Flyway needs to create its history table and the service's own objects; ownership of the
-- schema already grants that. Nothing else is granted to anyone.
GRANT CONNECT ON DATABASE careerpilot TO
    identity_svc, profile_svc, resume_svc, job_svc, matching_svc, tracker_svc;

-- pg_trgm lives in public, so job_svc needs to resolve operators there at query time.
-- USAGE only: it can call functions, not create anything.
GRANT USAGE ON SCHEMA public TO job_svc;
