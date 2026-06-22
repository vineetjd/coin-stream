# CoinStream Upgrade Plan: Portfolio → Production-Ready

Goal: close the gap between "interesting skeleton" and "demonstrated production readiness"
for an experienced-backend-engineer portfolio. Tiers are sequential; every item lists its
evidence — the artifact an interviewer can see.

Time model: evenings ≈ 2 focused hours; weekend ≈ 6–8 hours.

---

## Tier 1 — Quick Wins (1 weekend, ~8h)

High impact, low effort. Do these before showing the project to anyone.

| # | Task | Effort | Evidence |
|---|------|--------|----------|
| 1.1 | **Repo hygiene**: untrack `compile_error.txt`, `test_res.txt`; delete `gateway*.log`, `hs_err_*.log`; extend `.gitignore` (`*.log`, `hs_err_*`, `.env`); delete default Vite `frontend/README.md`; commit today's compose hardening + post-mortem with proper conventional-commit messages | 1h | Clean `git ls-files`, honest history from here on |
| 1.2 | **Fix the clone-and-run break**: stop baking `VITE_WS_URL` at build time. Make nginx proxy `/ws-market` to `api-gateway:8082` (with `Upgrade`/`Connection` headers for WebSocket) and have the frontend connect to a **relative** URL. Delete `frontend/.env` entirely. Verify from a fresh `git clone` in a temp dir | 1.5h | `git clone && docker compose up --build` works on any machine |
| 1.3 | **Exception-handling sweep**: replace every `log.error("...: {}", e.getMessage())` with `log.error("...", e)` in all three services (adapter + both gateway consumers). This pattern hid a fatal misconfig during the Boot 4 migration — reference the post-mortem | 1h | Stack traces in logs; post-mortem cross-link |
| 1.4 | **Fix `PriceAggregate` numeric bug**: `max` initialized to `Double.MIN_VALUE` (smallest *positive* double) → use `Double.NEGATIVE_INFINITY`; add unit tests for the aggregate including the first-element case | 0.5h | Failing test before / passing after |
| 1.5 | **Logging hygiene**: hot-path per-message logs (`Sending price`, `Consumed price update`, `Calculated Candle`) from INFO → DEBUG; remove `console.log` from `App.jsx` | 0.5h | Quiet logs at INFO under load |
| 1.6 | **Frontend empty/loading states**: skeleton chart + explanatory message while first candle forms ("first 1-minute candle closes in ~Ns"); visible reconnecting banner on disconnect | 1.5h | No blank-screen demo moment |
| 1.7 | **README truth pass**: remove unverified claims ("thousands of events/sec", "schema evolution resilience") until benchmarked; explicitly document two *deliberate* design decisions: (a) windows emit per-update (live-forming candles) instead of `suppress()`/`ON_WINDOW_CLOSE`, (b) `BigDecimal` at the boundary, `double` inside aggregation — and why that's acceptable for analytics (not order execution) | 1h | Claims = evidence |
| 1.8 | **Small fixes**: frontend Dockerfile `node:18` → `node:22` (Vite 7 requires ≥20.19); `CoinCard` div → `<button>` with focus ring (keyboard + a11y baseline) | 1h | Lighthouse a11y pass on cards |

**Definition of done:** a stranger can clone, run one command, and see a working, honest project with clean history.

---

## Tier 2 — The FAANG Polish (2–3 weeks of evenings, ~22h)

Sequencing note: do 2.D (CI) *second*, right after 2.A — so every subsequent change lands through a pipeline.

### 2.A Pipeline reliability (3 evenings, ~6h) — the core of the "production readiness" claim

- **Producer semantics** (`ingestion-service`): `acks=all`, `enable.idempotence=true`, bounded retries; handle the `send()` future — log + count failures (Micrometer counter). Document the resulting **at-least-once** guarantee in the README.
- **Poison-pill immunity** (`analytics-service`): replace default `LogAndFailExceptionHandler` with a continue-and-route handler; malformed records → `market.prices.dlt`. *Test: pipe garbage into `market.prices`, assert analytics survives and DLT receives it.*
- **Gateway resilience**: `DefaultErrorHandler` with exponential backoff + `DeadLetterPublishingRecoverer` on both listeners.
- **Config modernization**: replace `@Value` fields with `@ConfigurationProperties` records (kills the `ReflectionTestUtils` hack in tests).

### 2.B Observability (2 evenings, ~4h)

- `spring-boot-starter-actuator` in all three services; expose liveness/readiness; point compose `healthcheck`s at `/actuator/health` (currently only the broker is health-checked).
- `micrometer-registry-prometheus` + a compose `--profile observability` with Prometheus + Grafana and one dashboard (consumer lag, messages/sec, JVM).
- Tracing: make the existing `micrometer-tracing-bridge-brave` dep *real* (add Zipkin container + reporter) or remove it. No observability theater.

### 2.C Test depth (3 evenings, ~6h)

