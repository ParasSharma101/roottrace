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

Workspace-session increment: roles are now resolved from current database membership rather than trusted JWT claims. Version 2 sessions identify the user and optional workspace; old-format tokens require fresh login. See [WORKSPACE_DESIGN.md](WORKSPACE_DESIGN.md). This does not yet isolate incident or telemetry queries by workspace.

## Rotation and deployment limits

Replacing the signing secret invalidates tokens signed with the previous secret after all backend instances use the new key. Restart/roll out all instances together; this step does not implement multiple-key rotation or session revocation. Replace the ingestion key separately and update senders. Previously seeded demo accounts still exist and can use password login until explicitly removed or disabled. Do not migrate development credentials/accounts into production.

This decision does not complete workspace authorization, workspace ingestion keys, account/session lifecycle, identity-based recovery, or deployment hardening. The existing V2 migration still creates a development workspace, and non-Compose database configuration still has development defaults. These need separate remediation before production use.

## Verification

SecuritySettingsTest checks startup failure conditions, credential separation, profile precedence, and sanitized validation errors. CredentialFilterTest checks filter boundaries. ProductionCredentialIntegrationTest checks HTTP authorization with production settings, emergency headers, ingestion keys, and tokens signed using the old development key. The full backend suite uses disposable infrastructure; it does not call live AI/GitHub providers.
