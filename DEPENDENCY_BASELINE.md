# Dependency baseline and verification

## Decision — 2026-11-19

Upgrade the merged prototype/SaaS baseline before public production exposure. This increment changes dependencies, test framework integration and verification configuration. It does not add a database migration or change workspace permissions, email configuration or account policies.

| Component | Previous | New baseline |
| --- | --- | --- |
| Next.js | 14.2.18 | 16.4.6 |
| React / React DOM | 18.3.1 | 19.3.1 |
| Lucide | 0.460.x | 1.76.0, supporting React 19 |
| Node runtime | Docker Node 20 | 24.22.3 LTS, Docker and CI |
| Spring Boot | 3.3.5 | 4.1.2, confirmed available from Maven Central |
| Java | 21 | 21 |

Production SBOM scanning identified three high-severity advisories in Kafka's compression dependencies. Dependency management therefore pins `at.yawk.lz4:lz4-java` to 1.11.4 (CVE-2026-106451) and `org.xerial.snappy:snappy-java` to 1.1.10.9 (CVE-2026-108106 and CVE-2026-93451). Keep these overrides until the managed baseline provides equivalent or newer fixes. A parameterized broker/consumer integration check covers both compression formats against the self-hosted Kafka version.

The package lock updates PostCSS and compatible React/Node types. Runtime engines require a patched Node 24 release; use a portable runtime or update the local installation before reinstalling frontend dependencies. A package-lock-only update preserves an already-running dev server's installed modules, but that server continues to run the old code until dependencies are installed and it is restarted.

## Compatibility boundaries

Boot 4 uses modular MVC/Flyway/Kafka/security-test starters and relocated test/Actuator classes. MockitoSpyBean/MockitoBean replace removed Boot spy/mock annotations. Existing Testcontainers infrastructure remains disposable and sequential.

The Boot-managed Jackson 2 compatibility module is intentionally retained for existing internal payload serialization, queued event envelopes and idempotency hashes. Boot 4 prefers Jackson 3 for its web stack. A golden canonical payload test covers date, null, Unicode and numeric formatting for the internal mapper. The bridge is deprecated upstream and must be removed in a separate compatibility-reviewed change; do not switch persisted hashing formats incidentally during an upgrade. Existing JWT and queued-data rollout contracts still apply.

Next 16 uses the automatic React JSX runtime and generated route types. `npm run typecheck` generates route types first, so it works on a clean checkout. `npm test` runs the existing protocol/navigation tests sequentially. The removed `next lint` command is not advertised as a working script; dedicated lint rules can be introduced separately.

## CI contract

`.github/workflows/verify.yml` runs on PRs to main, pushes to main/codex branches and manual dispatch:

- Frontend: clean install without lifecycle scripts, protocol/navigation tests, generated route types/TypeScript, production build and npm audit (high/critical failures, including development packages).
- Backend: Java 21, full Testcontainers verification/package, a pinned CycloneDX Maven plugin inventory of resolved production dependencies, then Trivy SBOM scanning (high/critical failures). The gate fails if either inventory is empty, avoiding an empty-scan success. JVM/context cache bounds keep test memory predictable.
- Secret scan: Gitleaks on the checked-out source tree, with redacted output. This checks the current source, not all historical commits.

Actions use verified commit SHAs; scanner images use verified digests. Jobs have read-only repository permissions, bounded runtime and no production secrets. Dependabot proposes weekly npm, Maven, Docker and Actions updates. Updates are not automatically merged.

Required branch checks are a GitHub repository setting. The workflow provides checks but does not itself enable branch protection. Once the workflow has run successfully on GitHub, require these job checks through the repository's ruleset. No deployment job or repository access change is included.

Scanner failures must be investigated. Do not suppress an entire package, ignore all unfixed vulnerabilities or allow scanner/network errors to report success. Narrow false-positive exceptions require documented applicability and review.

## Verification scope

Validation results are recorded with the completed increment. The local upgraded frontend is built in a separate directory with real installed dependencies and the checksum-verified Node 24.22.3 executable, preserving the user's live dev server. Do not use a node_modules junction for isolated standalone builds.

The first overlapping verification attempt exhausted Windows native memory; the backend suite is rerun sequentially with CI-equivalent memory bounds. A resource failure is not counted as a passing check.

The initial archive scan timed out downloading Trivy's Java-identification index. The final gate uses Maven's resolved SBOM instead of guessing dependency identities from JAR archives. Its inventory excludes test-scoped dependencies and is retained alongside the scan result. The advisory database still requires network access; a scanner timeout or download failure fails the check.

Validation completed: all 166 backend tests and package/SBOM verification passed in an isolated Maven directory, including both patched Kafka compression formats; Trivy inventoried 144 packages and reported zero high/critical vulnerabilities after the codec fixes; all 21 frontend tests and the standalone production build passed on Node 24.22.3; the complete npm audit reported zero known vulnerabilities; the current-source Gitleaks scan passed. Sign-in and signup rendering/navigation were inspected on a separate standalone preview without submitting credentials. The preview was stopped afterward.

Gitleaks exemptions are limited to inline annotations on two existing public test fixture literals. Empty secret examples are separated by explanatory comments to avoid a cross-line false positive; real secret values remain absent from the example file.

Container base-image patch availability was checked. Application image builds, operating-system image scans, Kafka/Postgres/Redis upgrades, ingress/TLS, rate limiting, safer browser sessions and the rest of the UX/security backlog remain separate work. Passing dependency scans is not a production-security certification.

## Primary upgrade references

- [Next 16 migration](https://nextjs.org/docs/app/guides/upgrading/version-16)
- [Spring Boot 4 migration and Jackson compatibility](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Node release/support schedule](https://github.com/nodejs/Release)
- [LZ4 security release](https://github.com/yawkat/lz4-java/releases/tag/v1.11.4)
- [Snappy security release](https://github.com/xerial/snappy-java/releases/tag/v1.1.10.9)
