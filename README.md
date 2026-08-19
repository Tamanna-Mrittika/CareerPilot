# CareerPilot

Microservices job-discovery and resume-intelligence platform.
CSE 4636 Web Architecture — Nashat Islam (220041211), Subha Tamanna Mrittika (220041227).

Upload a resume, have your skills extracted and scored against live job postings
aggregated from multiple public job APIs, and get an explainable fit score plus a
prioritised list of what to fix.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | [Temurin 21](https://adoptium.net/temurin/releases/?version=21) |
| Docker Desktop | current | Includes Compose v2 |
| Node.js | 20+ | Frontend only (already installed) |

Maven is **not** required — the repo ships the Maven wrapper (`mvnw`), which downloads
Maven on first use.

Verify:

```bash
java -version && docker compose version
```

## Run

```bash
cp .env.example .env && ./mvnw package -DskipTests && docker compose up -d --build
```

That starts the 7 core services. The observability stack (Prometheus, Grafana, Zipkin) is
~1.3 GB of RAM that day-to-day development does not need, so it is opt-in:

```bash
docker compose --profile observability up -d
```

Compose only acts on services its profiles select, so tearing the full stack down needs
the flag too: `docker compose --profile observability down`.

Jars are built on the host and copied into thin images. A self-contained multi-stage
build would re-resolve the dependency tree once per service, turning a 40-second rebuild
into several minutes.

| Surface | URL |
|---|---|
| API gateway | http://localhost:8080 |
| Swagger (all services) | http://localhost:8080/swagger-ui.html |
| Eureka dashboard | http://localhost:8761 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Zipkin traces | http://localhost:9411 |
| MinIO console | http://localhost:9001 |

Tear down (`-v` also drops the database and re-runs `init.sql`):

```bash
docker compose down -v
```

---

## Architecture

```
                     Browser (React / Vite)
                              │
                    ┌─────────▼──────────┐
                    │    api-gateway     │   only application port on the host
                    │  JWT · rate limit  │
                    │  CORS · correlation│
                    └──┬───┬───┬───┬───┬─┘
       ┌───────────────┘   │   │   │   └───────────────┐
       ▼                   ▼   ▼   ▼                   ▼
 identity-service   profile-service  resume-service  job-service ──► Remotive
  RS256 · JWKS       skills · PDF     Tika · TF-IDF   dedup · cache    Arbeitnow
  refresh rotation                    ATS rules       Resilience4j     RemoteOK
       │                   ▲               ▲               ▲          Adzuna
       │                   └───────┬───────┴───────────────┘
       │                           │  API composition + fallbacks
       │                    matching-service
       │                     explainable fit score · skill gap
       ▼
  Eureka   Postgres (schema-per-service)   Redis   MinIO
  Prometheus · Grafana · Zipkin
```

### Decisions worth knowing

**Schema-per-service, one Postgres.** Six Postgres containers on a laptop is wasteful, so
each service instead gets its own schema *and its own login role*, with grants only on
that schema. Isolation is enforced by the database, not by developer discipline. Prove it:

```bash
docker compose exec postgres psql -U profile_svc -d careerpilot -c 'SELECT * FROM identity.user_account;'
```

That must fail with `permission denied for schema identity`. If it ever succeeds, the
isolation claim is broken.

**`careerpilot-common` carries infrastructure only** — JWT validation, correlation IDs,
RFC 7807 error rendering. Deliberately no domain DTOs: each service owns its own view of
a Job or Profile. Sharing domain types across service boundaries is how a microservice
system quietly becomes a distributed monolith.

**RS256 with JWKS, not a shared HS256 secret.** Only identity-service holds the private
key; peers verify with the public key fetched from `/.well-known/jwks.json`. Compromising
a downstream service therefore does not let an attacker mint tokens.

**Defence in depth.** The gateway validates the JWT *and* strips any client-supplied
`X-User-Id` before injecting the real one. Services then validate the token again
themselves and authorise on the token subject, never on the header.

**Postgres full-text (`tsvector` + GIN, `pg_trgm`) instead of Elasticsearch** — one fewer
container, and entirely adequate at this corpus size.

---

## Live job data

LinkedIn and Indeed have **no public job-search API** (LinkedIn's is partner-only; Indeed
retired its Publisher API), so CareerPilot aggregates several sources behind one
`JobProvider` interface.

| Provider | Key | Free tier | Coverage |
|---|---|---|---|
| Remotive | no | open feed | remote tech |
| Arbeitnow | no | open feed | EU-leaning, visa-sponsor flags |
| RemoteOK | no | open feed | remote |
| Adzuna | yes, instant | ~1,000 calls/**month** | 16 countries **(not Bangladesh)**, salary + locations |
| JSearch | yes, free | 200 calls/**month** | Google for Jobs; the only route to Bangladesh |

### Local vs remote are separate pages

`GET /api/v1/jobs/local` and `GET /api/v1/jobs/remote` are distinct endpoints, not one
endpoint with a flag, because they are genuinely different job hunts: local is "employers
I can commute to in Dhaka", remote is "I am competing globally". Merged into one list, a
few hundred remote postings bury the handful of local ones.

### Bangladesh coverage: solved via Apify, verified live

**None of the four original providers can return a Dhaka job.** Adzuna's 16 countries
exclude Bangladesh; Remotive and RemoteOK are remote-only; Arbeitnow is EU-focused.

Two more were evaluated and added:

- **JSearch** (Google for Jobs) -- wired up, but the configured key returns
  `403 "You are not subscribed to this API"`. That is an account-level subscription
  toggle on openwebninja's dashboard, not a code problem; JSearch stays a no-op until
  it is enabled there.
- **Apify** (`openclawai/job-board-scraper`) -- **this is the one that works.**
  Verified live: `GET /api/v1/jobs/local` returns real, current Dhaka postings (Software
  Engineer at Optimizely, Backend Developer at nextjobz, and 25 more) sourced via
  LinkedIn. The actor's own `bdjobs` and `google` boards return nothing per its own
  documentation -- Bdjobs content still arrives because Bdjobs.com cross-posts to
  LinkedIn, which is why `apify-sites` is set to `linkedin` only.

**Cost reality, measured, not estimated:** the actor's advertised "$0.003/job" undersells
it. A full configured run (2 search terms x 25 results, with `linkedinFetchDescription`
so postings carry real text for TF-IDF scoring) measured at **~$0.24/run** against
Apify's $5/month free credit -- LinkedIn scraping carries real proxy and browser compute
cost on top of the per-result fee. Sharing the free boards' 6-hourly cron would spend
~$7.20/month and lock the account mid-cycle, so Apify runs on its **own schedule**,
`careerpilot.ingestion.apify-cron` (default: every 3 days, ~$2.40/month). That default is
a safe starting point, not a tuned answer -- watch the Apify dashboard and adjust.

If Apify ever needs replacing, `ApifyProvider` is the only class that changes -- the
`JobProvider` interface keeps that blast radius to one file, same as JSearch.

Three keyless feeds mean the demo never dies for want of an API key. Adzuna's ~33
calls/day budget is precisely why ingestion is scheduled and Redis-cached rather than
per-user-request — the constraint drives the caching design rather than decorating it.

---

## Repository layout

```
careerpilot-common/    infrastructure-only shared library
discovery-server/      Eureka registry
api-gateway/           routing, edge auth, rate limiting, CORS
identity-service/      auth, RS256 issuance, JWKS
profile-service/       profile, skill taxonomy, cover-letter PDF
resume-service/        Tika parsing, skill extraction, ATS scoring
job-service/           provider adapters, dedup, scheduled ingestion
matching-service/      explainable fit scoring, skill-gap → courses
infra/                 postgres init, prometheus, grafana provisioning
scripts/               key generation
```

## Build status

| Module | State |
|---|---|
| careerpilot-common | built, running |
| discovery-server | built, running |
| api-gateway | built, running |
| identity-service | built, running |
| profile-service | built, running |
| resume-service | not started |
| job-service | built, running |
| matching-service | not started |
| frontend | not started |

`prometheus.yml` already lists the services that do not exist yet; they show as `DOWN`
in Prometheus until built, which is expected rather than a misconfiguration.

### Verified end to end

Run against the live stack, all passing:

- register → login → `/me` through the gateway; unauthenticated and tampered tokens both
  rejected with 401 at the edge
- refresh-token **rotation**, and **reuse detection** — replaying a consumed refresh token
  is rejected and revokes the family
- duplicate email → 409, weak password → 400, both as RFC 7807 `problem+json` carrying the
  correlation ID and per-field errors
- JWKS exposes `kty/e/kid/n` and **no private exponent**
- schema isolation: `profile_svc` reading `identity.user_account` fails with
  `permission denied`
- a forged `X-User-Id` header is stripped at the gateway; `/me` still resolves the real
  user from the token
- correlation IDs propagate exactly, client-supplied or server-minted
- Zipkin shows a single trace spanning `api-gateway` → `identity-service`
- profile CRUD, education and experience entries, and skill assignment through the gateway
- 149-skill taxonomy with 186 aliases seeded by migration; searching `k8s` resolves to
  Kubernetes
- years of experience computed server-side by **merging overlapping roles** (2022-2024 plus
  a 2023 contract is 2 years, not 3)
- an unknown skill slug is rejected with 400 and names the offending slug, rather than
  being silently dropped
- a second user gets their own empty profile and cannot see the first user's data
- cover-letter endpoint returns a valid PDF composed from stored profile data
- **471 real job postings** ingested live: Remotive (17), Arbeitnow (306), RemoteOK
  (106), Apify/LinkedIn (42, including 27 genuine Dhaka postings)
- `/jobs/local` returns 27 real Dhaka jobs through the full stack (gateway -> auth ->
  job-service -> Postgres); `/jobs/remote` returns 152, correctly disjoint
- cross-provider deduplication collapses genuine duplicate listings by content hash
- `scope` partitions the corpus exactly: ALL 136 = REMOTE 38 + LOCAL 98 for one query
- city filtering works on live data (`/jobs/local?city=Berlin` returns 34)
- Redis cache measurably serves repeat searches: 498ms cold, then 55ms and 36ms
- every provider has an independent circuit breaker, reported by `/jobs/stats`
