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
```

Results are written to `target/surefire-reports`. A successful build does not prove production readiness, tenant isolation, or deployed integration behavior.

Testcontainers 2.0.5 is pinned for the current Docker daemon. Commons Lang and Commons IO are pinned in test scope to satisfy its Commons Compress dependency without changing application source or adding runtime dependencies.
