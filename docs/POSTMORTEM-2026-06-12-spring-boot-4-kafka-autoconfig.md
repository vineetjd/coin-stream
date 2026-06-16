# Post-Mortem: Kafka Services Crash-Looping After Spring Boot 4 Migration

| | |
|---|---|
| **Date of incident** | 2026-06-12 |
| **Services affected** | `ingestion-service`, `analytics-service` (hard crash); `api-gateway` (silent degradation) |
| **Severity** | High — core data pipeline down in local/dev deployment |
| **Status** | Resolved |
| **Trigger** | Migration to Java 25 / Spring Boot 4.0.6 / Spring Framework 7 |

---

## 1. The Issue

After migrating CoinStream from Spring Boot 3.x to **Spring Boot 4.0.6** (Java 25, Spring Framework 7), all modules compiled successfully, but `docker compose up` produced the following symptoms:

- **`ingestion-service`** exited with code `1` within ~2 seconds of starting:

  ```
  APPLICATION FAILED TO START
  Parameter 0 of constructor in com.coinstream.ingestion.producer.MarketPriceProducer
  required a bean of type 'org.springframework.kafka.core.KafkaTemplate'
  that could not be found.
  ```

- **`analytics-service`** exited with code `1` within ~2 seconds of starting:

  ```
  Error creating bean with name 'defaultKafkaStreamsBuilder':
  There is no 'defaultKafkaStreamsConfig' KafkaStreamsConfiguration bean
  in the application context. Consider declaring one or don't use @EnableKafkaStreams.
  ```

- **`api-gateway`** appeared healthy (started in 2.3 s, served HTTP), but its
  `@KafkaListener` consumers were **never registered**, so it consumed zero
  messages. This was a latent silent failure with the same root cause and would
  have shipped a "working" gateway that streams nothing to the frontend.

A secondary issue surfaced during verification: after the primary fix,
`ingestion-service` stayed up but logged `Failed to construct kafka producer`
on every inbound Binance message, producing nothing to Kafka.

Notably ruled out during triage: `javax`→`jakarta` namespace issues (already
migrated correctly), container memory limits (no `OOMKilled` flag; crashes were
bean-wiring failures during context refresh), and Kafka startup races (the
applications died before opening any network connection to the broker).

## 2. The Root Cause

### Primary: Spring Boot 4.0 modularized auto-configuration

Through Spring Boot 3.x, the monolithic `spring-boot-autoconfigure` jar — present
on every classpath via any starter — contained `KafkaAutoConfiguration`. Merely
having `org.springframework.kafka:spring-kafka` as a dependency activated it,
which auto-created:

- the `KafkaTemplate` / `ProducerFactory` beans (needed by `ingestion-service`),
- the `defaultKafkaStreamsConfig` (`KafkaStreamsConfiguration`) bean built from
  `spring.kafka.streams.*` properties (needed by `analytics-service`'s
  `@EnableKafkaStreams`),
- the `@KafkaListener` annotation-processing infrastructure
  (needed by `api-gateway`).

In **Spring Boot 4.0**, auto-configuration was split into technology-specific
modules. Kafka auto-configuration now lives in the `spring-boot-kafka` module,
delivered by the **new starter `org.springframework.boot:spring-boot-starter-kafka`**.

Our poms were upgraded to the Boot 4.0.6 parent but still declared only the raw
library `spring-kafka`. Every Kafka class was therefore present at **compile
time** (the build passed), but **no Kafka auto-configuration ran at runtime** —
so the beans the services depended on simply did not exist. Services that
required the beans at startup crashed; the gateway, whose `@KafkaListener`
annotations are inert without the auto-configured post-processor, degraded
silently.

### Secondary: Jackson 2 → Jackson 3 serializer rename in spring-kafka 4.x

`ingestion-service` configured:

```properties
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
```

In spring-kafka 4.x, `JsonSerializer` is the **legacy Jackson 2-based**
implementation. The project's dependencies were migrated to Jackson 3
(`tools.jackson.core:jackson-databind`), so Jackson 2 is not on the classpath,
and instantiating `JsonSerializer` failed with a `NoClassDefFoundError` —
surfacing as `Failed to construct kafka producer` on first send (the producer
is built lazily). The Jackson 3-based replacement is `JacksonJsonSerializer`
(and `JacksonJsonSerde` for Streams, which `analytics-service` was already
using correctly).

## 3. The Resolution

### Dependency fix (all three Java services)

In `ingestion-service/pom.xml`, `analytics-service/pom.xml`, and
`api-gateway/pom.xml`, the raw library dependency was replaced with the new
Boot 4 starter (version managed by the `spring-boot-starter-parent` BOM;
`spring-kafka` arrives transitively):

