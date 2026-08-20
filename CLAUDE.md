# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

CareerPilot: a microservices job-discovery and resume-intelligence platform (CSE 4636 Web
Architecture coursework, team: Nashat Islam 220041211, Subha Tamanna Mrittika 220041227).
The instructor grades backend/infrastructure, not the frontend — every design decision in
this repo leans into service topology, resilience, security and observability
accordingly. Upload a resume → skills extracted → scored against live job postings →
explainable fit score. `README.md` has the pitch-level narrative; this file is the
operational one — read this first when resuming work.

Git repo: https://github.com/Tamanna-Mrittika/CareerPilot (branch `main`).

## Commands

```bash
# Build everything (JDK 21 required; mvnw bootstraps Maven itself, no system Maven needed)
./mvnw package -DskipTests

# Build one module -- MUST include -am or it fails on the careerpilot-common dependency
./mvnw -pl job-service -am package -DskipTests

# Run the full stack (core services only)
cp .env.example .env   # only if .env doesn't already exist -- see "Secrets" below, it does
docker compose up -d --build

# Add observability (Prometheus/Grafana/Zipkin) -- opt-in, ~1.3GB extra RAM
docker compose --profile observability up -d
# tearing down needs the same flag, or containers linger:
docker compose --profile observability down

# Wipe and rebuild from scratch (re-runs infra/postgres/init.sql, which only executes
# once against an empty volume -- required after changing it)
docker compose down -v && docker compose up -d --build

# Rebuild + redeploy a single service after a code change
./mvnw -pl <module> -am package -DskipTests && docker compose up -d --build <module>
```

### Tests

93 unit tests, all green, **runnable fully offline** (`mvn -o -B test`) — no Docker, no
network, no running stack. `spring-boot-starter-test` is inherited from the root
`<dependencies>`, so JUnit 5 + AssertJ + Mockito are already on every module's test
classpath; nothing needs adding to a module POM to write a test.

```bash
mvn -o -B test                       # whole reactor, offline
mvn -o -B -pl tracker-service -am test   # one module -- -am is required, same as packaging
```

One caveat: `./mvnw` re-bootstraps Maven over the network under Git Bash, so use the
installed `mvn` (see the PowerShell block below) when offline.

Coverage is deliberately **narrow and deep** rather than broad: the five pure-logic units
where correctness is actually arguable and a silent regression would produce confidently
wrong output, not CRUD or wiring.

| Test | Guards |
|---|---|
| `EmailClassifierTest` | rejection patterns checked **before** interview ones |
| `ApplicationStatusTest` | legal/illegal transitions, terminal states, no self-transition |
| `JobNormalizerTest` | dedup hash — collapses cross-board duplicates, keeps different cities apart |
| `FitScoreServiceTest` | rarity weighting, `-1` re-normalisation, `UNASSESSABLE_SKILLS_SCORE_CAP` |
| `ResumeSectionAnalyzerTest` | overlapping-date merge, experience-section scoping |

**Every one of these was mutation-checked**: the rule it guards was temporarily inverted in
the production code and the suite confirmed red, then reverted. This caught a test that
looked right and was worthless — the headline "rejection beats interview" case used an
email containing the *word* "interviewing" but matching no interview *pattern*, so it
passed even with the two checks swapped. It now asserts the interview half classifies as
INTERVIEWING **on its own** before asserting the whole email is REJECTED, so the fixture
cannot silently stop exercising the ordering. Apply the same check to any new test over
these rules; a green test proves nothing until you have seen it fail.

WireMock and Testcontainers are on the classpath in the relevant modules but unused so far —
integration tests against a real Postgres/Redis are the natural next layer.

### Windows/PowerShell environment note

