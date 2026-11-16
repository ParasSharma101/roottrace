# Named workspace ingestion keys

Date: 2026-11-17. Status: credential management, workspace-aware webhooks, durable submissions and investigation jobs implemented; production infrastructure and operational hardening remain.

## Decision and boundaries

The user chose multiple named keys per workspace, with independent revocation. Keys belong to a workspace rather than their creator's account. An administrator's departure does not automatically revoke integrations; a remaining administrator must revoke keys that should no longer be trusted. Keys currently have no automatic expiry.

Only a current ADMIN membership in the selected, non-legacy workspace can create, list, read metadata, or revoke keys. The service rechecks membership as well as the HTTP authorization layer. A key ID from another workspace is indistinguishable from an unknown ID (404). SRE/viewer requests are forbidden. Legacy workspaces cannot issue these credentials, even in development.

The security module owns generation, hashing, resolution, and revocation through IngestionKeyService. Workspace controllers expose management DTOs only. Persistence is in the domain model/repository and enforces workspace/user foreign keys, unique hashes, hash format, and nonblank names. Names are labels, not identifiers; duplicate labels are allowed.

## Storage and disclosure

New secrets use `rt_ing_` plus 32 cryptographically random bytes encoded as unpadded Base64url. The database stores a SHA-256 hash and a short display prefix, never the recoverable secret. Fast hashing is appropriate for randomly generated high-entropy credentials; human passwords continue to use BCrypt.

Only the successful creation response includes the secret. Metadata responses never include the secret or hash, and management responses use Cache-Control: no-store. The creation DTO redacts its string representation, and the service does not log credentials. Prefixes intentionally disclose only a small identifying portion. SHA-256/encoding for migration uses PostgreSQL 16's [built-in binary functions](https://www.postgresql.org/docs/16/functions-binarystring.html), without installing an extension.

The settings panel shows the new key in temporary component state, offers explicit copy/save acknowledgement, and does not persist it. It prevents another creation from overwriting an unacknowledged secret. Revocation requires a confirmation in the UI. Existing browser auth-profile ingestion-key fields are removed on read/save. JWT session storage is otherwise unchanged and remains a separate hardening task.

## API contract

All paths are under `/api/v1/workspace/ingestion-keys` and require a workspace ADMIN session.

| Method/path | Result |
| --- | --- |
| POST `/` with `{ "name": "Monitoring" }` | 201; `{ key: metadata, secret: oneTimeValue }`. Name is required and at most 128 characters. |
| GET `/` | Metadata for the selected workspace, including revoked records. |
| GET `/{id}` | Metadata only; no secret-recovery endpoint. |
| DELETE `/{id}` | 204; idempotent revocation of an owned key. Foreign/unknown IDs return 404. |

Metadata contains id, name, prefix, createdAt, and revokedAt. Creator/revoker user IDs are stored for audit. Concurrent repeated revocations retain the first recorded revocation rather than overwriting it. Losing a new secret requires revoking that key and creating another; it cannot be retrieved afterward.

## V4 migration and compatibility

V4 creates workspace_ingestion_keys. Existing non-legacy workspace keys of at least 32 characters are imported as hashes under the label `Imported workspace key`. Known inert `retired_` placeholders, short invalid values, and legacy workspace credentials are not imported. The old workspaces.api_key column is temporarily retained for schema compatibility but replaced with unrelated inert placeholders. New signup also writes only an inert placeholder there. Auth, profile, and workspace responses retain nullable apiKey compatibility fields with null values.

This preserves previously issued prototype credentials where feasible; it does not establish their randomness or erase copies from backups/WAL/older clients. Rotate imported keys before a real production launch. Use a coordinated application upgrade so old nodes cannot write new plaintext workspace keys after the migration. No existing development/production database has been migrated by the implementation tests.

## Webhook integration

Named keys now authenticate log and alert webhooks through `X-RootTrace-Key`. Resolution checks the hash, revocation status, and a non-legacy owning workspace on every new request. A typed IngestionIdentity supplies the workspace; neither request fields nor nested metadata/details can override it. Keys cannot authenticate application APIs. Existing selected ADMIN/SRE bearer sessions retain webhook access in their selected workspace. Account-only/viewer sessions cannot ingest. Requests containing both a key header and an Authorization header return 400 rather than choosing an identity implicitly.

