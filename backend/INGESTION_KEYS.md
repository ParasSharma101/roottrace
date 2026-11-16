# Named workspace ingestion keys

Date: 2026-11-17. Status: credential management implemented; tenant-aware webhook integration is the next increment.

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

## Deliberate integration gate

IngestionKeyService.resolveWorkspace validates hashes, revoked status, and non-legacy ownership for the following integration step. It is deliberately not connected to WebhookApiKeyFilter yet: the existing Kafka path cannot safely attribute tenant-owned evidence end to end. The current global ingestion credential still routes unscoped alerts to legacy quarantine. Named keys shown here do not yet authenticate webhook requests, and the UI says so explicitly.

Next: connect authenticated key identity to a versioned server-generated event envelope, carry ownership through consumers and persistence, and scope correlation/AI/cache reads before enabling tenant-key webhook traffic. Do not accept payload workspace IDs as authority or enable new keys on the old path. Deployment ingestion, key usage metrics/rate limits/expiry, and workspace deletion lifecycle are separate pending tasks.

## Verification

IngestionKeyIntegrationTest checks independent keys, one-time disclosure, hash-only storage, metadata redaction, cross-workspace/role restrictions, validation, idempotent revocation, continued validity of another key, and legacy rejection. IngestionKeyMigrationIntegrationTest upgrades an independent disposable schema from V3 to V4, checks Unicode hash compatibility, preserves an eligible credential as a hash, excludes legacy/short values, and retires plaintext storage. Frontend auth-storage tests verify that legacy ingestion secrets are removed from stored profiles while login remains intact.