Developed entirely from PowerShell/Git Bash on Windows. No system-wide Maven or Docker CLI
on PATH by default in a fresh shell. Working toolchain (already installed on this machine):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
$env:Path = "$env:JAVA_HOME\bin;C:\Users\ACER\tools\apache-maven-3.9.16\bin;C:\Program Files\Docker\Docker\resources\bin;$env:Path"
```

JDK 21 (Temurin), Maven 3.9.16 (checksum-verified, installed to
`C:\Users\ACER\tools\apache-maven-3.9.16`), Docker Desktop 4.87 / engine 29.7.2 are all
installed. Docker Desktop must be running (`docker info` to check) before any
`docker compose` command.

### Useful endpoints once the stack is up

| Surface | URL |
|---|---|
| API gateway (only published app port) | http://localhost:8080 |
| Swagger (all services aggregated) | http://localhost:8080/swagger-ui.html |
| Eureka dashboard | http://localhost:8761 |
| Grafana (needs `--profile observability`) | http://localhost:3000 |
| Prometheus (needs `--profile observability`) | http://localhost:9090 |
| Zipkin (needs `--profile observability`) | http://localhost:9411 |
| MinIO console | http://localhost:9001 |

### Creating an ADMIN test user

Roles are baked into the JWT **at token issue time**, so promoting a user requires a
re-login, not just a DB update:

```bash
# 1. register normally via POST /api/v1/auth/register
# 2. promote in Postgres:
docker compose exec -T postgres psql -U postgres -d careerpilot \
  -c "INSERT INTO identity.user_role (user_id, role) VALUES ('<user-id>','ADMIN') ON CONFLICT DO NOTHING;"
# 3. re-login via POST /api/v1/auth/login to get a token that actually carries ADMIN
```

Needed for `POST /api/v1/jobs/ingest` (ADMIN-only, since it spends real external API
quota/money — see job-service section below).

## Architecture

Monorepo, Maven multi-module, Java 21 / Spring Boot 3.5.3 / Spring Cloud 2025.0.0 (versions
pinned against Maven Central in the root `pom.xml`, not guessed). One `docker compose up`
runs the whole system.

```
                     Browser (React / Vite, not started)
                              |
                    +---------v----------+
                    |    api-gateway     |   only application port on the host
                    |  JWT . rate limit  |
                    |  CORS . correlation|
                    +--+---+---+---+---+-+
       +---------------+   |   |   |   +---------------+
       v                   v   v   v                   v
 identity-service   profile-service  resume-service  job-service --> Remotive
  RS256 . JWKS       skills . PDF     Tika . PDFBox   dedup . cache    Arbeitnow
  refresh rotation                    Aho-Corasick    Resilience4j     RemoteOK
                                      IDF scoring                      Apify (Dhaka)
       |                   ^               ^               ^
       |                   +-------+-------+---------------+
       |                           |  API composition + fallbacks
       |                    matching-service
       |                     explainable fit score . skill gap . courses
       v
  Eureka   Postgres (schema-per-service)   Redis   MinIO
  Prometheus . Grafana . Zipkin (opt-in profile)