```xml
<!-- Before -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- After -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

### Serializer fix (`ingestion-service/src/main/resources/application.properties`)

```properties
# Before
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
# After (Jackson 3 / tools.jackson based)
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer
```

### Configuration hygiene

`analytics-service` and `api-gateway` pointed `spring.kafka.bootstrap-servers`
at `localhost:9092`. Docker Compose masked this via the
`SPRING_KAFKA_BOOTSTRAP_SERVERS` environment override, but the property files
were aligned to `broker:29092` to match `ingestion-service` and remove the trap.

### Docker Compose hardening (`docker-compose.yml`)

- Removed the obsolete `version: '3.8'` attribute.
- Added a broker health check and gated the Java services on it, with a
  restart policy as a safety net:

```yaml
broker:
  healthcheck:
    test: ["CMD-SHELL", "kafka-broker-api-versions --bootstrap-server broker:29092 > /dev/null 2>&1"]
    interval: 10s
    timeout: 10s
    retries: 10
    start_period: 20s

ingestion-service:   # same for analytics-service, api-gateway
  depends_on:
    broker:
      condition: service_healthy
  restart: on-failure
```

### Clean rebuild

```powershell
docker compose down --remove-orphans
docker compose build --no-cache ingestion-service analytics-service api-gateway
docker compose up -d
```

### Verification evidence

- All six containers `Up`; broker reports `(healthy)` and the app services
  were held until it did.
- `ingestion-service`: `Started IngestionApplication`, connected to the Binance
  WebSocket, zero `ERROR` log lines.
- `analytics-service`: Kafka Streams state transition `REBALANCING → RUNNING`.
- `api-gateway`: `partitions assigned: [market.prices-0]` and
  `[market.analytics-0]` — the listeners are genuinely subscribed this time.
- End-to-end proof: live trade events consumed from the `market.prices` topic:

  ```json
  {"symbol":"ETH","price":1676.99000000,"timestamp":"2026-06-12T11:03:12.285Z"}
  ```

## 4. Future-Proofing

1. **Prefer starters over raw libraries with Spring Boot.** Boot's contract is
   "starter in, auto-configuration on." Spring Boot 4's modularization makes
   this mandatory for Kafka and other technologies that the old
   `spring-boot-autoconfigure` monolith carried implicitly. During upgrades,
   audit every `spring-*` dependency that is *not* a `spring-boot-starter-*`
   and check the release notes for a new dedicated starter.

2. **Read the migration guide for runtime changes, not just compile changes.**
   A build that compiles proves nothing about auto-configuration. The Spring
   Boot 4.0 migration guide explicitly lists the new technology modules; budget
   time to walk it even when the build is green.

3. **Add Docker health checks + gated `depends_on` (done in this fix).**
   `depends_on` without a `condition` only orders container *creation*. Gating
   on `service_healthy` removes the whole class of startup-race failures and
   makes `docker compose up` deterministic.

4. **Add Spring Boot Actuator with liveness/readiness probes.** Expose
   `/actuator/health/liveness` and `/actuator/health/readiness`
   (`spring-boot-starter-actuator`, `management.endpoint.health.probes.enabled=true`)
   and point each service's Compose `healthcheck` at it. That converts the
   gateway-style *silent* failure into a visible unhealthy container — Kafka
   listener container state can be tied into readiness.

5. **Fail fast and loud — don't swallow exceptions.** The Binance adapter
   caught the producer-construction failure per message and logged only
   `Error parsing message: <message>`, hiding the stack trace and disguising a
   fatal misconfiguration as a parse warning. Log the full exception, and treat
   "cannot construct producer" as fatal (crash) rather than retrying per event.

6. **Smoke-test the data path, not just process liveness.** "Container is Up"
   missed the gateway bug. A minimal post-deploy check — consume one message
   from `market.prices` and one from `market.analytics` — proves the pipeline.
   This is scriptable in CI with Testcontainers (already a test dependency in
   `analytics-service`).

7. **Centralize dependency/version management.** The three service poms
   duplicate the same dependencies and drifted independently. A parent/aggregator
   pom (or at least a shared BOM import) means the next framework upgrade is
   one edit instead of three, and prevents one service being migrated
   (`JacksonJsonSerde` in analytics) while another is missed
   (`JsonSerializer` in ingestion).

8. **Keep config truthful even when the environment overrides it.** Property
   files that disagree with the deployment (the `localhost:9092` entries) work
   right up until someone runs without the override. Align defaults with the
   primary runtime, and use Spring profiles (e.g. `application-local.properties`)
   for the exceptions.