- **Analytics**: window-boundary tests — out-of-order within grace, late-beyond-grace dropped, multi-symbol isolation; `PriceAggregate` unit tests (from 1.4).
- **Ingestion**: Binance payload parsing (valid/malformed/missing fields); producer failure-callback behavior.
- **Gateway**: consumer → broadcast unit tests incl. malformed JSON path.
- **One real integration test**: Testcontainers Kafka (the dependency is already declared — make it true): tick in → candle out, end to end.
- **Frontend**: extract candle-merge logic from `App.jsx` into a pure function; Vitest tests for the windowStart dedupe + 50-candle cap.
- JaCoCo with a CI-enforced floor (start 60–70%, ratchet up).
- **Contract decision (documented tradeoff):** no schema registry at this scale — instead commit a `docs/CONTRACTS.md` defining the JSON schemas of both topics + consumer-side tolerant-reader tests in gateway/analytics. Mention Avro + registry as the stated evolution path.

### 2.D CI/CD (3 evenings, ~5h) — free on GitHub Actions

- **Workflow 1 — `ci.yml`**: matrix build/test of the three Maven services + frontend (lint, test, build); Spotless or Checkstyle gate; JaCoCo report on PRs.
- **Workflow 2 — `release.yml`**: buildx multi-stage image builds with GHA layer caching → push to **GHCR** tagged `sha` + `latest`.
- Dependabot (Maven + npm + Actions); CI/coverage badges in README.
- Stretch: hadolint + Trivy image scan jobs.

### 2.E Container & frontend polish (2 evenings, ~4h)

- Dockerfiles: BuildKit cache mounts for `~/.m2` (today's full-rebuild pain), non-root `USER`, `HEALTHCHECK`.
- Frontend: replace the ref-mutation + 200ms `setInterval` with a buffered external store (`useSyncExternalStore`); actually use `ResponsiveContainer` (kill fixed 800px); disconnect toast + auto-retry; color-blind-safe up/down (add ▲/▼ glyphs).

**Definition of done:** broker-down, poison-message, and replica-restart scenarios all survive *and are tested in CI*; the README's claims are all demonstrably true.

---

## Tier 3 — "Persist, Replay, Prove" (4 shippable increments, later)

One narrative: the data layer (depth) becomes the load generator (scale). Each increment is independently demoable — stop after any of them and the project is still better.

| Increment | Scope | Effort | Evidence |
|-----------|-------|--------|----------|
| **T3.1 Persistence sink** | New `market-data-service`: consumes both topics → **TimescaleDB** hypertables on a named volume; schema + retention policy + indexes documented; Testcontainers integration test | 1 weekend | Data survives `compose down/up`; schema doc |
| **T3.2 Backfill API** | REST `GET /api/candles/{symbol}?before&limit` with **keyset pagination**; frontend loads last 50 candles on mount/symbol-switch — chart is instantly populated after any restart | 1 weekend | Cold-start chart < 1s; pagination tests |
| **T3.3 Seed + Replay** | Capture script → committed `seed/ticks.ndjson.gz` (a few hours of real Binance data); auto-seed empty DB on first boot; `DEMO_MODE=replay` streams the seed through the **same Kafka pipeline** in a loop at configurable speed (1×/10×/50×) | 1 weekend | Fresh clone + no internet = live-moving dashboard in seconds |
| **T3.4 Scale-out + benchmark** | Tick **conflation** in gateway (≤4 updates/sec/symbol per client); fix fan-out for multi-replica (per-instance consumer `group.id`); nginx load-balances 2–3 gateway replicas; **k6** WebSocket scenario (hundreds of clients) driven by 50× replay; publish methodology + throughput + p99 end-to-end latency in `docs/BENCHMARK.md` with graphs | 1–2 weekends | Reproducible benchmark; restored (now true) README throughput claim |

**Definition of done:** the README's performance claims link to a benchmark anyone can re-run with one command.

### Backlog finding (discovered during 2.A.1 fault injection)

- **Decouple ingestion from producing (thread-isolation + backpressure).** Ingestion currently produces to Kafka *synchronously on the single OkHttp WebSocket reader thread*. A prolonged broker outage fills the producer buffer; `send()` then blocks on the reader thread (`max.block.ms`), starving frame reads. Because it surfaces as repeated send timeouts (not a clean socket failure), the WS auto-reconnect never fires and ingestion silently stalls until restart. Fix: produce off a bounded queue on a dedicated thread/pool with an explicit drop-or-conflate policy when the buffer is full. Pairs with T3.4 conflation. Verify by repeating the `docker compose pause broker` (~145s) chaos test and asserting ingestion self-recovers without a restart.
- **Distributed tracing (deferred from 2.B).** Tracing was wired (Boot 4 `spring-boot-micrometer-tracing-brave` + `spring-boot-starter-zipkin` + a Zipkin container) but spans never exported — even a plain HTTP server span produced zero traces in Zipkin. Removed rather than ship non-working theater (metrics + health shipped instead). Revisit: confirm the tracing autoconfig activates (`/actuator/beans` for a `Tracer` bean), verify an HTTP server span reaches Zipkin first, then add Kafka producer/consumer propagation.

---

## Sequencing rules

1. Tier 1 fully before Tier 2 (especially 1.2 — never demo a broken clone).
2. CI (2.D) lands early in Tier 2; everything after merges through green pipelines.
3. Conventional commits from now on; each Tier-2/3 item = one focused PR-style commit or branch.
4. Tier 3 increments in order — each depends on the previous; stop anywhere and still win.