```

### Module status

Only uncomment a module in the root `pom.xml`'s `<modules>` block once its directory
actually exists; listing a missing directory fails the reactor before compiling anything.

| Module | State | Port |
|---|---|---|
| careerpilot-common | built, running | — (library) |
| discovery-server | built, running | 8761 (Eureka) |
| api-gateway | built, running | 8080 (only published host port) |
| identity-service | built, running | 8081 (internal) |
| profile-service | built, running | 8082 (internal) |
| job-service | built, running | 8084 (internal) |
| resume-service | built, running | 8083 (internal) |
| matching-service | built, running | 8085 (internal) |
| tracker-service | built, running | 8086 (internal) |
| frontend | **not started — next task** | — |

### Request path and trust boundary

`api-gateway` is the *only* service with a published host port; everything else is
reachable solely on the internal Docker network. The gateway validates JWTs at the edge
*and* every downstream service validates independently against identity-service's JWKS
(`/.well-known/jwks.json`) — this is deliberate defense in depth, not redundancy. The
gateway also strips any client-supplied `X-User-Id` header before injecting the verified
one from the token, so a forged header can never impersonate another user; services
authorize on the JWT subject (`CurrentUser` in `careerpilot-common`), never on that header.

Auth is RS256 + JWKS, not a shared HS256 secret — only identity-service holds the private
key (ephemeral dev keypair by default with a loud startup warning; `scripts/generate-keys.sh`
/ `.ps1` for persistent keys). `identity-service` also does refresh-token rotation with
**family-based reuse detection**: presenting an already-rotated token revokes the whole
token family, not just that token.

### `careerpilot-common`

Infrastructure only — JWT/JWKS resource-server config, correlation-ID propagation, RFC 7807
error rendering (`GlobalExceptionHandler`), `CurrentUser`. It deliberately holds **no
domain DTOs**. Each service defines its own view of a `Job`/`Profile`/`User`; sharing
domain types across service boundaries is how a microservices system quietly becomes a
distributed monolith. Auto-configured via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, so a
service gets correlation IDs and RFC 7807 errors just by depending on it — no manual
wiring. Scoped to servlet apps only (`api-gateway` is reactive/WebFlux and supplies its own
equivalents in `api-gateway/.../filter/`).

### Database: schema-per-service, one Postgres instance

`infra/postgres/init.sql` creates one schema *and one login role* per service, with grants
restricted to that schema only (`REVOKE ALL ON SCHEMA public FROM PUBLIC` plus per-role
`search_path`). Isolation is enforced by Postgres itself, not by convention — verify with:

```bash
docker compose exec postgres psql -U profile_svc -d careerpilot -c 'SELECT * FROM identity.user_account;'
# must fail: permission denied for schema identity
```

`job_svc` is the one exception: its `search_path` includes `public` too, because
`pg_trgm`'s `gin_trgm_ops`/`similarity()` live there and fuzzy company search needs them —
still `USAGE` only, no schema-crossing grants. Each service's own migrations live under
`<module>/src/main/resources/db/migration/`, run by Flyway with `ddl-auto: validate`
(Flyway owns the schema; Hibernate only checks its model against it).

`init.sql` runs once, only against an empty Postgres volume — `docker compose down -v`
before it will take effect again after an edit. Roles/schemas already provisioned:
`identity_svc`/`identity`, `profile_svc`/`profile`, `resume_svc`/`resume` (schema exists,
service doesn't yet), `job_svc`/`job`, `matching_svc`/`matching` (schema exists, service
doesn't yet), `tracker_svc`/`tracker` (schema exists, service doesn't yet).

### Skill taxonomy (`profile-service`)

149 skills / 186 aliases, seeded via `V2__seed_skills.sql`, generated and validated for
uniqueness before being written, because `skill_alias` carries a single global unique
index — one duplicate alias anywhere fails the migration and the service won't start.
`resume-service` (when built) should consume the full taxonomy via
`GET /api/v1/skills/taxonomy`, never touch this schema directly, and build an
Aho–Corasick automaton from the alias set for extraction. Searching an alias like `k8s`
correctly resolves to the canonical skill (`Kubernetes`).

### `job-service`: provider strategy + resilience

`JobProvider` is the extension point — each external board is one class implementing
`fetch(): Flux<RawJob>`. Adding or replacing a source touches exactly one file.
`JobIngestionService` wraps every provider in its **own** Resilience4j circuit breaker +
retry, so one board being down or rate-limited never blocks or fails the others
(`onErrorResume` turns a dead provider into an empty stream).

**Six providers wired up, real data verified live (471 postings ingested):**

| Provider | Key | Free tier | Coverage | Status |
|---|---|---|---|---|
| Remotive | no | open feed | remote tech | working, 17 postings |
| Arbeitnow | no | open feed | EU-leaning | working, 306 postings |
| RemoteOK | no | open feed | remote | working, 106 postings |
| Adzuna | yes | ~1,000/month | 16 countries, **excludes Bangladesh** | not configured (no key in `.env`) |
| JSearch | yes | 200/month | Google for Jobs | key is in `.env` but returns `403 "not subscribed"` — needs enabling on the openwebninja.com dashboard, not a code fix |
| **Apify** (`openclawai/job-board-scraper`) | yes | pay-per-run, $5/mo free credit | **the only verified source of real Dhaka jobs** | **working** — 42 postings, 27 in Dhaka, via LinkedIn (see below) |

**Local vs. remote are separate endpoints** (`/api/v1/jobs/local`, `/api/v1/jobs/remote`),
not one endpoint with a flag — they back different pages and merging them lets remote
volume drown out local results. Verified: `/jobs/local` → 27 real Dhaka postings (Software
Engineer at Optimizely, Backend Developer at nextjobz, etc.) end-to-end through the
gateway; `/jobs/remote` → 152, cleanly disjoint. Defaults to Dhaka/Bangladesh when no
city/country is supplied (`careerpilot.search.default-city`); note the country default is
intentionally **blank** in config, not "Bangladesh" — providers encode country
inconsistently ("BD" vs "Bangladesh"), so defaulting it would silently filter out
everything. City matching is the reliable default filter.

**Apify specifics — read before touching `ApifyProvider`:**
- The actor's own `bdjobs` and `google` boards return nothing (documented in the actor's
  own schema description as broken). `linkedin` is the board that actually works; Bdjobs
  content still arrives indirectly because Bdjobs.com cross-posts to LinkedIn.
- `linkedinFetchDescription: true` is mandatory in the request — without it, LinkedIn
  results carry no description, which is useless for the TF-IDF scoring resume-service
  will need to do.
- **Real measured cost is ~$0.24/run**, not the ~$0.003/job the actor's store listing
  implies — LinkedIn scraping carries real proxy/browser compute overhead on top of the
  per-result fee. This was discovered by checking actual per-run billing via Apify's API
  (`GET /v2/acts/.../runs` returns `usageTotalUsd` per run), not by trusting the
  advertised price.
- Because of that cost, **Apify runs on its own ingestion schedule**, decoupled from the
  free boards' `careerpilot.ingestion.cron` (every 6h). Its own cron
  (`careerpilot.ingestion.apify-cron`, default every 3 days, ~$2.40/month) is a safe
  starting point, not a tuned answer — check the Apify dashboard before changing cadence
  or `apify-max-results`. `JobIngestionService.ingestExcluding()` / `.ingestOnly()` plus a
  second `@Scheduled` method in `JobIngestionScheduler` implement the split — reuse this
  pattern if another cost-bearing provider is added.
- An immediate on-demand refresh (all providers, including Apify) remains available via
  `POST /api/v1/jobs/ingest`, ADMIN-only, for demo prep.
- **~$0.65 of the $5 free credit has already been spent** on testing/debugging today.
  Budget remaining runs accordingly.

**Cross-provider dedup**: `JobNormalizer.contentHash()` — SHA-256 over normalized
title + company + **city** + remote flag. City is included deliberately, even though board
formatting is messy, because hashing without it merged genuinely different vacancies (same
title/company in different cities) into one row; a duplicate shown twice is a smaller
failure than a real posting hidden. Verified: dedup correctly collapses genuine cross-board
duplicates without merging same-title-different-city jobs.

**`JobPersistenceService` is a separate bean from `JobIngestionService`** specifically
because Spring's `@Transactional` is proxy-based — a method called from another method on
the *same* bean bypasses the proxy and the annotation silently does nothing. If adding
transactional persistence logic, it needs to live in a bean the caller actually invokes
through, not one it calls into.

**Timeout layers — get all three consistent or a long-running provider silently loses its
results (this exact bug cost real Apify money to debug):**
1. Reactor `.timeout()` per provider, sourced from `JobProvider.fetchTimeout(properties)`
   (default = shared timeout; `ApifyProvider` overrides it, since an Apify actor run
   legitimately takes minutes).
2. The overall `blockOptional()` wait in `JobIngestionService.ingestAll()` — computed as
   the *max* of every enabled provider's `fetchTimeout()`, not a flat constant.
3. The shared Netty-level `HttpClient.responseTimeout()` in `WebClientConfig` — set as a
   generous backstop (6 minutes), never the enforcement point. A short value here
   previously fired before either of the above got a chance, and silently discarded two
   separate Apify runs that had already completed and been billed for (~$0.36 wasted this
   way before all three layers were found and fixed).

**Postgres full-text search + `pg_trgm`** (`JobRepository.search()`, native `tsvector`/
`ts_rank` query) stand in for Elasticsearch. The generated `search_vector` column in the
migration weights title > company > description.

### JPA pitfalls already hit in this codebase

- **`MultipleBagFetchException`**: an `@EntityGraph` join-fetching more than one `List`
  collection at once fails outright. The common workaround — retyping the collections to
  `Set` — just trades the exception for a silent cartesian product (3 education × 4
  experience × 10 skills = 120 rows to build one profile). This codebase uses Hibernate
  batch fetching (`default_batch_fetch_size` in each service's `application.yml`) instead —
  see `ProfileRepository.findByUserId()`.
- **Redis cache serialization**: `job-service`'s `CacheConfig` uses a *typed*
  `Jackson2JsonRedisSerializer<JobPage>`, not `GenericJackson2JsonRedisSerializer` with
  polymorphic default typing. The polymorphic version let writes succeed while every
  subsequent read threw — a bug that only appears on the second identical request. Bind any
  new cache to its concrete return type the same way. Verified fixed: repeat identical
  search now measurably faster (498ms cold → 55ms → 36ms) instead of 500ing.
- **Years-of-experience** in `profile-service` is computed server-side by **merging
  overlapping date ranges**, not summing durations — two concurrent roles (2022–2024 job +
  a 2023 contract) correctly yields 2 years, not 3.

### Observability

Every service ships Micrometer → Prometheus + Zipkin tracing (behind the `observability`
compose profile). Correlation IDs are minted at the gateway (`CorrelationIdGlobalFilter`,
reactive) and propagated via a servlet filter in `careerpilot-common`
(`CorrelationIdFilter`) — when adding a filter that sets response headers derived from
request state, set it in `beforeCommit()`/after downstream headers merge, not eagerly;
setting it too early on the gateway previously produced a duplicated `id,id` header value
once the proxied response's own header merged on top. Verified: a single Zipkin trace spans
`api-gateway` → `identity-service`.

## Verified end-to-end (all currently passing against the live stack)

- register → login → `/me` through the gateway; unauthenticated and tampered tokens both
  rejected with 401 at the edge
- refresh-token rotation, and reuse detection — replaying a consumed refresh token is
  rejected and revokes the family
- duplicate email → 409, weak password → 400, both as RFC 7807 `problem+json`
- JWKS exposes `kty/e/kid/n` and no private exponent
- schema isolation proven (see command above)
- forged `X-User-Id` header stripped at the gateway; `/me` still resolves the real user
- correlation IDs propagate exactly, client-supplied or server-minted
- profile CRUD, education/experience entries, skill assignment through the gateway
- unknown skill slug → 400 naming the slug, not silently dropped
- second user gets their own empty profile, no data leakage between users
- cover-letter endpoint returns a valid PDF composed from stored profile data
- 471 real job postings ingested live across 4 working providers
- `/jobs/local` (Dhaka) and `/jobs/remote` correctly disjoint, both return real data
- Redis cache measurably serves repeat searches faster
- every provider has an independent circuit breaker, reported by `/jobs/stats`

## Secrets / environment state (`.env`, git-ignored)

- `APIFY_TOKEN` — **set and working**, rotated to a fresh token (account
  `ecumenical_hairstreak`) with **$0 of $5 free credit used** as of the swap. The original
  token (which had $0.65 spent from earlier debugging) was pasted into a chat session and
  should be considered burned; the new one has not been exposed in conversation. Restart
  or recreate `job-service` for a `.env` token change to take effect (env var only, no
  rebuild needed). **Standing instruction from the user: before running anything that
  spends meaningful Apify credit (an ingestion run costs ~$0.24 — see job-service
  section), check remaining balance via
  `GET https://api.apify.com/v2/users/me/limits?token=$APIFY_TOKEN` and tell the user if
  it's close to exhausted rather than let it run dry silently. They'll supply a new token
  if needed.**