| Endpoint | Payload |
| --- | --- |
| POST `/api/v1/webhooks/logs` | `{ "serviceName": "checkout", "timestamp": "2026-11-17T00:00:00Z", "logLevel": "ERROR", "message": "Connection timeout" }` |
| POST `/api/v1/webhooks/logs/batch` | Array of 1-500 valid log objects; validated in full before publishing. |
| POST `/api/v1/webhooks/alerts/{source}` | `{ "serviceName": "checkout", "alertType": "HighLatency", "severity": "SEV1", "triggeredAt": "2026-11-17T00:00:00Z", "details": {} }` |

Service and alert-type names are limited to 128 characters; source is at most 64. Optional log stackTrace/metadata are supported. New credentials are shown once in Settings; use separate keys for integrations and keep them out of client/browser application bundles. Unknown or revoked keys return 401. Revocation stops subsequent authenticated requests; it does not cancel in-flight requests or already accepted broker events.

The durable outbox stores version 2 server-owned envelopes `{eventVersion: 2, eventType: "LOG"|"ALERT", workspaceId, eventId, payload}` and partition by `workspaceId:serviceName`. Consumers validate version, type, workspace existence, and payload before persistence. Invalid envelopes go to the relevant DLQ without becoming legacy or tenant records. Historical raw events always go to legacy quarantine, even if they contain workspaceId. The shared configured global credential also writes only to legacy; it grants no tenant/application access. Deployment ingestion is still pending.

A 202 now confirms atomic PostgreSQL storage of the submission and its outbox events; it does not wait for Kafka or AI. Optional Idempotency-Key headers identify HTTP retries, and stable event IDs suppress duplicate database effects from broker replay. Processing status is available under GET /api/v1/webhooks/requests/{trackingId}; investigation status is under GET /api/v1/incidents/{id}/investigation. See [DURABLE_PIPELINE.md](DURABLE_PIPELINE.md) for the changed acknowledgement contract, header scope/conflicts, leases, retries, fallback semantics, and operational limits. Revocation does not cancel durable work already accepted.

## Trust and rollout boundary

Envelope ownership is trusted broker metadata, not a cryptographic signature. Kafka must be private and producer access restricted to trusted application components; anyone allowed to publish arbitrary envelopes can assert workspace IDs. Local prototype broker settings do not establish production broker authentication/ACLs/TLS. Do not expose the broker to customer producers or the public internet. Production infrastructure hardening is still required.

V5 supplies workspace-owned log/deployment persistence and scoped correlation, timeline/export, and Redis incident contexts. Upgrade consumers/readers before enabling new envelope-producing nodes, with old writers stopped/drained; old consumers cannot safely interpret the new format. Use a coordinated rollout, not mixed application versions. Historical AI provenance and tenant-specific GitHub integrations remain separate review items. No real development/production database migration or infrastructure deployment was performed by these tests.

Other pending tasks: deployment ingestion, key usage metrics/rate limits/expiry, workspace deletion lifecycle, and eventual removal of the retired workspace.api_key column.

## Verification

IngestionKeyIntegrationTest checks independent keys, one-time disclosure, hash-only storage, metadata redaction, cross-workspace/role restrictions, validation, idempotent revocation, continued validity of another key, and legacy rejection. IngestionKeyMigrationIntegrationTest upgrades an independent disposable schema from V3 to V4, checks Unicode hash compatibility, preserves an eligible credential as a hash, excludes legacy/short values, and retires plaintext storage. Frontend auth-storage tests verify that legacy ingestion secrets are removed from stored profiles while login remains intact. TenantIngestionIntegrationTest follows key creation through authenticated webhooks, Kafka consumers, tenant persistence, deduplication, correlation, AI summary, and Redis. It checks forged ownership fields, same-service overlapping workspaces, raw/global legacy quarantine, revocation, credential separation, batch validation, and maximum-length names. IngestionEventCodecTest rejects unsupported/malformed/unknown-owner envelopes and preserves raw-event quarantine. Consumer unit regressions verify retryable persistence failures and required explicit DLQ completion. Durable pipeline integration coverage is documented in DURABLE_PIPELINE.md.
