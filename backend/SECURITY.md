# Security configuration decision

Date: 2026-11-16. Status: accepted for the first production release.

## Requirement and ownership

The initial SaaS release must not offer shared-token emergency administrator access. Signing and ingestion credentials must be explicitly supplied outside local development. The security module owns resolution and validation through `SecuritySettings`; JWT issuance and request filters consume its validated values. No database schema change is needed.

The earlier global break-glass bypass has no workspace identity, expiry, or revocation mechanism. It is disabled outside an explicitly active `dev` profile. If `prod` is also active, production rules win. A supplied token or enabled flag cannot override this. A future production emergency-access design would need a separate decision covering identity, workspace scope, expiry, revocation, and auditing.

## Configuration contract

| Variable | Behavior |
| --- | --- |
| `ROOTTRACE_JWT_SECRET` | Required outside development; at least 32 UTF-8 bytes, no surrounding whitespace, not a known development default. |
| `ROOTTRACE_INGESTION_KEY` | Required outside development; same minimum and default restrictions; must differ from the signing secret. This is the interim global ingestion credential until workspace key isolation is implemented. |
| `ROOTTRACE_JWT_EXPIRATION_MS` | Positive duration; defaults to 86400000 milliseconds. |
| `ROOTTRACE_BREAKGLASS_ENABLED` | Defaults to false. Only explicit development mode can enable it. |
| `ROOTTRACE_BREAKGLASS_TOKEN` | Used only when explicitly enabled in development; must differ from signing/ingestion credentials. Never enables production access. |
| `POSTGRES_PASSWORD` | Production Compose requires an explicit nonempty password. Provision a strong separate password; Compose's presence check does not measure its strength. |

Use independently generated random secrets (for example, 32 random bytes encoded as Base64), supplied by the deployment's secret store. Length/default checks cannot prove entropy. Do not reuse credentials between purposes or environments. Production Compose passes signing/ingestion credentials explicitly and disables break-glass.

In local development, activate `dev` to allow known local signing/ingestion defaults. Demo endpoints and emergency access each require their own explicit switch. Without `dev`, startup requires credentials even when no profile is specified. Integration tests supply their own fixtures and require no production credentials.

Spring Boot and Next.js do not automatically load the repository-root `.env` when launched directly. Docker Compose reads it for interpolation. Configure each application process explicitly; never print actual credentials in diagnostic output. Startup validation errors identify the property and requirement without the configured value.

## Request boundaries

`X-RootTrace-Key` is accepted only under `/api/v1/webhooks/`; it cannot authenticate workspace or other application requests. Credential comparisons use `MessageDigest.isEqual` on UTF-8 bytes. The emergency header is ignored outside explicitly enabled local development. Ordinary JWT role checks continue to apply to production requests.

Workspace-session increment: roles are now resolved from current database membership rather than trusted JWT claims. Version 2 sessions identify the user and optional workspace; old-format tokens require fresh login. See [WORKSPACE_DESIGN.md](WORKSPACE_DESIGN.md). The incident API boundary builds on this context; whole-pipeline isolation remains unfinished.

Incident-boundary increment: incident APIs now scope lists and object access to the selected workspace, and reject mismatched child links. Legacy workspace membership cannot establish a normal session, even with a previously issued token. V5 gives logs/deployments mandatory workspace ownership, preserving historical evidence in legacy quarantine. Correlation, timeline/export, and Redis incident contexts now use workspace scope; missing incident ownership stops correlation/AI. Named-key webhooks now carry authenticated ownership through server-generated envelopes, Kafka consumers, and persistence. Payload ownership fields are ignored. The global key and old raw events remain legacy-only; key/bearer combinations are rejected. Kafka envelope metadata assumes restricted trusted producers; the self-hosted Compose path now configures broker mTLS and resource ACLs; see KAFKA_SECURITY.md for credential provisioning and rollout boundaries. GitHub integration/cache and historical AI provenance also need review before public multi-tenant use. See WORKSPACE_DESIGN.md for rollout constraints.

## Rotation and deployment limits

Named workspace credentials now use hash-only storage and one-time disclosure through admin management APIs. Profile/workspace responses no longer return a plaintext ingestion key. V4 imports eligible prior non-legacy credentials as hashes and retires the old column's contents. See [INGESTION_KEYS.md](INGESTION_KEYS.md) for compatibility, webhook usage, trusted-broker assumptions, and coordinated rollout constraints.