- `JSEARCH_API_KEY` — set but **not usable yet**: returns 403 until subscribed to the
  JSearch product on the openwebninja.com dashboard (account-level toggle, not a code fix).
- `ADZUNA_APP_ID` / `ADZUNA_APP_KEY` — **not configured**. Low priority: Adzuna doesn't
  cover Bangladesh anyway; it would only add non-Dhaka salary/location data.
- `GATEWAY_PORT` — set to 8080 (default). Was temporarily moved to 8090 during setup
  because MiniTool ShadowMaker's backup agent service held port 8080; that service was
  found, stopped, and disabled (`Set-Service -Name MTAgentService -StartupType Disabled`,
  same for `MTSchedulerService`). **Backups from that tool are currently off** as a result
  — worth knowing if this machine is relied on for backups. Re-enable with
  `Set-Service -StartupType Automatic` if needed.
- JWT signing keys — not mounted; identity-service uses an ephemeral keypair (regenerated
  on every restart, fine for dev). Run `scripts/generate-keys.sh`/`.ps1` and set
  `JWT_PRIVATE_KEY_LOCATION`/`JWT_PUBLIC_KEY_LOCATION` in `.env` for persistent keys.

## Docker Desktop won't start: stuck socket files (recurring on this machine)

Symptom -- Docker Desktop shows:

