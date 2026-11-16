# Backend tests

## Prerequisites

- JDK 21, with `JAVA_HOME` pointing to the JDK directory.
- A running Docker daemon available to the current user (Docker Desktop must use Linux containers on Windows).
- Access to Maven Central and Docker Hub on the first run. Later runs can use cached dependencies and images.

From `backend`, run:

```powershell
.\mvnw.cmd -B test
```

On Linux/macOS use `./mvnw -B test`.

You do not need to start the development Compose stack or copy production credentials.

## Isolation and lifecycle

Every Spring integration test extends `IntegrationTestSupport`. It starts fresh PostgreSQL 16, Redis 7, and Apache Kafka 3.7 containers once per test JVM. Host ports are assigned dynamically, and connection properties are supplied using Spring's `DynamicPropertySource` rather than development endpoints. Flyway migrates the empty test database; Hibernate still validates the schema.

Provider tokens are overridden with empty values so existing AI/GitHub fallback code is used. Tests therefore cover local heuristic behavior and cached/fallback GitHub metadata, not live provider correctness. Test authentication values are explicit fixtures, not deployment credentials.

Demo auth fixtures are seeded only in `SecurityIntegrationTest`, which explicitly activates `dev` and enables `roottrace.demo.enabled`. Disabled-demo regression tests cover the default configuration, development with the switch off, and production with both an accidental `dev` profile and the switch on. Production must win in that last case. Shared test data can remain visible across Spring contexts; disabled-demo tests verify the seeder bean is absent rather than relying on the absence of previously created accounts.

Containers are not reusable, have no development volume mounts, and are cleaned up by Testcontainers' resource reaper after the test JVM exits. A missing Docker daemon fails integration tests; there is no fallback to localhost development services. Do not disable the resource reaper.

All test classes within a JVM share these disposable services. Keep tests sequential, use unique service names/IDs, and roll back direct database fixtures where practical. This isolates test runs from development, but does not provide a separate database for each test method.

When adding a Spring integration test, extend `IntegrationTestSupport`. Do not add fixed ports, `@Container` lifecycle annotations to the shared containers, or real provider credentials. Plain unit tests should not extend this class.

## Focused checks

```powershell
# No Docker required: citation grounding unit test
.\mvnw.cmd -B -Dtest=GroundingValidatorTest test

# Docker required: startup, fresh migrations, and provider credential overrides
.\mvnw.cmd -B -Dtest=RootTraceApplicationTests test

# No Docker required: security configuration and request-filter boundaries
.\mvnw.cmd -B '-Dtest=SecuritySettingsTest,CredentialFilterTest' test
```

Results are written to `target/surefire-reports`. A successful build does not prove production readiness, tenant isolation, or deployed integration behavior.

Integration fixtures use independent signing/ingestion keys rather than known development defaults. Emergency-access success is tested only with explicit development opt-in; production regression checks verify that matching emergency headers cannot authenticate or upgrade a viewer's role. Startup-validation unit tests use isolated Spring contexts and no database.

WorkspaceAuthenticationIntegrationTest checks account-only login, explicit switching, membership-only listing, roles in the selected workspace, permission changes without token renewal, removed membership, old-format rejection, and non-member selection. Production JWT tests now create actual users and memberships rather than relying on email/role claims alone. These API checks use MockMvc; they do not verify the browser interaction or incident/telemetry tenant isolation.

IncidentTenantIsolationIntegrationTest covers the incident API ownership boundary, including foreign reads/exports/mutations, same-incident child links, authenticated audit authors, legacy membership rejection, and exclusion of unscoped timeline telemetry. LegacyWorkspaceMigrationIntegrationTest upgrades an independent disposable schema from V2 to V3 and verifies preservation/backfill without touching public-schema fixtures. Evidence/correlation and authenticated ingestion coverage are described below.

Testcontainers 2.0.5 is pinned for the current Docker daemon. Commons Lang and Commons IO are pinned in test scope to satisfy its Commons Compress dependency without changing application source or adding runtime dependencies.

Named-key tests: IngestionKeyIntegrationTest covers lifecycle, access boundaries, metadata-only reads, and legacy rejection. IngestionKeyMigrationIntegrationTest verifies V3-to-V4 hash import and plaintext retirement in a separate disposable schema. Run `npm.cmd run test:auth-storage` from frontend for the client storage cleanup regression tests. Tenant-key webhook coverage is described below.

## Docker startup failures

If many tests report `NoClassDefFoundError: Could not initialize class IntegrationTestSupport`, inspect the first Testcontainers provider error in the Surefire XML/log. A failed static container initializer causes subsequent test classes to fail too; the class may be present and compiled correctly.

On Windows, verify the actual Linux engine rather than only the Desktop window:

```powershell
docker --context desktop-linux version --format 'Server={{.Server.Version}}'
```

An engine HTTP 500 prevented container startup during the November 17 verification run. Restarting Docker Desktop restored its server response. A restart interrupts local containers, so coordinate it before proceeding; do not delete volumes or reset Docker data as a first response. Rerun tests in a fresh Maven JVM after recovery and keep the failed log separate from the successful run.

## Evidence ownership regressions

EvidenceTenantIsolationIntegrationTest uses the same service/time in two tenant workspaces and legacy quarantine. It verifies owned log/deployment/PR context, timeline/export filtering, reverse legacy isolation, and refusal to correlate an unassigned incident. EvidenceOwnershipMigrationIntegrationTest upgrades V4 to V5 in an independent disposable schema, preserves evidence/PR content, and checks mandatory valid workspace ownership. GitHubAndRedisContextIntegrationTest checks incident-context workspace keys, embedded identity validation, scoped eviction, and exclusion of old global cache entries. These tests do not establish authenticated tenant ingestion or tenant-safe GitHub integration.

## Authenticated tenant ingestion

TenantIngestionIntegrationTest commits fixtures through signup/key APIs so real Kafka consumers can see them. It checks two workspaces with overlapping service/time, forged root/nested workspace fields, single/batch logs, alert ownership and deduplication, scoped context/AI summary/Redis, raw and global-key quarantine, immediate revocation, independent key validity, credential separation, invalid batches, and maximum-length names. IngestionEventCodecTest checks unsupported versions/types, unknown/malformed ownership, and raw-message quarantine. WebhookEventPublisherTest and IngestionConsumerFailureTest cover broker publication failure, retryable persistence failure, and explicit DLQ send failure. External Gemini/GitHub calls remain disabled by test properties. These checks do not verify a deployed secure broker, exactly-once processing, or a durable asynchronous investigation workflow.