Replacing the signing secret invalidates tokens signed with the previous secret after all backend instances use the new key. Restart/roll out all instances together; this step does not implement multiple-key rotation or session revocation. Replace the ingestion key separately and update senders. Previously seeded demo accounts still exist and can use password login until explicitly removed or disabled. Do not migrate development credentials/accounts into production.

This decision does not complete workspace authorization, workspace ingestion keys, account/session lifecycle, identity-based recovery, or deployment hardening. The existing V2 migration still creates a development workspace, and non-Compose database configuration still has development defaults. These need separate remediation before production use.

## Verification

SecuritySettingsTest checks startup failure conditions, credential separation, profile precedence, and sanitized validation errors. CredentialFilterTest checks filter boundaries. ProductionCredentialIntegrationTest checks HTTP authorization with production settings, emergency headers, ingestion keys, and tokens signed using the old development key. The full backend suite uses disposable infrastructure; it does not call live AI/GitHub providers.

Durable processing: V6 adds workspace-scoped request identity, replay receipts, and leased tasks with ownership foreign keys. Status endpoints enforce the same workspace boundary; failures store only safe exception codes. Keys revoked after acceptance cannot cancel queued events. Envelope ownership still trusts restricted broker producers. See [DURABLE_PIPELINE.md](DURABLE_PIPELINE.md) for the changed 202 contract and remaining operational limits.

Operator recovery: /api/v1/workspace/tasks requires selected ADMIN/SRE sessions and current non-legacy membership checks in the service. It never authenticates ingestion keys as operators. Metadata/history are workspace-scoped and no-store; payloads and claim tokens are omitted. Recovery snapshots the database actor and reason atomically with a FAILED-task reset and rejects stale recovery counts. See [TASK_RECOVERY.md](TASK_RECOVERY.md) for the audit trust/retention boundary.

Queue monitoring uses the same current operator-membership check as recovery and exposes workspace aggregates only. Public Actuator routes expose minimal default health probes; other diagnostics/discovery are denied independently of the configured endpoint base path. Workspace ADMIN never grants platform metric access. See [QUEUE_MONITORING.md](QUEUE_MONITORING.md).

The self-hosted Compose broker now requires client TLS identity and resource ACLs, with private service networks and distinct broker/bootstrap/backend keys. No live stack upgrade was performed. Certificate custody/rotation, HTTPS ingress, patch review and broader production readiness remain as described in [KAFKA_SECURITY.md](KAFKA_SECURITY.md).

## Verified email and invitation consent

V8 retires plaintext invitation tokens, revokes old pending invitations and preserves existing memberships. New membership requires authenticated explicit acceptance by an account with a verified matching email; the invitation ID alone grants nothing. Verification requires both the emailed token and its requesting account session, and issues no JWT. Proofs are hashed; retryable mail bodies use separately keyed AES-GCM and are scrubbed after send/cancellation. SMTP is opt-in with production TLS/authentication validation. See [EMAIL_INVITATIONS.md](EMAIL_INVITATIONS.md) for migration, configuration, recovery and account lifecycle limits.

## Account recovery and revocation

V9 adds asynchronous encrypted recovery intake, hashed single-use reset links, server-side session-family revocation and account-wide authentication epochs. Password reset atomically changes the password, proves the matching email, retires outstanding recovery/verification links, revokes sessions and queues notification. JWT version 3 is required; workspace selection cannot extend expiry or escape revocation. New passwords require 15 Unicode code points and at most 72 UTF-8 bytes; existing passwords remain usable. See [ACCOUNT_RECOVERY.md](ACCOUNT_RECOVERY.md) for coordinated rollout, direct-peer rate limits/proxy constraints, key custody and remaining lifecycle work.

## Audited membership management

V10 requires current database membership and verified administrator email for role changes and removal of other members. Members can leave themselves, while concurrent changes cannot remove/demote the final administrator through these APIs. Exact membership identity/version and reason are required. Removal atomically revokes invitations to that address in the workspace and appends audit history; a failed audit rolls everything back. Invitation operations now share the mutation lock order. Account/session row locks use NO KEY UPDATE to remain compatible with audit foreign-key references. See [MEMBERSHIP_MANAGEMENT.md](MEMBERSHIP_MANAGEMENT.md) for authorization, retention and operator boundaries.