```
starting services: initializing <X>: listening on unix://C:/Users/ACER/AppData/Local/...
remove ...\<something>.sock: The file cannot be accessed by the system.
```

Cause: Docker's AF_UNIX socket files are left behind after an unclean shutdown. Windows
cannot delete them individually (`Remove-Item`, `[System.IO.File]::Delete` and `del` all
fail, and `ls` shows them as `-?????????` with unreadable metadata), and they survive
reboots. On startup Docker tries to *remove* the pre-existing socket before binding, and
that removal is what fails.

**This is NOT filesystem corruption** -- an earlier diagnosis in this project wrongly
concluded that and ran `chkdsk`, which did nothing. Moving the parent *directory* works
perfectly; only deleting the individual socket files fails. What made it look like
corruption was Docker Desktop still running and instantly recreating a socket inside the
freshly made directory.

**Fix** -- kill every Docker process first, or it recreates the sockets while you work,
then move the offending directory aside, recreate it EMPTY, and start Docker Desktop.
Two directories have needed this so far; check whichever the error names:

- `C:\Users\ACER\AppData\Local\Docker\run`
- `C:\Users\ACER\AppData\Local\docker-secrets-engine`

The leftover `*-quarantine-*` / `*-broken-*` folders are zero-byte and harmless.

Also note: Docker Desktop does **not** auto-start after a reboot on this machine, and
`docker info` *hangs* rather than erroring when it is mid-start or stuck -- poll with
`timeout 8 docker version` so a stuck daemon cannot block the shell.

