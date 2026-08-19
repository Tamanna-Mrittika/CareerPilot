-- Adds short-form aliases for skills whose canonical name carries a vendor/organisation
-- prefix that people routinely drop in practice.
--
-- Found by a real extraction miss, not by inspection: a test resume mentioning "Kafka"
-- twice extracted no Kafka skill at all, because the taxonomy's canonical name is
-- "Apache Kafka" and its only alias was "apache-kafka" -- neither of which matches the
-- bare word almost every real resume actually uses. The matcher is whole-word
-- (onlyWholeWords), so "Apache Kafka" simply never fires on "Kafka".
--
-- Deliberately limited to proper nouns where the short form is unambiguous and standard.
-- Multi-word *descriptive* names (Machine Learning, Unit Testing, Data Structures,
-- System Design...) are intentionally NOT given last-word aliases: matching a bare
-- "Learning", "Testing" or "Design" would produce far more false positives than the
-- missed matches it would fix, and a wrong skill on a candidate's profile is worse than
-- an absent one.

INSERT INTO skill_alias (skill_id, alias)
SELECT s.id, a.alias
FROM skill s
JOIN (VALUES
    -- Apache-prefixed projects: the bare project name is the near-universal usage.
    ('kafka',                'kafka'),
    ('spark',                'spark'),
    ('apache-http-server',   'apache http'),
    -- Vendor-prefixed products.
    ('azure',                'azure'),
    ('sql-server',           'sql server'),
    ('oracle-database',      'oracle'),
    ('gcp',                  'google cloud platform'),
    ('power-bi',             'power bi'),
    ('material-ui',          'material ui'),
    -- Platform names that appear bare far more often than in full.
    ('android',              'android'),
    ('ios',                  'ios'),
    ('react-native',         'react native'),
    -- Spring portfolio: "Spring Boot"/"Spring Cloud"/"Spring Security" are themselves the
    -- standard forms, but the hyphenated/compact spellings show up in skills lists.
    ('spring-cloud',         'springcloud'),
    ('spring-security',      'springsecurity'),
    -- Common compact spellings.
    ('github-actions',       'githubactions'),
    ('tailwind-css',         'tailwind css'),
    ('functional-programming', 'fp')
) AS a(slug, alias) ON a.slug = s.slug
-- Idempotent: skill_alias has a global unique index on alias, so a re-run (or an alias
-- that already exists from the original seed) must not fail the migration.
WHERE NOT EXISTS (SELECT 1 FROM skill_alias existing WHERE existing.alias = a.alias);