## Housekeeping notes (machine-level, not project-level)

- A WSL distro (`Ubuntu`, unrelated coursework, not `opp_env`) was removed to free disk
  space during setup. `opp_env` (on D:, unaffected) and `docker-desktop` (required)
  remain.
- Four zero-byte files under
  `C:\Users\ACER\AppData\Local\Docker\run-broken-<timestamp>\` are leftover from an
  earlier disk-full crash; Windows refuses to delete them (real NTFS-level stuck state,
  not a Docker issue) but they cost nothing and are harmless.

## Remaining build order (from the original plan)

Plan file: `C:\Users\ACER\.claude\plans\okay-so-i-m-building-transient-comet.md` (full
detail; summarized here so this file is self-sufficient).

### `resume-service` — BUILT, port 8083

Upload -> MinIO -> **202 Accepted** with a `Location` header; parsing runs on a bounded
`@Async` executor (`AsyncConfig`), client polls `GET /api/v1/resumes/{id}` until status
leaves PENDING/PROCESSING. REST-only async, no message broker.

Pipeline (all verified against a real PDF end to end):
- **Tika** for canonical text extraction; **PDFBox** only for PDF-specific structural
  checks (`PdfStructuralAnalyzer`): page count, scanned-image detection, embedded images,
  missing contact info. No multi-column detector -- would need geometric text-run
  clustering for little gain over these four.
- **Aho-Corasick** skill extraction (`SkillTaxonomyCache` + `SkillExtractionService`) built
  from profile-service's `/api/v1/skills/taxonomy`. Uses `.onlyWholeWords().ignoreCase()`
  -- **critical**: without `onlyWholeWords()`, "go" matches inside "ergonomic" and "r"
  matches nearly everything.
- **IDF-weighted keyword scoring** (`AtsScoringService` + `JobCorpusIdfCache`) against a
  300-doc sample of job-service's live corpus, refreshed every 6h.
- Rule-based feedback (`ResumeFeedbackService`): weak verbs, unquantified bullets, passive
  voice, over-long bullets, first-person pronouns; each rule caps evidence at 8 lines.
- Section detection + date-range experience inference (`ResumeSectionAnalyzer`).

**Non-obvious things already fixed here -- do not reintroduce:**
- `ResumeUploadService.upload()` is `@Transactional`, so the async processor is triggered
  from `TransactionSynchronization.afterCommit()`, **not** inline. Calling it directly
  races the commit: the worker's own connection queries `findById()` before the INSERT is
  visible and logs "vanished before processing could start", leaving every upload stuck in
  PENDING forever. This actually happened.
- `ResumeProcessingService` is a **separate bean** from `ResumeUploadService` for the same
  proxy reason `JobPersistenceService` is separate in job-service -- `@Async` self-invocation
  silently runs synchronously.
- `ResumeController.publicBaseUrl()` reads `X-Forwarded-Host`/`-Proto` explicitly. This
  gateway build (`spring-cloud-starter-gateway-server-webflux`) does **not** send them by
  default -- verified by dumping every header downstream -- so `ForwardedHeadersGlobalFilter`
  in api-gateway was added to stamp them. Without both halves, the 202 `Location` header
  points at an unreachable internal Docker IP (`172.18.0.x:8083`).
- `AtsScoringService` returns `actionableGaps` alongside raw `missingTerms`: pure IDF ranks
  company names, cities and hashtags highest (rarity == high IDF), which is statistically
  right and useless as advice. Filtering to taxonomy-recognised skills turned 255 noisy
  terms into 6 useful ones on a real job.
- `WebClientConfig` raises `maxInMemorySize` to 16MB -- the 300-job corpus sample blows past
  WebClient's 256KB default (same fix as job-service, for the same reason).
- `profile-service` and `job-service` each gained a `SecurityConfig` making their **GET**
  routes public, because resume-service reads both on a background schedule with no user
  context. `POST /api/v1/jobs/ingest` stays ADMIN-only (explicit `HttpMethod.GET` scoping).

### `matching-service` — BUILT, port 8085

Composes profile-service + job-service into ranked, explainable matches. Owns almost no
data (only the 123-row free-course catalog); fit scores are computed fresh per request,
never persisted -- both inputs change independently and a stored score would need
invalidating on every profile edit and every ingestion run.

Endpoints: `/api/v1/matches/local`, `/matches/remote`, `/matches` (ALL),
`/matches/jobs/{jobId}`, and `/api/v1/skill-gap`.

**Fit score = 4 weighted components**, each returning its own number AND a sentence:
skills 0.55, experience 0.20, location 0.15, workStyle 0.10.

- **Skills** use rarity weighting from `SkillRarityIndex` (IDF over a 300-job sample of the
  live corpus), so matching Kubernetes counts for more than matching Git. `JobSkillExtractor`
  derives each job's implied skills by running the shared taxonomy over its title/tags/
  description with Aho-Corasick -- postings ship prose, not structured skill lists.
- **Inapplicable components score `-1`** and are excluded from the weighted average rather
  than scored 0 or 100 (e.g. location on a remote role genuinely does not apply; scoring it
  either way would distort remote-vs-local comparisons).

**Non-obvious things already fixed here -- do not reintroduce:**
- **Unassessable skills are capped, not excluded.** When a posting has no recognisable
  skills the component is `-1`, but the *overall* score is then capped at 50
  (`UNASSESSABLE_SKILLS_SCORE_CAP`). Without that cap such jobs scored a clean 100% on
  experience + workStyle alone and floated to the TOP of the ranking above genuinely good
  matches. "Could not assess" is not "perfect match".
- **Skill-gap ranks by demand, per-job gaps rank by rarity** -- deliberately opposite.
  For "what should I learn next?", a skill 59% of Dhaka postings want beats an exotic one
  appearing once, even though the exotic one scores higher on any individual match.
- `CurrentUser.rawToken()` (added to careerpilot-common) forwards the caller's own JWT to
  profile-service, because `/profiles/me` is user-scoped private data -- unlike the public
  taxonomy/job reads, which need no token.
- Course catalog slugs are **validated against profile-service's taxonomy at generation
  time**. A typo'd slug fails silently at runtime: the gap is still reported, it just
  recommends nothing, with no error anywhere.

### Cold-start race (bit both matching-service and resume-service)

Caches that load from a peer in `@PostConstruct` can fire before that peer has registered
with **Eureka** -- compose's `depends_on: service_healthy` waits for the healthcheck, not
for service discovery, so a cold start of the whole stack reliably loses the first attempt
(observed: 503 from an unresolved `lb://` host). Both `SkillRarityIndex` and
`JobCorpusIdfCache` now have a `retryUntilLoaded()` on a 90s `@Scheduled` that no-ops once
loaded. Without it the caches sat empty until the next 6-hourly tick and every score in
that window silently fell back to unweighted -- wrong answers, no error.

### `tracker-service` — BUILT, port 8086

Kanban application tracker. Two things here carry the marks, not the CRUD:

**1. Status is a real state machine, not a string.** Legal transitions live on the
`ApplicationStatus` enum itself so rules cannot drift from the states they govern. An
illegal move returns 409 naming both states and what *was* allowed
(`"Cannot move an application from APPLIED to WISHLIST. Allowed from APPLIED: [...]"`).
`REJECTED`/`WITHDRAWN` are terminal. Every card exposes `allowedTransitions` so the UI can
grey out illegal drop targets, and `GET /api/v1/applications/statuses` returns the whole
machine so a client never hardcodes the rules. Every change is recorded as an immutable
`ApplicationEvent` tagged MANUAL / EMAIL_WEBHOOK / INITIAL -- when a card moves on its own
the user can see which email did it.

**2. The email webhook is HMAC-authenticated, not JWT.** Its caller is a mail provider with
no user session, so `POST /api/v1/webhooks/email` is permitted through Spring Security
(and through the gateway's `PUBLIC_PATHS`) and authenticated instead by an
`X-Signature-256: sha256=<hex>` HMAC-SHA256 over the **raw request body**. Non-obvious bits:
- The controller takes `byte[]`, not a bound object -- re-serialising parsed JSON changes
  whitespace/key order and no signature would ever verify.
- Comparison uses `MessageDigest.isEqual` (constant-time). `equals` returns early on the
  first differing byte, leaking how much of a guessed signature was right.
- **Fails closed**: a blank `WEBHOOK_SECRET` rejects everything rather than accepting
  unsigned input.
- `/email/simulate` and `/email/sign` are ADMIN+JWT only, so a demo never needs the real
  endpoint weakened.

**Classifier ordering is load-bearing.** `EmailClassifier` checks rejection patterns
**before** interview ones, because rejection emails routinely contain interview vocabulary
("thank you for interviewing with us, however..."). Checking interview first misreads most
rejections as progress -- the most damaging error possible here. Verified: an email saying
both correctly resolves to REJECTED. No confident match returns empty and moves nothing; a
wrong automatic move costs more trust than an absent one.

Webhook responses are always 200 with a description, even when nothing matched -- returning
an error for "no card moved" makes a mail provider retry the same message forever.

### Cut list, in order, if the timeline gets tight

1. LLM resume-feedback pass (already off by default — costs nothing to drop)
2. Email-webhook auto-transition on the Kanban tracker → keep it manual
3. `tracker-service` entirely → fold a flat "applications" table into `profile-service`
   instead
4. Load testing (k6)
5. Cover-letter PDF (already built — only cut this if truly desperate)

**Never cut:** gateway, Eureka, resilience patterns, Redis caching, tracing,
resume→match end-to-end. Those are the actual grade.

### Not started, lower priority

- `tracker-service` (Kanban board, port TBD, schema already provisioned as `tracker`) —
  see cut list, likely first to go if time is short
- Frontend (React/Vite) — instructor doesn't grade this; keep minimal
- `docs/` — C4-style diagrams + short ADRs justifying the architectural decisions above,
  useful for the viva/demo but not functional
